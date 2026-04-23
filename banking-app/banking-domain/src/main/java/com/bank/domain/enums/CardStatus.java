package com.bank.domain.enums;

/**
 * Statut du cycle de vie d'une carte bancaire.
 *
 * <p>Transitions autorisées :</p>
 * <pre>
 *   INACTIVE ──► ACTIVE ──► BLOCKED ──► ACTIVE
 *                       └──────────────► EXPIRED   (automatique, tâche planifiée)
 *                       └──────────────► CANCELLED
 *   INACTIVE ──► CANCELLED
 *   BLOCKED  ──► CANCELLED
 *   EXPIRED  ──► (terminal)
 *   CANCELLED──► (terminal)
 * </pre>
 */
public enum CardStatus {

    /**
     * Carte émise mais non encore activée par le porteur.
     * Aucun paiement n'est autorisé.
     * État initial à la création ou après renouvellement.
     */
    INACTIVE("Inactive"),

    /**
     * Carte active — paiements autorisés dans les limites configurées.
     */
    ACTIVE("Active"),

    /**
     * Carte temporairement bloquée.
     * Causes : 3 tentatives de PIN erronées, opposition client, suspicion de fraude.
     * Réversible vers {@link #ACTIVE} après déblocage (validation agence ou appli).
     */
    BLOCKED("Bloquée"),

    /**
     * Carte expirée (date d'expiration dépassée).
     * Transition automatique gérée par une tâche planifiée ({@code CardExpiryScheduler}).
     * Statut terminal — aucune opération possible.
     */
    EXPIRED("Expirée"),

    /**
     * Carte définitivement annulée (perte, vol, fermeture de compte).
     * Statut terminal — aucune réactivation possible.
     * Une nouvelle carte doit être émise si nécessaire.
     */
    CANCELLED("Annulée");

    // ─────────────────────────────────────────────────────────
    //  Attributs
    // ─────────────────────────────────────────────────────────

    private final String label;

    CardStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers métier
    // ─────────────────────────────────────────────────────────

    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }

    public boolean allowsPayment() {
        return this == ACTIVE;
    }

    /**
     * Indique si la transition vers le statut cible est autorisée.
     */
    public boolean canTransitionTo(CardStatus target) {
        if (this.isTerminal()) return false;
        if (this == target)    return false;
        return switch (this) {
            case INACTIVE -> target == ACTIVE    || target == CANCELLED;
            case ACTIVE   -> target == BLOCKED   || target == EXPIRED || target == CANCELLED;
            case BLOCKED  -> target == ACTIVE    || target == CANCELLED;
            default       -> false;
        };
    }
}