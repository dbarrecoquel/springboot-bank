package com.bank.infrastructure.external;

import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Adaptateur vers le système de Core Banking (CBS — Core Banking System).
 *
 * <p>Le CBS est le système central de la banque qui gère le grand livre
 * comptable officiel. L'application Spring Boot agit comme une couche digitale
 * qui synchronise ses données avec le CBS en temps réel ou de manière
 * asynchrone selon la criticité des opérations.</p>
 *
 * <p>Opérations synchronisées avec le CBS :</p>
 * <ul>
 *   <li>Création et clôture de comptes (ouverture dans le grand livre).</li>
 *   <li>Mouvements comptables (débits/crédits officiels).</li>
 *   <li>Consultation des soldes officiels (source de vérité).</li>
 *   <li>Génération des relevés de compte réglementaires.</li>
 * </ul>
 *
 * <p>En mode {@code simulation} (dev/test), toutes les opérations retournent
 * des réponses simulées sans appel réseau.</p>
 */
@Slf4j
@Component
public class CoreBankingAdapter {

    private final WebClient webClient;

    @Value("${banking.cbs.base-url:http://localhost:8090/api/v1}")
    private String baseUrl;

    @Value("${banking.cbs.api-key:}")
    private String apiKey;

    @Value("${banking.cbs.enabled:false}")
    private boolean enabled;

    @Value("${banking.cbs.timeout-seconds:15}")
    private int timeoutSeconds;

    public CoreBankingAdapter(WebClient.Builder webClientBuilder,
                               @Value("${banking.cbs.base-url:http://localhost:8090/api/v1}") String baseUrl) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT,       MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    // ─────────────────────────────────────────────────────────
    //  Gestion des comptes
    // ─────────────────────────────────────────────────────────

