package com.bank.domain.enums;


/**
 * Statut du cycle de vie d'une transaction bancaire.
 *
 * <p>Graphe de transitions :</p>
 * <pre>
 *                       ┌─────────────┐
 *                       │   PENDING   │
 *                       └──────┬──────┘
 *              ┌───────────────┼───────────────┐
 *              ▼               ▼               ▼
 *       ┌────────────┐  ┌───────────┐  ┌───────────────┐
 *       │ PROCESSING │  │ CANCELLED │  │ FRAUD_SUSPECT │
 *       └─────┬──────┘  └───────────┘  └───────┬───────┘
 *             │                                 │
 *      ┌──────┴──────┐                  ┌───────┴───────┐
 *      ▼             ▼                  ▼               ▼
 *  ┌──────────┐ ┌─────────┐      ┌───────────┐  ┌───────────┐
 *  │ APPROVED │ │ REFUSED │      │ CONFIRMED │  │  BLOCKED  │
 *  └────┬─────┘ └─────────┘      └───────────┘  └───────────┘
 *       │
 *  ┌────┴────┐
 *  ▼         ▼
 * ┌──────────┐ ┌───────────┐
 * │ SETTLED  │ │ REVERSED  │
 * └──────────┘ └───────────┘
 * </pre>
 *
 * <p>Les statuts {@code SETTLED}, {@code REFUSED}, {@code CANCELLED},
 * {@code REVERSED} et {@code BLOCKED} sont terminaux.</p>
 */
public enum TransactionStatus {

    /**
     * Transaction initiée, en attente de traitement.
     * Le montant est réservé (provision) sur le compte émetteur.
     */
    PENDING("En attente"),

    /**
     * Transaction en cours de traitement (appel réseau, compensation).
     */
    PROCESSING("En cours de traitement"),

    /**
     * Transaction approuvée par les systèmes internes.
     * En attente de règlement définitif (J+1 pour SEPA standard).
     */
    APPROVED("Approuvée"),

    /**
     * Transaction définitivement réglée — les fonds ont changé de propriétaire.
     * Statut terminal.
     */
    SETTLED("Réglée"),

    /**
     * Transaction refusée (fonds insuffisants, limite dépassée, données invalides).
     * La provision est libérée. Statut terminal.
     */
    REFUSED("Refusée"),

    /**
     * Transaction annulée avant traitement (à l'initiative du client ou du système).
     * La provision est libérée. Statut terminal.
     */
    CANCELLED("Annulée"),

    /**
     * Transaction approuvée puis annulée a posteriori (remboursement, erreur).
     * Génère une transaction inverse de type {@code CARD_REFUND} ou {@code SEPA_TRANSFER}.
     * Statut terminal.
     */
    REVERSED("Annulée a posteriori"),

    /**
     * Transaction signalée comme suspecte par le moteur anti-fraude.
     * En attente de décision compliance (vers {@code CONFIRMED} ou {@code BLOCKED}).
     */
    FRAUD_SUSPECT("Suspicion de fraude"),

    /**
     * Transaction suspecte confirmée comme légitime par compliance.
     * Reprend le traitement normal.
     */
    CONFIRMED("Confirmée après analyse"),

    /**
     * Transaction définitivement bloquée suite à fraude avérée.
     * Statut terminal.
     */
    BLOCKED("Bloquée — fraude");

    // ─────────────────────────────────────────────────────────
    //  Attributs
    // ─────────────────────────────────────────────────────────

    private final String label;

    TransactionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers métier
    // ─────────────────────────────────────────────────────────

    public boolean isTerminal() {
        return switch (this) {
            case SETTLED, REFUSED, CANCELLED, REVERSED, BLOCKED -> true;
            default -> false;
        };
    }

    public boolean isFinal() {
        return this == SETTLED || this == REVERSED;
    }

    public boolean requiresComplianceReview() {
        return this == FRAUD_SUSPECT;
    }

    /**
     * Indique si la provision sur le compte émetteur doit être maintenue.
     */
    public boolean holdsFunds() {
        return this == PENDING || this == PROCESSING
            || this == APPROVED || this == FRAUD_SUSPECT;
    }

    /**
     * Indique si la transition vers le statut cible est autorisée.
     */
    public boolean canTransitionTo(TransactionStatus target) {
        if (this.isTerminal()) return false;
        if (this == target)    return false;
        return switch (this) {
            case PENDING        -> target == PROCESSING
                                || target == CANCELLED
                                || target == FRAUD_SUSPECT;
            case PROCESSING     -> target == APPROVED
                                || target == REFUSED
                                || target == FRAUD_SUSPECT;
            case APPROVED       -> target == SETTLED
                                || target == REVERSED;
            case FRAUD_SUSPECT  -> target == CONFIRMED
                                || target == BLOCKED;
            case CONFIRMED      -> target == APPROVED
                                || target == REFUSED;
            default             -> false;
        };
    }
}