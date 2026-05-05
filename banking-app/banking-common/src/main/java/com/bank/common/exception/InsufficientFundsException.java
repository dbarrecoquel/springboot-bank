package com.bank.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BankingException {
	
    private static final String ERROR_CODE = "INSUFFICIENT_FUNDS";
    
    private final UUID accountId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;
    private final String currency;
    
    public InsufficientFundsException(UUID accountId,
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            String currency) {
		super(
		buildMessage(accountId, requestedAmount, availableBalance, currency),
		ERROR_CODE,
		HttpStatus.UNPROCESSABLE_ENTITY
		);
		this.accountId        = accountId;
		this.requestedAmount  = requestedAmount;
		this.availableBalance = availableBalance;
		this.currency         = currency;
	}
    public InsufficientFundsException(String message) {
        super(message, ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
        this.accountId        = null;
        this.requestedAmount  = null;
        this.availableBalance = null;
        this.currency         = null;
    }
    
    public static InsufficientFundsException of(UUID accountId,
            BigDecimal requested,
            BigDecimal available,
            String currency) {
    
    	return new InsufficientFundsException(accountId, requested, available, currency);
    }
    
    public UUID getAccountId() {
        return accountId;
    }
 
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
 
    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
 
    public String getCurrency() {
        return currency;
    }
 
    public BigDecimal getMissingAmount() {
        if (requestedAmount == null || availableBalance == null) return null;
        return requestedAmount.subtract(availableBalance);
    }
    private static String buildMessage(UUID accountId,
            BigDecimal requested,
            BigDecimal available,
            String currency) {
    	
		return String.format(
		"Fonds insuffisants sur le compte %s — demandé : %s %s, disponible : %s %s",
		accountId, requested, currency, available, currency
		);
    }
}
