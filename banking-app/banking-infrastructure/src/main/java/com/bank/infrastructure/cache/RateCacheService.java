package com.bank.infrastructure.cache;
import com.bank.domain.enums.CurrencyCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
 
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final WebClient.Builder             webClientBuilder;
 
    @Value("${banking.rates.ecb-url:https://api.frankfurter.app}")
    private String ecbApiUrl;
 
    @Value("${banking.rates.base-currency:EUR}")
    private String baseCurrency;
 
    @Value("${banking.rates.ttl-hours:1}")
    private int rateTtlHours;
 
    @Value("${banking.rates.spread-percent:0.5}")
    private BigDecimal spreadPercent;
 
    // Devises supportées — sous-ensemble de CurrencyCode
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
        "USD", "GBP", "CHF", "JPY", "CAD", "AUD", "SEK", "NOK", "DKK",
        "PLN", "CZK", "HUF", "RON", "CNY", "BRL", "INR", "MXN",
        "ZAR", "AED", "MAD", "TND", "SGD", "HKD"
    );
    
    /**
     * Retourne le taux de change entre deux devises.
     * Utilise EUR comme devise pivot si ni {@code from} ni {@code to} n'est EUR.
     *
     * @param from devise source
     * @param to   devise cible
     * @return taux : 1 unité de {@code from} = résultat unités de {@code to}
     * @throws ExchangeRateUnavailableException si le taux est absent du cache
     */
    public BigDecimal getRate(CurrencyCode from, CurrencyCode to) {
        if (from == to) return BigDecimal.ONE;
 
        // Taux direct depuis EUR
        if (from.name().equals(baseCurrency)) {
            return getRateFromBase(to);
        }
 
        // Taux inverse vers EUR
        if (to.name().equals(baseCurrency)) {
            return invertRate(getRateFromBase(from));
        }
 
        // Conversion croisée via EUR pivot : from → EUR → to
        BigDecimal fromToEur = invertRate(getRateFromBase(from));
        BigDecimal eurToTo   = getRateFromBase(to);
        return fromToEur.multiply(eurToTo).setScale(6, RoundingMode.HALF_EVEN);
    }
    /**
     * Retourne le taux avec spread bancaire appliqué.
     * Le spread représente la marge de la banque sur les opérations de change.
     *
     * @param from     devise source
     * @param to       devise cible
     * @param isDebit  {@code true} si le client vend {@code from} (taux légèrement défavorable)
     * @return taux avec spread
     */
    public BigDecimal getRateWithSpread(CurrencyCode from, CurrencyCode to, boolean isDebit) {
        BigDecimal midRate = getRate(from, to);
        BigDecimal spread  = midRate.multiply(spreadPercent)
                                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_EVEN);
        // Spread défavorable au client : débit → taux plus bas, crédit → taux plus haut
        return isDebit
            ? midRate.subtract(spread)
            : midRate.add(spread);
    }
 
    /**
     * Retourne tous les taux disponibles depuis EUR.
     *
     * @return map devise → taux (1 EUR = x devise)
     */
    public Map<String, BigDecimal> getAllRates() {
        String hashKey = RedisConfig.PREFIX_RATE + baseCurrency;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);
 
        Map<String, BigDecimal> rates = new HashMap<>();
        entries.forEach((k, v) -> {
            try {
                rates.put(k.toString(), new BigDecimal(v.toString()));
            } catch (NumberFormatException ex) {
                log.warn("[RATE] Taux invalide en cache — key={} value={}", k, v);
            }
        });
        return rates;
    }
 
    /**
     * Retourne l'horodatage du dernier rafraîchissement des taux.
     */
    public Optional<LocalDateTime> getLastUpdated() {
        String key = RedisConfig.PREFIX_RATE_TIMESTAMP + baseCurrency;
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val == null) return Optional.empty();
        try {
            return Optional.of(LocalDateTime.parse(val, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
 
    /**
     * Vérifie si les taux en cache sont encore frais.
     */
    public boolean areRatesFresh() {
        return getLastUpdated()
            .map(updated -> updated.isAfter(
                LocalDateTime.now().minusHours(rateTtlHours)))
            .orElse(false);
    }
 
    // ─────────────────────────────────────────────────────────
    //  Écriture / mise en cache des taux
    // ─────────────────────────────────────────────────────────
 
    /**
     * Stocke un ensemble de taux en cache Redis.
     * Remplace atomiquement tous les taux existants (MULTI/EXEC).
     *
     * @param rates map devise → taux (1 EUR = x devise)
     */
    public void storeRates(Map<String, BigDecimal> rates) {
        if (rates == null || rates.isEmpty()) {
            log.warn("[RATE] Tentative de stockage de taux vides — ignoré");
            return;
        }
 
        String hashKey      = RedisConfig.PREFIX_RATE + baseCurrency;
        String timestampKey = RedisConfig.PREFIX_RATE_TIMESTAMP + baseCurrency;
 
        // Conversion en Map<String, String> pour le Hash Redis
        Map<String, String> ratesStr = new HashMap<>();
        rates.forEach((k, v) -> ratesStr.put(k, v.toPlainString()));
 
        // Exécution atomique via pipeline
        redisTemplate.execute(connection -> {
            connection.multi();
            try {
                // Supprimer l'ancien hash et le remplacer entièrement
                connection.keyCommands().del(hashKey.getBytes());
                ratesStr.forEach((k, v) ->
                    connection.hashCommands().hSet(
                        hashKey.getBytes(),
                        k.getBytes(),
                        v.getBytes()
                    )
                );
                // Définir le TTL du hash
                connection.keyCommands().expire(
                    hashKey.getBytes(),
                    Duration.ofHours(rateTtlHours + 1).getSeconds()
                );
                connection.exec();
            } catch (Exception ex) {
                connection.discard();
                throw ex;
            }
            return null;
        }, true);
 
        // Horodatage du refresh (hors transaction)
        stringRedisTemplate.opsForValue().set(
            timestampKey,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            Duration.ofHours(rateTtlHours + 1)
        );
 
        log.info("[RATE] {} taux stockés en cache — base={} devises={}",
                 rates.size(), baseCurrency, rates.keySet());
    }
 
    /**
     * Met à jour un taux individuel (ex : correction manuelle par un opérateur).
     *
     * @param currency devise cible
     * @param rate     nouveau taux (1 EUR = rate devise)
     */
    public void updateRate(CurrencyCode currency, BigDecimal rate) {
        String hashKey = RedisConfig.PREFIX_RATE + baseCurrency;
        redisTemplate.opsForHash().put(hashKey, currency.name(), rate.toPlainString());
        log.info("[RATE] Taux mis à jour manuellement — {} = {} {}", baseCurrency, rate, currency);
    }
 
    /**
     * Invalide tous les taux en cache (force un rechargement au prochain accès).
     */
    public void evictAllRates() {
        stringRedisTemplate.delete(RedisConfig.PREFIX_RATE + baseCurrency);
        stringRedisTemplate.delete(RedisConfig.PREFIX_RATE_TIMESTAMP + baseCurrency);
        log.info("[RATE] Cache des taux invalidé");
    }
 
    // ─────────────────────────────────────────────────────────
    //  Scheduler — rafraîchissement automatique depuis l'ECB
    // ─────────────────────────────────────────────────────────
 
    /**
     * Rafraîchit les taux depuis l'API Frankfurter (données ECB) toutes les heures.
     * Première exécution au démarrage (initialDelay = 0).
     *
     * <p>L'API Frankfurter est gratuite, sans clé, et reflète les taux officiels
     * de la BCE publiés chaque jour ouvré à 16h CET.</p>
     */
    @Scheduled(initialDelay = 0, fixedRateString = "${banking.rates.refresh-rate-ms:3600000}")
    public void refreshRatesFromEcb() {
        log.info("[RATE] Démarrage rafraîchissement taux ECB — base={}", baseCurrency);
 
        try {
            WebClient client = webClientBuilder
                .baseUrl(ecbApiUrl)
                .build();
 
            // Appel API : GET /latest?from=EUR&to=USD,GBP,...
            String symbols = String.join(",", SUPPORTED_CURRENCIES);
 
            EcbRateResponse response = client.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/latest")
                    .queryParam("from", baseCurrency)
                    .queryParam("to", symbols)
                    .build())
                .retrieve()
                .bodyToMono(EcbRateResponse.class)
                .timeout(Duration.ofSeconds(10))
                .block();
 
            if (response == null || response.rates() == null || response.rates().isEmpty()) {
                log.error("[RATE] Réponse ECB vide ou invalide");
                return;
            }
 
            // Convertir en BigDecimal et stocker
            Map<String, BigDecimal> rates = new HashMap<>();
            response.rates().forEach((currency, rateValue) -> {
                try {
                    rates.put(currency, new BigDecimal(rateValue.toString())
                        .setScale(6, RoundingMode.HALF_EVEN));
                } catch (NumberFormatException ex) {
                    log.warn("[RATE] Taux invalide reçu ECB — {} = {}", currency, rateValue);
                }
            });
 
            storeRates(rates);
            log.info("[RATE] Taux ECB rafraîchis avec succès — {} devises mises à jour",
                     rates.size());
 
        } catch (Exception ex) {
            log.error("[RATE] Échec rafraîchissement taux ECB — {}. " +
                      "Les taux en cache restent actifs.", ex.getMessage(), ex);
            // Ne pas propager — les taux en cache restent valides pendant rateTtlHours
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Helpers privés
    // ─────────────────────────────────────────────────────────
 
    private BigDecimal getRateFromBase(CurrencyCode to) {
        String hashKey = RedisConfig.PREFIX_RATE + baseCurrency;
        Object val = redisTemplate.opsForHash().get(hashKey, to.name());
 
        if (val == null) {
            log.error("[RATE] Taux introuvable en cache — {}/{}", baseCurrency, to.name());
            throw new ExchangeRateUnavailableException(
                "Taux de change indisponible pour " + baseCurrency + "/" + to.name() +
                ". Réessayez dans quelques instants."
            );
        }
 
        try {
            return new BigDecimal(val.toString()).setScale(6, RoundingMode.HALF_EVEN);
        } catch (NumberFormatException ex) {
            throw new ExchangeRateUnavailableException(
                "Taux de change corrompu en cache pour " + to.name());
        }
    }
 
    private BigDecimal invertRate(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            throw new ExchangeRateUnavailableException("Taux de change nul — division impossible");
        }
        return BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_EVEN);
    }
 
    // ─────────────────────────────────────────────────────────
    //  Record — réponse API Frankfurter/ECB
    // ─────────────────────────────────────────────────────────
 
    /**
     * Structure de la réponse JSON de l'API Frankfurter.
     * <pre>
     * {
     *   "base": "EUR",
     *   "date": "2024-01-27",
     *   "rates": { "USD": 1.085, "GBP": 0.856, ... }
     * }
     * </pre>
     */
    private record EcbRateResponse(
        String              base,
        String              date,
        Map<String, Object> rates
    ) {}
 
    // ─────────────────────────────────────────────────────────
    //  Exception
    // ─────────────────────────────────────────────────────────
 
    public static class ExchangeRateUnavailableException extends RuntimeException {
        public ExchangeRateUnavailableException(String message) {
            super(message);
        }
    }

}