    /**
     * Ouvre un nouveau compte dans le CBS.
     * Appelé après la création du compte en base locale.
     *
     * @param request données du compte à créer
     * @return référence CBS du compte créé
     */
    public CbsAccountResponse openAccount(CbsOpenAccountRequest request) {
        if (!enabled) {
            log.info("[CBS] Simulation — ouverture compte iban={}", request.iban());
            return new CbsAccountResponse(
                "CBS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.iban(), "OPEN", LocalDateTime.now()
            );
        }

        try {
            CbsAccountResponse response = webClient.post()
                .uri("/accounts")
                .header("X-API-Key", apiKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    res.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new CbsException(
                            "Erreur ouverture compte CBS : " + body, "CBS_OPEN_ERROR"))))
                .bodyToMono(CbsAccountResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
                .block();

            log.info("[CBS] Compte ouvert — cbsRef={} iban={}",
                     response != null ? response.cbsReference() : "null", request.iban());
            return response;

        } catch (CbsException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[CBS] Erreur ouverture compte — iban={} error={}",
                      request.iban(), ex.getMessage(), ex);
            throw new CbsException("Erreur CBS ouverture compte : " + ex.getMessage(),
                                    "CBS_OPEN_ERROR");
        }
    }

    /**
     * Clôture un compte dans le CBS.
     *
     * @param cbsReference référence CBS du compte
     * @param reason       motif de clôture
     */
    public void closeAccount(String cbsReference, String reason) {
        if (!enabled) {
            log.info("[CBS] Simulation — clôture compte cbsRef={}", cbsReference);
            return;
        }

        try {
            webClient.delete()
                .uri("/accounts/{ref}?reason={reason}", cbsReference, reason)
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new CbsException("Erreur clôture CBS", "CBS_CLOSE_ERROR")))
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            log.info("[CBS] Compte clôturé — cbsRef={}", cbsReference);

        } catch (Exception ex) {
            log.error("[CBS] Erreur clôture compte — cbsRef={} error={}",
                      cbsReference, ex.getMessage(), ex);
            throw new CbsException("Erreur CBS clôture compte : " + ex.getMessage(),
                                    "CBS_CLOSE_ERROR");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Mouvements comptables
    // ─────────────────────────────────────────────────────────

    /**
     * Enregistre un mouvement comptable dans le CBS (débit ou crédit officiel).
     *
     * @param movement mouvement à enregistrer
     * @return confirmation CBS avec identifiant du mouvement
     */
    public CbsMovementResponse postMovement(CbsMovementRequest movement) {
        if (!enabled) {
            log.info("[CBS] Simulation — mouvement ref={} amount={} {}",
                     movement.reference(), movement.amount(), movement.currency());
            return new CbsMovementResponse(
                "MOV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                movement.reference(), "POSTED", LocalDateTime.now()
            );
        }

        try {
            CbsMovementResponse response = webClient.post()
                .uri("/movements")
                .header("X-API-Key", apiKey)
                .bodyValue(movement)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res ->
                    res.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new CbsException(
                            "Mouvement CBS refusé : " + body, "CBS_MOVEMENT_REJECTED"))))
                .onStatus(HttpStatusCode::is5xxServerError, res ->
                    Mono.error(new CbsException(
                        "CBS indisponible", "CBS_UNAVAILABLE")))
                .bodyToMono(CbsMovementResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                    .filter(ex -> ex instanceof CbsException ce
                                  && "CBS_UNAVAILABLE".equals(ce.getErrorCode())))
                .block();

            log.info("[CBS] Mouvement enregistré — cbsMvtId={} ref={}",
                     response != null ? response.cbsMovementId() : "null",
                     movement.reference());
            return response;

        } catch (CbsException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[CBS] Erreur enregistrement mouvement — ref={} error={}",
                      movement.reference(), ex.getMessage(), ex);
            throw new CbsException("Erreur CBS mouvement : " + ex.getMessage(),
                                    "CBS_MOVEMENT_ERROR");
        }
    }

    /**
     * Récupère le solde officiel d'un compte depuis le CBS.
     * Source de vérité — peut différer du solde local en cas de décalage de synchronisation.
     *
     * @param cbsReference référence CBS du compte
     * @return solde officiel
     */
    public CbsBalanceResponse getOfficialBalance(String cbsReference) {
        if (!enabled) {
            return new CbsBalanceResponse(cbsReference, BigDecimal.ZERO,
                                           BigDecimal.ZERO, CurrencyCode.EUR,
                                           LocalDateTime.now());
        }

        try {
            return webClient.get()
                .uri("/accounts/{ref}/balance", cbsReference)
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new CbsException("Erreur solde CBS", "CBS_BALANCE_ERROR")))
                .bodyToMono(CbsBalanceResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Exception ex) {
            log.error("[CBS] Erreur récupération solde — cbsRef={} error={}",
                      cbsReference, ex.getMessage(), ex);
            throw new CbsException("Erreur CBS solde : " + ex.getMessage(),
                                    "CBS_BALANCE_ERROR");
        }
    }

    /**
     * Récupère l'historique des mouvements comptables sur une période.
     *
     * @param cbsReference référence CBS du compte
     * @param from         date de début
     * @param to           date de fin
     * @return liste des mouvements comptables
     */
    public List<CbsMovementResponse> getMovements(String cbsReference,
                                                    LocalDateTime from,
                                                    LocalDateTime to) {
        if (!enabled) {
            return List.of();
        }

        try {
            return webClient.get()
                .uri(u -> u.path("/accounts/{ref}/movements")
                    .queryParam("from", from.toString())
                    .queryParam("to",   to.toString())
                    .build(cbsReference))
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new CbsException("Erreur mouvements CBS", "CBS_MOVEMENTS_ERROR")))
                .bodyToFlux(CbsMovementResponse.class)
                .collectList()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Exception ex) {
            log.error("[CBS] Erreur récupération mouvements — cbsRef={} error={}",
                      cbsReference, ex.getMessage(), ex);
            throw new CbsException("Erreur CBS mouvements : " + ex.getMessage(),
                                    "CBS_MOVEMENTS_ERROR");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Records — DTOs
    // ─────────────────────────────────────────────────────────

    public record CbsOpenAccountRequest(
        String      iban,
        String      accountNumber,
        AccountType type,
        CurrencyCode currency,
        String      ownerName,
        String      ownerNationalId,
        LocalDateTime openedAt
    ) {}

    public record CbsAccountResponse(
        String        cbsReference,
        String        iban,
        String        status,
        LocalDateTime syncedAt
    ) {}

    public record CbsMovementRequest(
        String      reference,
        String      cbsAccountReference,
        String      type,              // "DEBIT" ou "CREDIT"
        BigDecimal  amount,
        CurrencyCode currency,
        String      counterpartIban,
        String      label,
        LocalDateTime valueDate
    ) {}

    public record CbsMovementResponse(
        String        cbsMovementId,
        String        reference,
        String        status,
        LocalDateTime postedAt
    ) {}

    public record CbsBalanceResponse(
        String        cbsReference,
        BigDecimal    ledgerBalance,     // solde comptable
        BigDecimal    availableBalance,  // solde disponible (provisions déduites)
        CurrencyCode  currency,
        LocalDateTime asOf
    ) {}

    // ─────────────────────────────────────────────────────────
    //  Exception
    // ─────────────────────────────────────────────────────────

    public static class CbsException extends RuntimeException {
        private final String errorCode;

        public CbsException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() { return errorCode; }
    }
}