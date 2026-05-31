package com.bank.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


public record FraudAlertEvent(
		UUID eventId,
		LocalDateTime occurredAt,
		UUID transactionId,
		String transactionReference,
		UUID accountId,
		UUID userId,
		Severity severity,
		BigDecimal riskScore,
		String triggeredRules,
		String description,
		RecommendedAction recommendedAction,
		boolean transactionAutoBlocked,
		String initiatorIp,
		String ipCountryCode
		) {
	
	public enum Severity {
		LOW,
		MEDIUM,
		HIGH,
		CRITICAL;
	
		public static Severity fromScore(BigDecimal score) {
			double s = score.doubleValue();
			if (s >= 0.95) return CRITICAL;
			if (s >= 0.75) return HIGH;
			if (s >= 0.40) return MEDIUM;
			return LOW;
		}
	}
	
	public enum RecommendedAction {
		MONITOR,
		REQUEST_STRONG_AUTH,
		BLOCK_TRANSACTION,
		BLOCK_ACCOUNT,
		BLOCK_ACCOUNT_AND_CARD
	}
	
    public FraudAlertEvent {
        if (eventId               == null) throw new IllegalArgumentException("eventId est obligatoire");
        if (occurredAt            == null) throw new IllegalArgumentException("occurredAt est obligatoire");
        if (transactionId         == null) throw new IllegalArgumentException("transactionId est obligatoire");
        if (accountId             == null) throw new IllegalArgumentException("accountId est obligatoire");
        if (userId                == null) throw new IllegalArgumentException("userId est obligatoire");
        if (severity              == null) throw new IllegalArgumentException("severity est obligatoire");
        if (riskScore             == null) throw new IllegalArgumentException("riskScore est obligatoire");
        if (recommendedAction     == null) throw new IllegalArgumentException("recommendedAction est obligatoire");
        if (riskScore.doubleValue() < 0 || riskScore.doubleValue() > 1)
            throw new IllegalArgumentException("riskScore doit être compris entre 0.0 et 1.0");
    }
    
    public static FraudAlertEvent of(
            UUID transactionId, String transactionReference,
            UUID accountId, UUID userId,
            BigDecimal riskScore, String triggeredRules, String description,
            boolean autoBlocked, String initiatorIp, String ipCountryCode) {
 
        Severity          severity = Severity.fromScore(riskScore);
        RecommendedAction action   = resolveAction(severity, autoBlocked);
 
        return new FraudAlertEvent(
            UUID.randomUUID(),
            LocalDateTime.now(),
            transactionId,
            transactionReference,
            accountId,
            userId,
            severity,
            riskScore,
            triggeredRules,
            description,
            action,
            autoBlocked,
            initiatorIp,
            ipCountryCode
        );
    }
    
    public static RecommendedAction resolveAction(Severity severity, boolean autoBlocked )
    {
    	 if (autoBlocked) return RecommendedAction.BLOCK_ACCOUNT_AND_CARD;
    	 
    	 return switch (severity) {
    	 	case  LOW      -> RecommendedAction.MONITOR;
	         case MEDIUM   -> RecommendedAction.REQUEST_STRONG_AUTH;
	         case HIGH     -> RecommendedAction.BLOCK_TRANSACTION;
	         case CRITICAL -> RecommendedAction.BLOCK_ACCOUNT;
    	 };
    }
    public boolean requiresImmediateAction() {
        return severity == Severity.HIGH || severity == Severity.CRITICAL;
    }
 
    public boolean requiresComplianceNotification() {
        return severity == Severity.MEDIUM
            || severity == Severity.HIGH
            || severity == Severity.CRITICAL;
    }
}
