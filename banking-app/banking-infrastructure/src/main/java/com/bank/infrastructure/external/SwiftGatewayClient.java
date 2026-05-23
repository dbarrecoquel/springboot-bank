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
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Client d'intégration avec le réseau SWIFT (Society for Worldwide
 * Interbank Financial Telecommunication).
 *
 * <p>Utilisé pour les virements internationaux hors SEPA.
 * Les messages SWIFT sont envoyés via l'API SWIFT GPI (Global Payments Innovation)
 * qui offre un suivi en temps réel des paiements transfrontaliers.</p>
 *
 * <p>Messages SWIFT utilisés :</p>
 * <ul>
 *   <li><strong>MT103</strong> — virement client (Customer Credit Transfer).</li>
 *   <li><strong>MT202</strong> — virement inter-banques (Financial Institution Transfer).</li>
 *   <li><strong>gpi Tracker</strong> — suivi du statut en temps réel.</li>
 * </ul>
 *
 * <p>En cas d'indisponibilité SWIFT, les virements sont mis en attente
 * dans une file de retry avec backoff exponentiel.</p>
 */
@Slf4j
@Component
public class SwiftGatewayClient {

    private final WebClient webClient;

    @Value("${banking.swift.base-url:https://sandbox.swift.com/swift-apitracker-pilot/v4}")
    private String baseUrl;

    @Value("${banking.swift.api-key:}")
    private String apiKey;

    @Value("${banking.swift.bic:BANKFRPPXXX}")
    private String ourBic;

    @Value("${banking.swift.enabled:false}")
    private boolean enabled;

    @Value("${banking.swift.timeout-seconds:30}")
    private int timeoutSeconds;

    public SwiftGatewayClient(WebClient.Builder webClientBuilder,
                               @Value("${banking.swift.base-url:https://sandbox.swift.com/swift-apitracker-pilot/v4}") String baseUrl) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT,       MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    // ─────────────────────────────────────────────────────────
    //  Initiation d'un virement international
    // ─────────────────────────────────────────────────────────

