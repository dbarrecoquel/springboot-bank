package com.bank.common.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class UnauthorizedOperationException extends BankingException {
	
    private static final String ERROR_CODE = "UNAUTHORIZED_OPERATION";
    
    private final String operation;
    private final UUID subjectId;
    private final String subjectType;
    private final UUID requesterId;
    
    public UnauthorizedOperationException(String message) {
    	
        super(message, ERROR_CODE, HttpStatus.FORBIDDEN);
        this.operation   = null;
        this.subjectId   = null;
        this.subjectType = null;
        this.requesterId = null;
    }
    private UnauthorizedOperationException(String message, String operation,
            String subjectType, UUID subjectId,
            UUID requesterId) {
    	
		super(message, ERROR_CODE, HttpStatus.FORBIDDEN);
		this.operation   = operation;
		this.subjectId   = subjectId;
		this.subjectType = subjectType;
		this.requesterId = requesterId;
	}
    public static UnauthorizedOperationException accessDenied(
            String operation, String subjectType, UUID subjectId, UUID requesterId) {
    	
        String msg = String.format(
            "Accès refusé — l'utilisateur %s n'est pas autorisé à effectuer '%s' sur %s[%s]",
            requesterId, operation, subjectType, subjectId
        );
        return new UnauthorizedOperationException(msg, operation, subjectType, subjectId, requesterId);
    }

    public static UnauthorizedOperationException invalidStatus(
            String operation, String subjectType, UUID subjectId, String currentStatus) {
        
    	String msg = String.format(
            "Opération '%s' impossible sur %s[%s] — statut actuel : %s",
            operation, subjectType, subjectId, currentStatus
        );
        return new UnauthorizedOperationException(msg, operation, subjectType, subjectId, null);
    }
    
    public static UnauthorizedOperationException invalidTransition(
            String subjectType, UUID subjectId, String fromStatus, String toStatus) {
        
    	String msg = String.format(
            "Transition interdite sur %s[%s] : %s → %s",
            subjectType, subjectId, fromStatus, toStatus
        );
        return new UnauthorizedOperationException(msg, "STATUS_TRANSITION",
                                                  subjectType, subjectId, null);
    }
    
    public static UnauthorizedOperationException kycNotVerified(UUID userId, String operation) {
       
    	String msg = String.format(
            "Opération '%s' refusée — vérification KYC requise pour l'utilisateur %s",
            operation, userId
        );
        return new UnauthorizedOperationException(msg, operation, "User", userId, userId);
    }
    
    public static UnauthorizedOperationException dailyLimitReached(UUID accountId,
            String limitType,
            String limit,
            String currency) {
		
    	String msg = String.format(
		"Plafond journalier atteint sur le compte %s — limite %s : %s %s",
		accountId, limitType, limit, currency
		);
		return new UnauthorizedOperationException(msg, "DEBIT", "Account", accountId, null);
    }
    public String getOperation() {
        return operation;
    }
 
    public UUID getSubjectId() {
        return subjectId;
    }
 
    public String getSubjectType() {
        return subjectType;
    }
 
    public UUID getRequesterId() {
        return requesterId;
    }
}
