package com.bank.service.api;

import com.bank.domain.entity.Transaction;
import com.bank.domain.event.FraudAlertEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface du service de détection de fraude.
 *
 * <p>Évalue le risque de chaque transaction via un ensemble de règles métier
 * et retourne un score de 0.0 (aucun risque) à 1.0 (fraude quasi-certaine).</p>
 *
 * <p>Règles implémentées :</p>
 * <ul>
 *   <li><strong>Velocity check</strong> — nombre de transactions sur une fenêtre glissante.</li>
 *   <li><strong>Amount threshold</strong> — montant unitaire ou cumulé anormal.</li>
 *   <li><strong>Unusual country</strong> — transaction depuis un pays inhabituel.</li>
 *   <li><strong>Repeated beneficiary</strong> — virements répétés vers le même IBAN.</li>
 *   <li><strong>AML threshold</strong> — montant >= 10 000 € (seuil TRACFIN).</li>
 *   <li><strong>Night activity</strong> — opération entre 00h00 et 05h00.</li>
 *   <li><strong>New device</strong> — premier paiement depuis un appareil inconnu.</li>
 * </ul>
 */
public interface FraudDetectionService {

    /**
     * Évalue le score de risque d'une transaction.
     *
     * <p>Si le score dépasse le seuil configuré ({@code banking.fraud.score-threshold-medium}),
     * la transaction est passée en {@code FRAUD_SUSPECT} et un {@link FraudAlertEvent}
     * est publié sur Kafka.</p>
     *
     * @param transaction transaction à évaluer (doit être persistée, statut PENDING)
     * @param initiatorIp adresse IP de l'initiateur
     * @return résultat de l'analyse contenant le score et les règles déclenchées
     */
    FraudAnalysisResult analyze(Transaction transaction, String initiatorIp);

    /**
     * Vérifie uniquement le velocity check pour un compte donné.
     * Utilisé par les controllers avant même de créer la transaction.
     *
     * @param accountId identifiant du compte
     * @param amount    montant de l'opération envisagée
     * @return {@code true} si les seuils de velocity sont dépassés
     */
    boolean isVelocityExceeded(UUID accountId, BigDecimal amount);

    /**
     * Confirme manuellement une transaction suspecte comme légitime.
     * Réservé aux opérateurs compliance ({@code ROLE_COMPLIANCE}).
     *
     * @param transactionId identifiant de la transaction
     * @param operatorId    identifiant de l'opérateur validant
     * @param justification motif de la validation
     */
    void confirmLegitimate(UUID transactionId, UUID operatorId, String justification);

    /**
     * Confirme manuellement une transaction suspecte comme frauduleuse.
     * Réservé aux opérateurs compliance ({@code ROLE_COMPLIANCE}).
     *
     * @param transactionId identifiant de la transaction
     * @param operatorId    identifiant de l'opérateur
     * @param justification motif du blocage définitif
     */
    void confirmFraud(UUID transactionId, UUID operatorId, String justification);

    // ─────────────────────────────────────────────────────────
    //  Résultat de l'analyse — record immuable
    // ─────────────────────────────────────────────────────────

    /**
     * Résultat complet d'une analyse de fraude.
     *
     * @param transactionId   identifiant de la transaction analysée
     * @param score           score de risque global (0.0 à 1.0)
     * @param severity        niveau de sévérité calculé depuis le score
     * @param triggeredRules  règles ayant contribué au score (séparées par virgule)
     * @param recommendation  action recommandée
     * @param autoBlocked     {@code true} si la transaction a été bloquée automatiquement
     * @param detail          description textuelle pour les logs compliance
     */
    record FraudAnalysisResult(
        UUID                              transactionId,
        BigDecimal                        score,
        FraudAlertEvent.Severity          severity,
        String                            triggeredRules,
        FraudAlertEvent.RecommendedAction recommendation,
        boolean                           autoBlocked,
        String                            detail
    ) {
        public boolean requiresAction() {
            return score.doubleValue() >= 0.40;
        }

        public boolean isCritical() {
            return severity == FraudAlertEvent.Severity.CRITICAL;
        }

        public boolean isClean() {
            return score.doubleValue() < 0.40;
        }
    }
}