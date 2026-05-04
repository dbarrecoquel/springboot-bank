package com.bank.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bank.domain.enums.CurrencyCode;

public record AccountBlockedEvent (
		UUID eventId,
		LocalDateTime occurredAt,
		UUID accountId,
		String iban,
		UUID userId,
		BigDecimal balanceAtBlock,
		CurrencyCode currency,
		BlockReason reason,
		String description,
		boolean automatic,
		UUID operatorId,
		UUID fraudAlertEventId,
		UUID relatedTransactionId,
		boolean blockAssociatedCards,

	    /**
	     * Indique si une notification réglementaire TRACFIN doit être déclenchée.
	     * Requis pour les motifs LCB-FT (Anti-Money Laundering).
	     */
		boolean requiresTracfinReport
) {
	
    public enum BlockReason {
    	 
        /**
         * Fraude confirmée par l'équipe compliance après analyse.
         * Déclenche le blocage des cartes et la notification TRACFIN.
         */
        FRAUD_CONFIRMED("Fraude confirmée", true, true),
 
        /**
         * Suspicion de fraude — blocage préventif en attente de décision compliance.
         * Réversible si la transaction est confirmée légitime.
         */
        FRAUD_SUSPECTED("Suspicion de fraude", true, false),
 
        /**
         * Investigation LCB-FT (Lutte Contre le Blanchiment et le Financement du Terrorisme).
         * Obligation réglementaire AMLD5 / directive européenne.
         */
        AML_INVESTIGATION("Investigation LCB-FT", true, true),
 
        /**
         * Opposition déposée par le client (perte, vol de carte ou d'identifiants).
         */
        CLIENT_REQUEST("Opposition client", false, false),
 
        /**
         * Solde débiteur persistant au-delà du délai de régularisation.
         */
        OVERDUE_BALANCE("Solde débiteur non régularisé", false, false),
 
        /**
         * Décision judiciaire ou administrative (saisie, gel d'avoirs).
         */
        LEGAL_ORDER("Décision judiciaire / gel d'avoirs", true, true),
 
        /**
         * Blocage décidé par un opérateur interne (compliance, risk management).
         */
        COMPLIANCE_DECISION("Décision compliance interne", false, false),
 
        /**
         * Échec ou expiration de la vérification KYC / renouvellement de pièce d'identité.
         */
        KYC_FAILURE("Échec vérification KYC", false, false);
 
        private final String  label;
        private final boolean cardBlockRecommended;
        private final boolean tracfinReportRequired;
 
        BlockReason(String label, boolean cardBlockRecommended, boolean tracfinReportRequired) {
            this.label                = label;
            this.cardBlockRecommended = cardBlockRecommended;
            this.tracfinReportRequired = tracfinReportRequired;
        }
 
        public String getLabel() {
            return label;
        }
 
        public boolean isCardBlockRecommended() {
            return cardBlockRecommended;
        }
 
        public boolean isTracfinReportRequired() {
            return tracfinReportRequired;
        }
    }
    public AccountBlockedEvent {
        if (eventId    == null) throw new IllegalArgumentException("eventId est obligatoire");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt est obligatoire");
        if (accountId  == null) throw new IllegalArgumentException("accountId est obligatoire");
        if (iban       == null || iban.isBlank()) throw new IllegalArgumentException("iban est obligatoire");
        if (userId     == null) throw new IllegalArgumentException("userId est obligatoire");
        if (reason     == null) throw new IllegalArgumentException("reason est obligatoire");
        if (currency   == null) throw new IllegalArgumentException("currency est obligatoire");
        if (!automatic && operatorId == null)
            throw new IllegalArgumentException("operatorId est obligatoire pour un blocage manuel");
    }
    
    /**
     * Blocage automatique déclenché par le moteur anti-fraude.
     */
    public static AccountBlockedEvent automatic(
            UUID accountId, String iban, UUID userId,
            BigDecimal balanceAtBlock, CurrencyCode currency,
            BlockReason reason, String description,
            UUID fraudAlertEventId, UUID relatedTransactionId) {
 
        return new AccountBlockedEvent(
            UUID.randomUUID(),
            LocalDateTime.now(),
            accountId, iban, userId,
            balanceAtBlock, currency,
            reason, description,
            true,
            null,                   // operatorId — nul pour blocage automatique
            fraudAlertEventId,
            relatedTransactionId,
            reason.isCardBlockRecommended(),
            reason.isTracfinReportRequired()
        );
    }
    /**
     * Blocage manuel décidé par un opérateur (compliance, manager).
     */
    public static AccountBlockedEvent manual(
            UUID accountId, String iban, UUID userId,
            BigDecimal balanceAtBlock, CurrencyCode currency,
            BlockReason reason, String description,
            UUID operatorId) {
 
        return new AccountBlockedEvent(
            UUID.randomUUID(),
            LocalDateTime.now(),
            accountId, iban, userId,
            balanceAtBlock, currency,
            reason, description,
            false,
            operatorId,
            null,   // fraudAlertEventId
            null,   // relatedTransactionId
            reason.isCardBlockRecommended(),
            reason.isTracfinReportRequired()
        );
    }
    /**
     * Indique si cet événement est d'origine réglementaire
     * (LCB-FT, gel d'avoirs, fraude confirmée).
     */
    public boolean isRegulatoryOrigin() {
        return reason == BlockReason.AML_INVESTIGATION
            || reason == BlockReason.LEGAL_ORDER
            || reason == BlockReason.FRAUD_CONFIRMED;
    }

    /**
     * Indique si le blocage est potentiellement réversible.
     * Les blocages réglementaires et fraude confirmée ne le sont pas
     * sans décision explicite d'un manager ou d'un tribunal.
     */
    public boolean isPotentiallyReversible() {
        return reason == BlockReason.FRAUD_SUSPECTED
            || reason == BlockReason.CLIENT_REQUEST
            || reason == BlockReason.OVERDUE_BALANCE
            || reason == BlockReason.KYC_FAILURE
            || reason == BlockReason.COMPLIANCE_DECISION;
    }
    /**
     * IBAN partiellement masqué pour les logs (ex : FR76 **** **** **** **** **12 345).
     */
    public String maskedIban() {
        if (iban == null || iban.length() < 8) return "****";
        return iban.substring(0, 4) + " **** **** " + iban.substring(iban.length() - 6);
    }
    
    

}
