package com.bank.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class FraudDetectedException extends BankingException {

	private static final String ERROR_CODE = "FRAUD_DETECTED";
    private static final String CLIENT_MSG = "Opération refusée pour des raisons de sécurité. "
                                             + "Contactez votre conseiller si vous pensez à une erreur.";
 
    private final UUID transactionId;
    private final UUID accountId;
    private final BigDecimal riskScore;
    private final String triggeredRules;
    private final boolean accountBlocked;
    
    public FraudDetectedException(UUID transactionId, UUID accountId,
            BigDecimal riskScore, String triggeredRules,
            boolean accountBlocked) {

		super(CLIENT_MSG, ERROR_CODE, HttpStatus.FORBIDDEN, false);
		this.transactionId  = transactionId;
		this.accountId      = accountId;
		this.riskScore      = riskScore;
		this.triggeredRules = triggeredRules;
		this.accountBlocked = accountBlocked;
	}
    public static FraudDetectedException blockTransaction(UUID transactionId,
            UUID accountId,
            BigDecimal riskScore,
            String triggeredRules) {
    	
    	return new FraudDetectedException(transactionId, accountId,
    			riskScore, triggeredRules, false);
    }
    
    public static FraudDetectedException blockAccountAndTransaction(UUID transactionId,
            UUID accountId,
            BigDecimal riskScore,
            String triggeredRules) {
    	
    	return new FraudDetectedException(transactionId, accountId,
    			riskScore, triggeredRules, true);
    }
    
    public UUID getTransactionId() {
        return transactionId;
    }
 
    public UUID getAccountId() {
        return accountId;
    }
 
    public BigDecimal getRiskScore() {
        return riskScore;
    }
 
    public String getTriggeredRules() {
        return triggeredRules;
    }
 
    public boolean isAccountBlocked() {
        return accountBlocked;
    }
    
    public String getInternalDetail() {
        return String.format(
            "[FRAUDE] transactionId=%s, accountId=%s, riskScore=%s, rules=[%s], accountBlocked=%s",
            transactionId, accountId, riskScore, triggeredRules, accountBlocked
        );
    }
}
