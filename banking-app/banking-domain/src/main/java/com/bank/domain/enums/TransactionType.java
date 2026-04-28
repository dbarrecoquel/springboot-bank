package com.bank.domain.enums;

/**
 * Type d'opération bancaire.
 *
 * <p>Détermine le sens du flux (débit/crédit), le canal de traitement
 * (SEPA, SWIFT, carte…) et les règles de plafonnement applicables.</p>
 */
public enum TransactionType {

    // ── Virements ────────────────────────────────────────────

    /**
     * Virement SEPA instantané (SCT Inst) ou standard (SCT).
     * Débit du compte émetteur, crédit du compte bénéficiaire.
     */
    SEPA_TRANSFER("Virement SEPA", FlowDirection.DEBIT, true),

    /**
     * Virement international hors SEPA (réseau SWIFT / IBAN non SEPA).
     * Soumis à des frais et délais supplémentaires.
     */
    INTERNATIONAL_TRANSFER("Virement international", FlowDirection.DEBIT, true),

    /**
     * Virement interne entre deux comptes du même client (même banque).
     */
    INTERNAL_TRANSFER("Virement interne", FlowDirection.DEBIT, false),

    // ── Dépôts & retraits ────────────────────────────────────

    /**
     * Dépôt d'espèces en agence ou via automate.
     */
    CASH_DEPOSIT("Dépôt espèces", FlowDirection.CREDIT, false),

    /**
     * Retrait d'espèces au DAB (distributeur automatique).
     */
    CASH_WITHDRAWAL("Retrait DAB", FlowDirection.DEBIT, false),

    // ── Paiements ────────────────────────────────────────────

    /**
     * Paiement par carte (TPE, en ligne, sans contact).
     */
    CARD_PAYMENT("Paiement carte", FlowDirection.DEBIT, false),

    /**
     * Remboursement suite à un paiement par carte (retour marchand).
     */
    CARD_REFUND("Remboursement carte", FlowDirection.CREDIT, false),

    // ── Prélèvements & domiciliations ────────────────────────

    /**
     * Prélèvement SEPA automatique (mandat domicilié).
     */
    DIRECT_DEBIT("Prélèvement SEPA", FlowDirection.DEBIT, true),

    /**
     * Remboursement d'un prélèvement SEPA (opposition sous 8 semaines).
     */
    DIRECT_DEBIT_REFUND("Remboursement prélèvement", FlowDirection.CREDIT, true),

    // ── Intérêts & frais ─────────────────────────────────────

    /**
     * Crédit d'intérêts sur compte épargne.
     */
    INTEREST_CREDIT("Intérêts créditeurs", FlowDirection.CREDIT, false),

    /**
     * Débit d'intérêts débiteurs (découvert, crédit revolving).
     */
    INTEREST_DEBIT("Intérêts débiteurs", FlowDirection.DEBIT, false),

    /**
     * Frais bancaires (cotisation carte, frais de tenue de compte, commissions).
     */
    FEE("Frais bancaires", FlowDirection.DEBIT, false),

    // ── Opérations de change ─────────────────────────────────

    /**
     * Conversion de devise (ex : EUR → USD).
     */
    CURRENCY_EXCHANGE("Opération de change", FlowDirection.BOTH, true);

    // ─────────────────────────────────────────────────────────
    //  Direction du flux monétaire
    // ─────────────────────────────────────────────────────────

    public enum FlowDirection {
        DEBIT,   // sortie d'argent du compte
        CREDIT,  // entrée d'argent sur le compte
        BOTH     // les deux (ex : change : débit EUR + crédit USD)
    }

    // ─────────────────────────────────────────────────────────
    //  Attributs
    // ─────────────────────────────────────────────────────────

    private final String        label;
    private final FlowDirection flowDirection;

    /**
     * Indique si ce type de transaction est soumis à des contrôles
     * anti-blanchiment renforcés (LCB-FT / AMLD5).
     */
    private final boolean amlControlRequired;

    // ─────────────────────────────────────────────────────────
    //  Constructeur
    // ─────────────────────────────────────────────────────────

    TransactionType(String label, FlowDirection flowDirection, boolean amlControlRequired) {
        this.label              = label;
        this.flowDirection      = flowDirection;
        this.amlControlRequired = amlControlRequired;
    }

    // ─────────────────────────────────────────────────────────
    //  Accesseurs
    // ─────────────────────────────────────────────────────────

    public String getLabel() {
        return label;
    }

    public FlowDirection getFlowDirection() {
        return flowDirection;
    }

    public boolean isAmlControlRequired() {
        return amlControlRequired;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers métier
    // ─────────────────────────────────────────────────────────

    public boolean isDebit() {
        return flowDirection == FlowDirection.DEBIT || flowDirection == FlowDirection.BOTH;
    }

    public boolean isCredit() {
        return flowDirection == FlowDirection.CREDIT || flowDirection == FlowDirection.BOTH;
    }

    public boolean isCrossBorder() {
        return this == INTERNATIONAL_TRANSFER || this == CURRENCY_EXCHANGE;
    }

    public boolean isCardOperation() {
        return this == CARD_PAYMENT || this == CARD_REFUND || this == CASH_WITHDRAWAL;
    }
}