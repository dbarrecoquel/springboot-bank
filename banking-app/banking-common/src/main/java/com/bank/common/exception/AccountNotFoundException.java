package com.bank.common.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BankingException{
	
	private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";
	 
    private final String searchCriteria;
    private final String searchValue;
    
    private AccountNotFoundException(String searchCriteria,
            String searchValue,
            String message) {
    	
		super(message, ERROR_CODE, HttpStatus.NOT_FOUND);
		this.searchCriteria = searchCriteria;
		this.searchValue    = searchValue;
	}
    
    public static AccountNotFoundException byId(UUID accountId) {
        return new AccountNotFoundException(
            "id",
            accountId.toString(),
            "Compte introuvable — id : " + accountId
        );
    }
    
    public static AccountNotFoundException byIban(String iban) {
    	
        String masked = iban != null && iban.length() > 6
            ? iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4)
            : "****";
        return new AccountNotFoundException(
            "iban",
            masked,
            "Compte introuvable — IBAN : " + masked
        );
    }
    
    public static AccountNotFoundException byAccountNumber(String accountNumber) {
        return new AccountNotFoundException(
            "accountNumber",
            accountNumber,
            "Compte introuvable — numéro : " + accountNumber
        );
    }
    
    public static AccountNotFoundException noActiveAccountForUser(UUID userId) {
        return new AccountNotFoundException(
            "userId",
            userId.toString(),
            "Aucun compte actif trouvé pour l'utilisateur : " + userId
        );
    }
    
    public String getSearchCriteria() {
        return searchCriteria;
    }
 
    public String getSearchValue() {
        return searchValue;
    }
}
