package com.bank.domain.enums;

/**
 * Type de compte bancaire.
 *
 * <p>Détermine les règles de gestion applicables au compte :</p>
 * <ul>
 *   <li>{@link #CURRENT}  — compte courant, opérations quotidiennes, découvert possible.</li>
 *   <li>{@link #SAVINGS}  — compte épargne, rémunéré, retraits limités réglementairement.</li>
 *   <li>{@link #CREDIT}   — compte de crédit revolving, ligne de crédit autorisée.</li>
 *   <li>{@link #JOINT}    — compte joint, plusieurs titulaires.</li>
 *   <li>{@link #BUSINESS} — compte professionnel, personne morale.</li>
 * </ul>
 */
public enum AccountType {

    /**
     * Compte courant (dépôt à vue).
     * Caractéristiques :
     * - Virements et prélèvements SEPA illimités
     * - Découvert autorisé configurable
     * - Pas de rémunération du solde
     * - Carte bancaire associable
     */
    CURRENT("Compte courant"),

    /**
     * Compte épargne rémunéré.
     * Caractéristiques :
     * - Taux d'intérêt annuel applicable (champ {@code interestRate})
     * - Nombre de retraits mensuel limité (réglementation Livret A : 12/mois)
     * - Pas de découvert autorisé
     * - Pas de carte bancaire directement associée
     */
    SAVINGS("Compte épargne"),

    /**
     * Compte de crédit revolving.
     * Caractéristiques :
     * - Ligne de crédit définie par {@code overdraftLimit}
     * - Taux débiteur applicable
     * - Remboursement mensuel minimum obligatoire
     */
    CREDIT("Compte crédit"),

    /**
     * Compte joint (co-titulaires).
     * Caractéristiques :
     * - Plusieurs propriétaires (relation User n-n)
     * - Chaque titulaire peut opérer individuellement
     * - Solidarité passive en cas de découvert
     */
    JOINT("Compte joint"),

    /**
     * Compte professionnel (personne morale ou entrepreneur).
     * Caractéristiques :
     * - KYB (Know Your Business) requis à l'ouverture
     * - Plafonds de virement plus élevés
     * - Fonctionnalités comptables étendues
     */
    BUSINESS("Compte professionnel");

    // ─────────────────────────────────────────────────────────
    //  Attributs
    // ─────────────────────────────────────────────────────────

    /** Libellé lisible destiné aux IHM et aux logs. */
    private final String label;

    // ─────────────────────────────────────────────────────────
    //  Constructeur
    // ─────────────────────────────────────────────────────────

    AccountType(String label) {
        this.label = label;
    }

    // ─────────────────────────────────────────────────────────
    //  Accesseurs
    // ─────────────────────────────────────────────────────────

    public String getLabel() {
        return label;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers métier
    // ─────────────────────────────────────────────────────────

    /**
     * Indique si ce type de compte autorise un découvert.
     * Seuls {@link #CURRENT}, {@link #JOINT} et {@link #BUSINESS} le permettent.
     */
    public boolean supportsOverdraft() {
        return this == CURRENT || this == JOINT || this == BUSINESS;
    }

    /**
     * Indique si ce type de compte produit des intérêts créditeurs.
     */
    public boolean isInterestBearing() {
        return this == SAVINGS;
    }

    /**
     * Indique si ce type de compte est soumis à des plafonds de retrait réglementaires.
     */
    public boolean hasWithdrawalLimit() {
        return this == SAVINGS;
    }

    /**
     * Indique si ce type de compte nécessite une vérification KYB (entreprise).
     */
    public boolean requiresKyb() {
        return this == BUSINESS;
    }

    /**
     * Retrouve un {@code AccountType} depuis son libellé (insensible à la casse).
     *
     * @param label libellé à rechercher
     * @return l'enum correspondant
     * @throws IllegalArgumentException si aucun type ne correspond
     */
    public static AccountType fromLabel(String label) {
        for (AccountType type : values()) {
            if (type.label.equalsIgnoreCase(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Type de compte inconnu : " + label);
    }
}