    /**
     * Initie un virement international via SWIFT GPI (MT103).
     *
     * @param request détails du virement
     * @return réponse SWIFT avec l'UETR (Unique End-to-end Transaction Reference)
     * @throws SwiftException si l'envoi échoue après les tentatives de retry
     */
    public SwiftTransferResponse initiateTransfer(SwiftTransferRequest request) {
        if (!enabled) {
            log.info("[SWIFT] Mode simulation — virement simulé ref={}", request.endToEndId());
            return simulateTransferResponse(request);
        }

        log.info("[SWIFT] Initiation virement — ref={} amount={} {} to={}",
                 request.endToEndId(), request.amount(), request.currency(),
                 request.creditorBic());

        try {
            SwiftTransferResponse response = webClient.post()
                .uri("/payments")
                .header("X-API-Key", apiKey)
                .bodyValue(buildMt103Payload(request))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res ->
                    res.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new SwiftException(
                            "Erreur client SWIFT — " + body, "SWIFT_CLIENT_ERROR"))))
                .onStatus(HttpStatusCode::is5xxServerError, res ->
                    Mono.error(new SwiftException(
                        "Erreur serveur SWIFT — indisponible", "SWIFT_SERVER_ERROR")))
                .bodyToMono(SwiftTransferResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                    .filter(ex -> ex instanceof SwiftException se
                                  && "SWIFT_SERVER_ERROR".equals(se.getErrorCode())))
                .block();

            log.info("[SWIFT] Virement initié — uetr={} status={}",
                     response != null ? response.uetr() : "null",
                     response != null ? response.status() : "null");

            return response;

        } catch (SwiftException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[SWIFT] Erreur inattendue — ref={} error={}",
                      request.endToEndId(), ex.getMessage(), ex);
            throw new SwiftException(
                "Erreur lors de l'envoi du virement SWIFT : " + ex.getMessage(),
                "SWIFT_UNEXPECTED_ERROR"
            );
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Suivi du statut GPI
    // ─────────────────────────────────────────────────────────

    /**
     * Récupère le statut d'un virement SWIFT via son UETR (GPI Tracker).
     *
     * @param uetr Unique End-to-end Transaction Reference (UUID)
     * @return statut courant du paiement
     */
    public SwiftPaymentStatus getPaymentStatus(String uetr) {
        if (!enabled) {
            return new SwiftPaymentStatus(uetr, "ACCC", "Settled",
                                          LocalDateTime.now(), null);
        }

        try {
            return webClient.get()
                .uri("/payments/{uetr}", uetr)
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new SwiftException(
                        "Erreur récupération statut SWIFT — uetr=" + uetr,
                        "SWIFT_STATUS_ERROR")))
                .bodyToMono(SwiftPaymentStatus.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        } catch (Exception ex) {
            log.error("[SWIFT] Erreur récupération statut — uetr={} error={}",
                      uetr, ex.getMessage());
            throw new SwiftException(
                "Impossible de récupérer le statut SWIFT : " + ex.getMessage(),
                "SWIFT_STATUS_ERROR"
            );
        }
    }

    /**
     * Vérifie si un BIC est valide et joignable via SWIFT.
     *
     * @param bic code BIC/SWIFT à valider (8 ou 11 caractères)
     * @return {@code true} si le BIC est connu du réseau SWIFT
     */
    public boolean validateBic(String bic) {
        if (!enabled) {
            return bic != null && bic.matches("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
        }

        try {
            Boolean result = webClient.get()
                .uri("/bics/{bic}", bic)
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(status -> status.value() == 404, res -> Mono.empty())
                .onStatus(HttpStatusCode::isError, res ->
                    Mono.error(new SwiftException("Erreur validation BIC", "SWIFT_BIC_ERROR")))
                .bodyToMono(BicValidationResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(BicValidationResponse::valid)
                .onErrorReturn(false)
                .block();

            return Boolean.TRUE.equals(result);

        } catch (Exception ex) {
            log.warn("[SWIFT] Validation BIC échouée — bic={} error={}", bic, ex.getMessage());
            // En cas d'erreur, on laisse passer et on valide via le format uniquement
            return bic != null && bic.matches("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Construction du payload MT103
    // ─────────────────────────────────────────────────────────

    private Mt103Payload buildMt103Payload(SwiftTransferRequest request) {
        return new Mt103Payload(
            request.endToEndId(),
            ourBic,
            request.creditorBic(),
            request.debtorIban(),
            request.debtorName(),
            request.creditorIban(),
            request.creditorName(),
            request.amount().toPlainString(),
            request.currency().name(),
            request.remittanceInfo(),
            LocalDateTime.now().toString()
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Simulation pour les environnements non-prod
    // ─────────────────────────────────────────────────────────

    private SwiftTransferResponse simulateTransferResponse(SwiftTransferRequest request) {
        String uetr = UUID.randomUUID().toString();
        return new SwiftTransferResponse(
            uetr,
            request.endToEndId(),
            "ACSP",          // Accepted Settlement In Process
            LocalDateTime.now(),
            "Simulation SWIFT — virement accepté"
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Records — DTOs internes
    // ─────────────────────────────────────────────────────────

    public record SwiftTransferRequest(
        String      endToEndId,
        String      debtorIban,
        String      debtorName,
        String      creditorIban,
        String      creditorName,
        String      creditorBic,
        BigDecimal  amount,
        CurrencyCode currency,
        String      remittanceInfo
    ) {}

    public record SwiftTransferResponse(
        String        uetr,
        String        endToEndId,
        String        status,
        LocalDateTime createdAt,
        String        message
    ) {}

    public record SwiftPaymentStatus(
        String        uetr,
        String        statusCode,    // ACCC, ACSP, RJCT, PDNG
        String        statusLabel,
        LocalDateTime updatedAt,
        String        rejectionReason
    ) {
        public boolean isSettled()  { return "ACCC".equals(statusCode); }
        public boolean isRejected() { return "RJCT".equals(statusCode); }
        public boolean isPending()  { return "PDNG".equals(statusCode) || "ACSP".equals(statusCode); }
    }

    private record Mt103Payload(
        String endToEndId,
        String debtorBic,
        String creditorBic,
        String debtorIban,
        String debtorName,
        String creditorIban,
        String creditorName,
        String amount,
        String currency,
        String remittanceInfo,
        String valueDate
    ) {}

    private record BicValidationResponse(boolean valid, String bicCode, String bankName) {}

    // ─────────────────────────────────────────────────────────
    //  Exception
    // ─────────────────────────────────────────────────────────

    public static class SwiftException extends RuntimeException {
        private final String errorCode;

        public SwiftException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() { return errorCode; }
    }
}