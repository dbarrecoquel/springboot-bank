package com.bank.domain.enums;

public enum AccountStatus {
	PENDING_VALIDATION("En attente de validation"),
	ACTIVE("Actif"),
	BLOCKED("Bloqué"),
	CLOSED("Clôturé");
	
	private final String label;
	
    AccountStatus(String label) {
        this.label = label;
    }
    
    public String getLabel() {
    	return this.label;
    }
    
    public boolean allowsOperations() {
    	return this == ACTIVE;
    }
    
    public boolean isTerminal() {
    	return this == BLOCKED;
    }
    
    public boolean canTransitionTo(AccountStatus target) {
    	if (this == CLOSED)
    		return false;
    	
    	if (this == target)
    		return false;
    
    	return switch (this) {
    		case PENDING_VALIDATION -> target == ACTIVE || target == CLOSED;
    		case ACTIVE -> target == BLOCKED || target == CLOSED;
    		case BLOCKED -> target == ACTIVE || target == CLOSED;
    		default -> false;
    	};
    }
}
