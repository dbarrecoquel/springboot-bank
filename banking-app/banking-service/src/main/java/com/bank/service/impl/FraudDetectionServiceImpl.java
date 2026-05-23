package com.bank.service.impl;

import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Transaction;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.FraudAlertEvent.RecommendedAction;
import com.bank.domain.event.FraudAlertEvent.Severity;
import com.bank.infrastructure.cache.SessionCacheService;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du moteur de détection de fraude basé sur des règles métier.
 *
 * <p>Architecture du scoring :</p>
 * <ol>
 *   <li>Chaque règle contribue un score partiel entre 0.0 et 1.0.</li>
 *   <li>Le score global est la somme pondérée, plafonnée à 1.0.</li>
 *   <li>Les seuils de décision sont configurables via {@code application.yml}.</li>
 *   <li>Si le score dépasse {@code threshold-medium}, un {@link FraudAlertEvent}
 *       est publié sur Kafka.</li>
 *   <li>Si le score dépasse {@code threshold-critical}, la transaction et
 *       éventuellement le compte sont bloqués automatiquement.</li>
 * </ol>
 *
 * <p>Poids des règles (configurables) :</p>
 * <pre>
 *   VELOCITY_COUNT     0.30  — trop de transactions en peu de temps
 *   VELOCITY_AMOUNT    0.25  — cumul de montants anormal
 *   AMOUNT_THRESHOLD   0.20  — montant unitaire très élevé
 *   AML_THRESHOLD      0.15  — >= seuil TRACFIN (10 000 €)
 *   REPEATED_BENEFICIARY 0.15 — virements répétés même IBAN
 *   NIGHT_ACTIVITY     0.10  — opération entre 00h et 05h
 *   UNUSUAL_COUNTRY    0.20  — IP hors pays habituel
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionServiceImpl implements FraudDetectionService {

    private final TransactionRepository    transactionRepository;
    private final AuditLogRepository       auditLogRepository;
    private final SessionCacheService      sessionCacheService;
    private final TransactionEventProducer eventProducer;

    // ── Seuils configurables ─────────────────────────────────
    @Value("${banking.fraud.score-threshold-medium:0.40}")
    private double thresholdMedium;

    @Value("${banking.fraud.score-threshold-high:0.70}")
    private double thresholdHigh;

    @Value("${banking.fraud.score-threshold-critical:0.90}")
    private double thresholdCritical;

    @Value("${banking.fraud.velocity-window-minutes:60}")
    private int velocityWindowMinutes;

    @Value("${banking.fraud.velocity-max-transactions:10}")
    private int velocityMaxTransactions;

    @Value("${banking.fraud.velocity-max-amount:5000.00}")
    private BigDecimal velocityMaxAmount;

    @Value("${banking.transaction.aml-threshold:10000.00}")
    private BigDecimal amlThreshold;

    // ─────────────────────────────────────────────────────────
    //  Analyse principale
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FraudAnalysisResult analyze(Transaction transaction, String initiatorIp) {
        log.debug("[FRAUD] Analyse démarrée — txRef={} amount={} ip={}",
                  transaction.getReference(), transaction.getAmount(), initiatorIp);

        List<String> triggeredRules = new ArrayList<>();
        double       totalScore     = 0.0;

        // ── Règle 1 : Velocity count ──────────────────────────
        double velocityCountScore = checkVelocityCount(transaction, triggeredRules);
        totalScore += velocityCountScore * 0.30;

        // ── Règle 2 : Velocity amount ─────────────────────────
        double velocityAmountScore = checkVelocityAmount(transaction, triggeredRules);
        totalScore += velocityAmountScore * 0.25;

        // ── Règle 3 : Montant unitaire élevé ──────────────────
        double amountScore = checkHighAmount(transaction, triggeredRules);
        totalScore += amountScore * 0.20;

        // ── Règle 4 : Seuil AML (TRACFIN) ────────────────────
        double amlScore = checkAmlThreshold(transaction, triggeredRules);
        totalScore += amlScore * 0.15;

        // ── Règle 5 : Bénéficiaire répété ─────────────────────
        double repeatedBeneficiaryScore = checkRepeatedBeneficiary(transaction, triggeredRules);
        totalScore += repeatedBeneficiaryScore * 0.15;

        // ── Règle 6 : Activité nocturne ───────────────────────
        double nightScore = checkNightActivity(transaction, triggeredRules);
        totalScore += nightScore * 0.10;

        // ── Règle 7 : Pays inhabituel (IP) ────────────────────
        double countryScore = checkUnusualCountry(transaction, initiatorIp, triggeredRules);
        totalScore += countryScore * 0.20;

        // Plafonner le score à 1.0
        BigDecimal finalScore = BigDecimal.valueOf(Math.min(totalScore, 1.0))
                                          .setScale(3, RoundingMode.HALF_EVEN);

        Severity          severity   = Severity.fromScore(finalScore);
        RecommendedAction action     = resolveAction(severity);
        String            rulesStr   = String.join(",", triggeredRules);
        boolean           autoBlocked = false;

        log.info("[FRAUD] Score calculé — txRef={} score={} severity={} rules=[{}]",
                 transaction.getReference(), finalScore, severity, rulesStr);

        // ── Actions automatiques ──────────────────────────────
        if (finalScore.doubleValue() >= thresholdMedium) {
            autoBlocked = applyAutomaticAction(transaction, finalScore, severity,
                                               rulesStr, initiatorIp);
        }

        // ── Mise à jour du velocity counter dans Redis ─────────
        updateVelocityCounters(transaction);

        FraudAnalysisResult result = new FraudAnalysisResult(
            transaction.getId(),
            finalScore,
            severity,
            rulesStr,
            action,
            autoBlocked,
            buildDetail(transaction, finalScore, triggeredRules, initiatorIp)
        );

        // ── Audit ─────────────────────────────────────────────
        if (finalScore.doubleValue() >= thresholdMedium) {
            auditLogRepository.save(AuditLog.success(
                "FRAUD_ANALYSIS_FLAGGED",
                "Transaction",
                transaction.getId().toString(),
                transaction.getAccount().getOwner().getId(),
                "Score: " + finalScore + " — Règles: [" + rulesStr + "]"
            ));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────
    //  Velocity check
    // ─────────────────────────────────────────────────────────

    @Override
    public boolean isVelocityExceeded(UUID accountId, BigDecimal amount) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(velocityWindowMinutes);

        long       count      = transactionRepository.countRecentByAccount(accountId, since);
        BigDecimal cumulated  = transactionRepository.sumDebitedAmountSince(accountId, since);

        return count >= velocityMaxTransactions
            || cumulated.add(amount).compareTo(velocityMaxAmount) > 0;
    }

    // ─────────────────────────────────────────────────────────
    //  Décisions manuelles compliance
    // ─────────────────────────────────────────────────────────

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
    //  Règles de scoring
    // ─────────────────────────────────────────────────────────

    /**
     * Règle 1 — Velocity count : trop de transactions sur la fenêtre glissante.
     */
    private double checkVelocityCount(Transaction tx, List<String> rules) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
        long count = transactionRepository.countRecentByAccount(
            tx.getAccount().getId(), since);

        if (count >= velocityMaxTransactions) {
            rules.add("VELOCITY_COUNT_EXCEEDED");
            return 1.0;
        }
        if (count >= velocityMaxTransactions * 0.7) {
            rules.add("VELOCITY_COUNT_HIGH");
            return 0.5;
        }
        return 0.0;
    }

    /**
     * Règle 2 — Velocity amount : cumul des montants anormal sur la fenêtre.
     */
    private double checkVelocityAmount(Transaction tx, List<String> rules) {
        LocalDateTime since   = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
        BigDecimal    cumulated = transactionRepository
            .sumDebitedAmountSince(tx.getAccount().getId(), since);

        BigDecimal projected = cumulated.add(tx.getAmount());

        if (projected.compareTo(velocityMaxAmount) > 0) {
            rules.add("VELOCITY_AMOUNT_EXCEEDED");
            // Score proportionnel au dépassement (plafonné à 1.0)
            double ratio = projected.divide(velocityMaxAmount, 4, RoundingMode.HALF_EVEN)
                                    .doubleValue();
            return Math.min(ratio - 1.0 + 0.5, 1.0);
        }
        return 0.0;
    }

    /**
     * Règle 3 — Montant unitaire très élevé par rapport au solde disponible.
     */
    private double checkHighAmount(Transaction tx, List<String> rules) {
        BigDecimal balance = tx.getAccount().getBalance();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) return 0.0;

        // Score si le montant représente plus de 80% du solde
        BigDecimal ratio = tx.getAmount()
            .divide(balance, 4, RoundingMode.HALF_EVEN);

        if (ratio.doubleValue() > 0.95) {
            rules.add("HIGH_AMOUNT_RATIO");
            return 0.8;
        }
        if (ratio.doubleValue() > 0.80) {
            rules.add("MEDIUM_AMOUNT_RATIO");
            return 0.4;
        }
        return 0.0;
    }

    /**
     * Règle 4 — Seuil AML : montant >= 10 000 € (obligation TRACFIN).
     */
    private double checkAmlThreshold(Transaction tx, List<String> rules) {
        if (tx.getAmount().compareTo(amlThreshold) >= 0) {
            rules.add("AML_THRESHOLD");
            // Score proportionnel au dépassement du seuil
            double multiplier = tx.getAmount()
                .divide(amlThreshold, 4, RoundingMode.HALF_EVEN)
                .doubleValue();
            return Math.min(0.3 + (multiplier - 1.0) * 0.1, 0.8);
        }
        return 0.0;
    }

    /**
     * Règle 5 — Bénéficiaire répété : virements multiples vers le même IBAN
     * sur une courte période.
     */
    private double checkRepeatedBeneficiary(Transaction tx, List<String> rules) {
        if (tx.getCounterpartIban() == null) return 0.0;

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        var repeated = transactionRepository.findRecentByCounterpartIban(
            tx.getAccount().getId(), tx.getCounterpartIban(), since);

        if (repeated.size() >= 5) {
            rules.add("REPEATED_BENEFICIARY_HIGH");
            return 0.9;
        }
        if (repeated.size() >= 3) {
            rules.add("REPEATED_BENEFICIARY");
            return 0.5;
        }
        return 0.0;
    }

    /**
     * Règle 6 — Activité nocturne : opération entre 00h00 et 05h00 UTC.
     * Les transactions de nuit sont statistiquement plus souvent frauduleuses.
     */
    private double checkNightActivity(Transaction tx, List<String> rules) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(LocalTime.MIDNIGHT) && now.isBefore(LocalTime.of(5, 0))) {
            rules.add("NIGHT_ACTIVITY");
            return 0.6;
        }
        return 0.0;
    }

    /**
     * Règle 7 — Pays inhabituel : IP hors de la zone SEPA ou pays non reconnu.
     * Implémentation simplifiée — en production, utiliser une base GeoIP (MaxMind).
     */
    private double checkUnusualCountry(Transaction tx, String initiatorIp,
                                        List<String> rules) {
        if (initiatorIp == null || initiatorIp.isBlank()) return 0.0;

        // En production : appel à un service de géolocalisation IP
        // Ici : détection simpliste des plages IP non-européennes connues
        boolean isLocalOrPrivate = initiatorIp.startsWith("127.")
            || initiatorIp.startsWith("192.168.")
            || initiatorIp.startsWith("10.")
            || initiatorIp.equals("::1");

        if (isLocalOrPrivate) return 0.0; // dev / test

        // Placeholder — en prod remplacer par MaxMind GeoIP2
        // Si IP hors SEPA détectée :
        // rules.add("UNUSUAL_COUNTRY");
        // return 0.7;

        return 0.0;
    }

    // ─────────────────────────────────────────────────────────
    //  Actions automatiques
    // ─────────────────────────────────────────────────────────

    private boolean applyAutomaticAction(Transaction transaction, BigDecimal score,
                                          Severity severity, String rules,
                                          String initiatorIp) {
        boolean autoBlocked = false;

        // Passer la transaction en FRAUD_SUSPECT
        transactionRepository.flagFraud(
            transaction.getId(), score, LocalDateTime.now());

        // Bloquer automatiquement si score critique
        if (score.doubleValue() >= thresholdCritical) {
            transactionRepository.updateStatus(
                transaction.getId(), TransactionStatus.BLOCKED, LocalDateTime.now());
            autoBlocked = true;
            log.warn("[FRAUD] Transaction bloquée automatiquement — txRef={} score={}",
                     transaction.getReference(), score);
        }

        // Publier FraudAlertEvent sur Kafka
        FraudAlertEvent event = FraudAlertEvent.of(
            transaction.getId(),
            transaction.getReference(),
            transaction.getAccount().getId(),
            transaction.getAccount().getOwner().getId(),
            score,
            rules,
            buildDetail(transaction, score, List.of(rules.split(",")), initiatorIp),
            autoBlocked,
            initiatorIp,
            null  // ipCountryCode — résolu par le consommateur
        );

        // Synchrone pour les cas critiques
        boolean waitAck = score.doubleValue() >= thresholdCritical;
        eventProducer.publishFraudAlert(event, waitAck);

        return autoBlocked;
    }

    // ─────────────────────────────────────────────────────────
    //  Velocity counters Redis
    // ─────────────────────────────────────────────────────────

    private void updateVelocityCounters(Transaction transaction) {
        String windowKey = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        long ttlSeconds = (long) velocityWindowMinutes * 60 + 300;

        try {
            sessionCacheService.incrementVelocityCounter(
                transaction.getAccount().getId(), windowKey, ttlSeconds);
        } catch (Exception ex) {
            // Redis indisponible — ne pas bloquer l'analyse
            log.warn("[FRAUD] Velocity counter Redis indisponible — txRef={} error={}",
                     transaction.getReference(), ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private RecommendedAction resolveAction(Severity severity) {
        return switch (severity) {
            case LOW      -> RecommendedAction.MONITOR;
            case MEDIUM   -> RecommendedAction.REQUEST_STRONG_AUTH;
            case HIGH     -> RecommendedAction.BLOCK_TRANSACTION;
            case CRITICAL -> RecommendedAction.BLOCK_ACCOUNT_AND_CARD;
        };
    }

    private String buildDetail(Transaction tx, BigDecimal score,
                                List<String> rules, String ip) {
        return String.format(
            "txRef=%s amount=%s %s accountId=%s score=%s rules=[%s] ip=%s",
            tx.getReference(), tx.getAmount(), tx.getCurrency(),
            tx.getAccount().getId(), score,
            String.join(",", rules), ip
        );
    }
}