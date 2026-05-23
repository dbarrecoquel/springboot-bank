package com.bank.infrastructure.external;

import com.bank.domain.enums.CurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Client PSD2 / Open Banking — implémentation de la directive européenne
 * sur les services de paiement (Payment Services Directive 2).
 *
 * <p>Expose les APIs PSD2 obligatoires pour les TPP (Third Party Providers) :</p>
 * <ul>
 *   <li><strong>AIS</strong> (Account Information Service) — consultation des comptes
 *       et transactions par des agrégateurs tiers (ex : Bankin, Linxo).</li>
 *   <li><strong>PIS</strong> (Payment Initiation Service) — initiation de paiements
 *       depuis des applications tierces.</li>
 *   <li><strong>CBPII</strong> (Card Based Payment Instrument Issuer) — confirmation
 *       de la disponibilité des fonds pour les paiements par carte.</li>
 * </ul>
 *
 * <p>Authentification forte (SCA — Strong Customer Authentication) :</p>
 * <ul>
 *   <li>OAuth2 avec scopes PSD2 spécifiques.</li>
 *   <li>Consentement explicite de l'utilisateur requis avant tout accès TPP.</li>
 *   <li>Durée du consentement : 90 jours maximum (règlement EBA).</li>
 * </ul>
 *
 * <p>Standard utilisé : Berlin Group NextGenPSD2 (API commune européenne).</p>
 */
@Slf4j
@Component
public class Psd2OpenBankingClient {

    private final WebClient webClient;

    @Value("${banking.psd2.base-url:https://api.bank.com/psd2/v1}")
    private String baseUrl;

    @Value("${banking.psd2.client-id:}")
    private String clientId;

    @Value("${banking.psd2.client-secret:}")
    private String clientSecret;

    @Value("${banking.psd2.enabled:false}")
    private boolean enabled;

    @Value("${banking.psd2.timeout-seconds:20}")
    private int timeoutSeconds;

    public Psd2OpenBankingClient(WebClient.Builder webClientBuilder,
                                  @Value("${banking.psd2.base-url:https://api.bank.com/psd2/v1}") String baseUrl) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT,       MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    // ─────────────────────────────────────────────────────────
    //  AIS — Account Information Service
    // ─────────────────────────────────────────────────────────

