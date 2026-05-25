package com.bank.service.impl;

import com.bank.domain.entity.AuditLog;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.api.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Moteur de détection de fraude — calcul de score de risque par accumulation de règles.
 *
 * <p>Règles et poids :</p>
 * <pre>
 *   AMOUNT_THRESHOLD       0.35+  montant >= seuil AML (10 000 €)
 *   VELOCITY_COUNT         0.25+  nb transactions > max/heure
 *   VELOCITY_AMOUNT        0.20+  montant cumulé > max/heure
 *   INTERNATIONAL_TRANSFER 0.15   virement hors SEPA
 *   HIGH_RISK_COUNTRY      0.20   IBAN / IP pays à risque élevé
 *   CARD_TESTING           0.30   petits paiements carte répétés
 *   FIRST_TRANSFER_TO_IBAN 0.10   nouveau bénéficiaire
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionServiceImpl implements FraudDetectionService {

    private final TransactionRepository    transactionRepository;
    private final TransactionEventProducer eventProducer;
    private final AuditLogRepository auditLogRepository;

    @Value("${banking.fraud.score-threshold-medium:0.40}")
    BigDecimal scoreThresholdMedium;

    @Value("${banking.fraud.score-threshold-high:0.70}")
    BigDecimal scoreThresholdHigh;

    @Value("${banking.fraud.score-threshold-critical:0.90}")
    BigDecimal scoreThresholdCritical;

    @Value("${banking.fraud.velocity-window-minutes:60}")
    int velocityWindowMinutes;

    @Value("${banking.fraud.velocity-max-transactions:10}")
    int velocityMaxTransactions;

    @Value("${banking.fraud.velocity-max-amount:5000.00}")
    BigDecimal velocityMaxAmount;

    @Value("${banking.fraud.aml-threshold:10000.00}")
    BigDecimal amlThreshold;

    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "NG", "KP", "IR", "MM", "PK", "SY", "YE", "SO", "AF", "LY"
    );

    // ─────────────────────────────────────────────────────────
    //  calculateRiskScore
    // ─────────────────────────────────────────────────────────

    @Override
    public BigDecimal calculateRiskScore(TransactionCreatedEvent event) {
        List<String> rules    = new ArrayList<>();
        double       score    = 0.0;
        LocalDateTime window  = LocalDateTime.now().minusMinutes(velocityWindowMinutes);

        // Règle 1 — AMOUNT_THRESHOLD
        if (event.amount().compareTo(amlThreshold) >= 0) {
            double ratio  = event.amount().doubleValue() / amlThreshold.doubleValue();
            double weight = Math.min(0.35 + (ratio - 1.0) * 0.05, 0.60);
            score += weight;
            rules.add("AMOUNT_THRESHOLD");
        }

        // Règle 2 — VELOCITY_COUNT
        long count = transactionRepository.countRecentByAccount(event.accountId(), window);
        if (count >= velocityMaxTransactions) {
            double excess = (double) count / velocityMaxTransactions - 1.0;
            score += Math.min(0.25 + excess * 0.10, 0.40);
            rules.add("VELOCITY_COUNT");
        }

        // Règle 3 — VELOCITY_AMOUNT
        BigDecimal cumul = transactionRepository.sumDebitedAmountSince(event.accountId(), window);
        if (cumul != null && cumul.compareTo(velocityMaxAmount) >= 0) {
            double ratio = cumul.doubleValue() / velocityMaxAmount.doubleValue();
            score += Math.min(0.20 * ratio, 0.30);
            rules.add("VELOCITY_AMOUNT");
        }

        // Règle 4 — INTERNATIONAL_TRANSFER
        if (event.type() == TransactionType.INTERNATIONAL_TRANSFER) {
            score += 0.15;
            rules.add("INTERNATIONAL_TRANSFER");
        }

        // Règle 5 — HIGH_RISK_COUNTRY (IBAN ou IP)
        if (isHighRiskCountry(event.counterpartIban())
                || isHighRiskIp(event.initiatorIp())) {
            score += 0.20;
            rules.add("HIGH_RISK_COUNTRY");
        }

        // Règle 6 — CARD_TESTING (petits montants répétés)
        if (event.type() == TransactionType.CARD_PAYMENT
                && event.amount().compareTo(new BigDecimal("5.00")) <= 0
                && count >= 3) {
            score += 0.30;
            rules.add("CARD_TESTING");
        }

        // Règle 7 — FIRST_TRANSFER_TO_IBAN
        if (event.counterpartIban() != null
                && isFirstTransferToIban(event.accountId(), event.counterpartIban())) {
            score += 0.10;
            rules.add("FIRST_TRANSFER_TO_IBAN");
        }

        BigDecimal finalScore = BigDecimal.valueOf(Math.min(score, 1.0))
            .setScale(3, RoundingMode.HALF_UP);

        log.info("[FRAUD] Score — ref={} score={} rules=[{}]",
                 event.reference(), finalScore, String.join(",", rules));

        return finalScore;
    }

    // ─────────────────────────────────────────────────────────
    //  analyze
    // ─────────────────────────────────────────────────────────

    @Override
    public void analyze(TransactionCreatedEvent event) {
        BigDecimal score = calculateRiskScore(event);

        if (score.compareTo(scoreThresholdMedium) < 0) {
            return;
        }

        boolean autoBlocked = score.compareTo(scoreThresholdCritical) >= 0;
        boolean waitForAck  = autoBlocked;

        FraudAlertEvent alert = FraudAlertEvent.of(
            event.transactionId(),
            event.reference(),
            event.accountId(),
            event.userId(),
            score,
            buildRulesString(event, score),
            buildDescription(score),
            autoBlocked,
            event.initiatorIp(),
            extractCountry(event.initiatorIp())
        );

        eventProducer.publishFraudAlert(alert, waitForAck);

        log.warn("[FRAUD] Alerte — ref={} score={} severity={} blocked={}",
                 event.reference(), score, alert.severity(), autoBlocked);
    }

    // ─────────────────────────────────────────────────────────
    //  isVelocityAnormal
    // ─────────────────────────────────────────────────────────

    @Override
    public boolean isVelocityAnormal(UUID accountId) {
        LocalDateTime window = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
        long       count  = transactionRepository.countRecentByAccount(accountId, window);
        BigDecimal amount = transactionRepository.sumDebitedAmountSince(accountId, window);
        return count >= velocityMaxTransactions
            || (amount != null && amount.compareTo(velocityMaxAmount) >= 0);
    }
    @Override
    @Transactional
    public void confirmLegitimate(UUID transactionId, UUID operatorId, String justification) {
        int updated = transactionRepository.updateStatus(
            transactionId, TransactionStatus.CONFIRMED, LocalDateTime.now());

        if (updated == 0) {
            log.warn("[FRAUD] confirmLegitimate — transaction introuvable : {}", transactionId);
            return;
        }

        auditLogRepository.save(
            AuditLog.success(
                "FRAUD_CONFIRMED_LEGITIMATE",
                "Transaction",
                transactionId.toString(),
                operatorId,
                "Validé par opérateur compliance — " + justification
            )
        );

        log.info("[FRAUD] Transaction confirmée légitime — txId={} operatorId={}",
                 transactionId, operatorId);
    }
    @Override
    @Transactional
    public void confirmFraud(UUID transactionId, UUID operatorId, String justification) {
        int updated = transactionRepository.updateStatus(
            transactionId, TransactionStatus.BLOCKED, LocalDateTime.now());

        if (updated == 0) {
            log.warn("[FRAUD] confirmFraud — transaction introuvable : {}", transactionId);
            return;
        }

        auditLogRepository.save(
            AuditLog.failure(
                "FRAUD_CONFIRMED_FRAUDULENT",
                "Transaction",
                transactionId.toString(),
                operatorId,
                "Fraude confirmée par opérateur — " + justification
            )
        );

        log.warn("[FRAUD] Fraude confirmée manuellement — txId={} operatorId={}",
                 transactionId, operatorId);
    }
    // ─────────────────────────────────────────────────────────
    //  Helpers privés
    // ─────────────────────────────────────────────────────────

    private boolean isHighRiskCountry(String iban) {
        if (iban == null || iban.length() < 2) return false;
        return HIGH_RISK_COUNTRIES.contains(iban.substring(0, 2).toUpperCase());
    }

    private boolean isHighRiskIp(String ip) {
        if (ip == null) return false;
        return ip.startsWith("41.202.") || ip.startsWith("175.45.") || ip.startsWith("85.204.");
    }

    private boolean isFirstTransferToIban(UUID accountId, String iban) {
        return transactionRepository.findRecentByCounterpartIban(
            accountId, iban, LocalDateTime.now().minusMonths(12)).isEmpty();
    }

    private String extractCountry(String ip) {
        if (ip == null) return "XX";
        return isHighRiskIp(ip) ? "HIGH_RISK" : "FR";
    }

    private String buildRulesString(TransactionCreatedEvent event, BigDecimal score) {
        List<String> rules = new ArrayList<>();
        LocalDateTime window = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
        long       count  = transactionRepository.countRecentByAccount(event.accountId(), window);
        BigDecimal cumul  = transactionRepository.sumDebitedAmountSince(event.accountId(), window);

        if (event.amount().compareTo(amlThreshold) >= 0)         rules.add("AMOUNT_THRESHOLD");
        if (count >= velocityMaxTransactions)                     rules.add("VELOCITY_COUNT");
        if (cumul != null && cumul.compareTo(velocityMaxAmount) >= 0) rules.add("VELOCITY_AMOUNT");
        if (event.type() == TransactionType.INTERNATIONAL_TRANSFER) rules.add("INTERNATIONAL_TRANSFER");
        if (isHighRiskCountry(event.counterpartIban())
                || isHighRiskIp(event.initiatorIp()))             rules.add("HIGH_RISK_COUNTRY");
        if (event.type() == TransactionType.CARD_PAYMENT
                && event.amount().compareTo(new BigDecimal("5.00")) <= 0
                && count >= 3)                                    rules.add("CARD_TESTING");
        return String.join(",", rules);
    }

    private String buildDescription(BigDecimal score) {
        return switch (FraudAlertEvent.Severity.fromScore(score)) {
            case LOW      -> "Anomalie mineure — surveillance renforcée";
            case MEDIUM   -> "Anomalie significative — revue compliance requise";
            case HIGH     -> "Anomalie grave — blocage transaction recommandé";
            case CRITICAL -> "Fraude quasi-certaine — blocage immédiat";
        };
    }
}