    /**
     * Crée un consentement AIS permettant à un TPP d'accéder aux informations de compte.
     * L'utilisateur doit approuver ce consentement via SCA (OTP ou biométrie).
     *
     * @param request détails du consentement demandé
     * @return consentement créé avec l'URL de redirection SCA
     */
    public ConsentResponse createAisConsent(ConsentRequest request) {
        if (!enabled) {
            log.info("[PSD2] Simulation — création consentement AIS tppId={}",
                     request.tppId());
            return simulateConsentResponse(request.tppId(), "AIS");
        }

        try {
            return webClient.post()
                .uri("/consents")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .header("TPP-Redirect-URI", request.redirectUri())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    res.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new Psd2Exception(
                            "Erreur création consentement AIS : " + body,
                            "PSD2_CONSENT_ERROR"))))
                .bodyToMono(ConsentResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Psd2Exception ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[PSD2] Erreur création consentement — tppId={} error={}",
                      request.tppId(), ex.getMessage(), ex);
            throw new Psd2Exception("Erreur PSD2 consentement : " + ex.getMessage(),
                                     "PSD2_CONSENT_ERROR");
        }
    }

    /**
     * Récupère les informations d'un compte pour un TPP autorisé.
     * Requiert un consentement AIS valide et approuvé.
     *
     * @param consentId  identifiant du consentement approuvé
     * @param accountId  identifiant du compte
     * @param accessToken token OAuth2 du TPP
     * @return informations du compte
     */
    public AisAccountDetails getAccountDetails(String consentId,
                                                String accountId,
                                                String accessToken) {
        if (!enabled) {
            return new AisAccountDetails(accountId, "FR76****", "EUR",
                                          BigDecimal.ZERO, "enabled",
                                          LocalDateTime.now());
        }

        try {
            return webClient.get()
                .uri("/accounts/{accountId}", accountId)
                .header("Consent-ID",    consentId)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Request-ID",  java.util.UUID.randomUUID().toString())
                .retrieve()
                .onStatus(status -> status.value() == 401, res ->
                    Mono.error(new Psd2Exception("Consentement invalide ou expiré",
                                                  "PSD2_CONSENT_EXPIRED")))
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new Psd2Exception("Erreur récupération compte AIS",
                                                  "PSD2_AIS_ERROR")))
                .bodyToMono(AisAccountDetails.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Psd2Exception ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[PSD2] Erreur AIS getAccountDetails — accountId={} error={}",
                      accountId, ex.getMessage(), ex);
            throw new Psd2Exception("Erreur PSD2 AIS : " + ex.getMessage(), "PSD2_AIS_ERROR");
        }
    }

    /**
     * Récupère les transactions d'un compte pour un TPP autorisé (AIS).
     *
     * @param consentId   consentement AIS approuvé
     * @param accountId   identifiant du compte
     * @param accessToken token OAuth2 du TPP
     * @param dateFrom    date de début (format ISO)
     * @param dateTo      date de fin (format ISO)
     * @return liste des transactions
     */
    public List<AisTransaction> getTransactions(String consentId, String accountId,
                                                  String accessToken,
                                                  String dateFrom, String dateTo) {
        if (!enabled) {
            return List.of();
        }

        try {
            return webClient.get()
                .uri(u -> u.path("/accounts/{id}/transactions")
                    .queryParam("dateFrom", dateFrom)
                    .queryParam("dateTo",   dateTo)
                    .queryParam("bookingStatus", "booked")
                    .build(accountId))
                .header("Consent-ID",    consentId)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Request-ID",  java.util.UUID.randomUUID().toString())
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new Psd2Exception("Erreur transactions AIS", "PSD2_AIS_TX_ERROR")))
                .bodyToFlux(AisTransaction.class)
                .collectList()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Exception ex) {
            log.error("[PSD2] Erreur AIS transactions — accountId={} error={}",
                      accountId, ex.getMessage(), ex);
            throw new Psd2Exception("Erreur PSD2 transactions : " + ex.getMessage(),
                                     "PSD2_AIS_TX_ERROR");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  PIS — Payment Initiation Service
    // ─────────────────────────────────────────────────────────

    /**
     * Initie un paiement via PSD2 PIS pour un TPP autorisé.
     * Retourne une URL de redirection SCA pour l'approbation utilisateur.
     *
     * @param request     détails du paiement
     * @param accessToken token OAuth2 du TPP
     * @return initiation de paiement avec statut et URL SCA
     */
    public PisPaymentResponse initiatePayment(PisPaymentRequest request,
                                               String accessToken) {
        if (!enabled) {
            log.info("[PSD2] Simulation — initiation paiement PIS amount={} {}",
                     request.amount(), request.currency());
            return new PisPaymentResponse(
                "PIS-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                "RCVD",
                baseUrl + "/auth?paymentId=MOCK",
                LocalDateTime.now()
            );
        }

        try {
            return webClient.post()
                .uri("/payments/sepa-credit-transfers")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Request-ID",  java.util.UUID.randomUUID().toString())
                .header("TPP-Redirect-URI", request.redirectUri())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res ->
                    res.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new Psd2Exception(
                            "Paiement PIS refusé : " + body, "PSD2_PIS_REJECTED"))))
                .onStatus(HttpStatusCode::is5xxServerError, res ->
                    Mono.error(new Psd2Exception("PSD2 indisponible", "PSD2_UNAVAILABLE")))
                .bodyToMono(PisPaymentResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Psd2Exception ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[PSD2] Erreur initiation paiement PIS — error={}", ex.getMessage(), ex);
            throw new Psd2Exception("Erreur PSD2 PIS : " + ex.getMessage(), "PSD2_PIS_ERROR");
        }
    }

    /**
     * Récupère le statut d'un paiement PIS.
     *
     * @param paymentId   identifiant du paiement
     * @param accessToken token OAuth2 du TPP
     * @return statut courant du paiement
     */
    public PisPaymentStatus getPaymentStatus(String paymentId, String accessToken) {
        if (!enabled) {
            return new PisPaymentStatus(paymentId, "ACCC", "Règlement effectué",
                                         LocalDateTime.now());
        }

        try {
            return webClient.get()
                .uri("/payments/sepa-credit-transfers/{paymentId}/status", paymentId)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Request-ID",  java.util.UUID.randomUUID().toString())
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new Psd2Exception("Erreur statut PIS", "PSD2_PIS_STATUS_ERROR")))
                .bodyToMono(PisPaymentStatus.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Exception ex) {
            log.error("[PSD2] Erreur statut paiement — paymentId={} error={}",
                      paymentId, ex.getMessage(), ex);
            throw new Psd2Exception("Erreur PSD2 statut : " + ex.getMessage(),
                                     "PSD2_PIS_STATUS_ERROR");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  CBPII — Confirmation de disponibilité des fonds
    // ─────────────────────────────────────────────────────────

    /**
     * Confirme la disponibilité des fonds pour un paiement par carte (CBPII).
     * Utilisé par les réseaux carte avant d'autoriser une transaction.
     *
     * @param iban    IBAN du compte à vérifier
     * @param amount  montant à vérifier
     * @param currency devise
     * @return {@code true} si les fonds sont disponibles
     */
    public boolean confirmFundsAvailability(String iban, BigDecimal amount,
                                              CurrencyCode currency) {
        if (!enabled) {
            log.debug("[PSD2] Simulation CBPII — fonds disponibles iban={} amount={}",
                      iban, amount);
            return true;
        }

        try {
            FundsConfirmationResponse response = webClient.post()
                .uri("/funds-confirmations")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .bodyValue(new FundsConfirmationRequest(iban, amount.toPlainString(),
                                                         currency.name()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new Psd2Exception("Erreur CBPII", "PSD2_CBPII_ERROR")))
                .bodyToMono(FundsConfirmationResponse.class)
                .timeout(Duration.ofSeconds(10))
                .block();

            boolean available = response != null && response.fundsAvailable();
            log.debug("[PSD2] CBPII — iban={} amount={} available={}",
                      iban.substring(0, 8) + "****", amount, available);
            return available;

        } catch (Exception ex) {
            log.error("[PSD2] Erreur CBPII — iban={} error={}", iban, ex.getMessage(), ex);
            // En cas d'erreur, refuser par sécurité
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Simulation
    // ─────────────────────────────────────────────────────────

    private ConsentResponse simulateConsentResponse(String tppId, String type) {
        return new ConsentResponse(
            "CONSENT-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            tppId, type, "received",
            baseUrl + "/auth?consentId=MOCK",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(90)
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Records — DTOs
    // ─────────────────────────────────────────────────────────

    public record ConsentRequest(
        String tppId,
        String redirectUri,
        List<String> accountIds,
        List<String> permissions,    // "ReadAccountsDetail", "ReadTransactionsDetail", etc.
        String validUntil
    ) {}

    public record ConsentResponse(
        String        consentId,
        String        tppId,
        String        consentType,
        String        consentStatus,
        String        scaRedirectUri,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
    ) {}

    public record AisAccountDetails(
        String        accountId,
        String        maskedIban,
        String        currency,
        BigDecimal    balanceAmount,
        String        status,
        LocalDateTime retrievedAt
    ) {}

    public record AisTransaction(
        String        transactionId,
        String        bookingDate,
        BigDecimal    amount,
        String        currency,
        String        creditorName,
        String        debtorName,
        String        remittanceInfo
    ) {}

    public record PisPaymentRequest(
        String     debtorIban,
        String     creditorIban,
        String     creditorName,
        String     creditorBic,
        BigDecimal amount,
        CurrencyCode currency,
        String     remittanceInfo,
        String     redirectUri
    ) {}

    public record PisPaymentResponse(
        String        paymentId,
        String        transactionStatus,  // RCVD, ACCP, ACSC, RJCT
        String        scaRedirectUri,
        LocalDateTime createdAt
    ) {}

    public record PisPaymentStatus(
        String        paymentId,
        String        transactionStatus,
        String        statusLabel,
        LocalDateTime updatedAt
    ) {}

    private record FundsConfirmationRequest(
        String iban, String amount, String currency) {}

    private record FundsConfirmationResponse(boolean fundsAvailable) {}

    // ─────────────────────────────────────────────────────────
    //  Exception
    // ─────────────────────────────────────────────────────────

    public static class Psd2Exception extends RuntimeException {
        private final String errorCode;

        public Psd2Exception(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() { return errorCode; }
    }
}