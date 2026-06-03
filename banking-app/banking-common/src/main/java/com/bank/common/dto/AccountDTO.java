package com.bank.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountDTO(
		UUID id,
		String iban,
		String accountNumber,
		AccountType type,
		String typeLabel,
		AccountStatus status,
		CurrencyCode currency,
		BigDecimal balance,
		BigDecimal availableBalance,
		BigDecimal overdraftLimit,
		BigDecimal interestRate,
		String label,
		UUID ownerId,
		String ownerFullName,
		
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime createdAt,
		
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime updatedAt,
		
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime closedAt
		
) {
	
    

	/**
     * Vue résumée pour les listes (moins de champs).
     */
    public record Summary(
        UUID          id,
        String        iban,
        AccountType   type,
        AccountStatus status,
        CurrencyCode  currency,
        BigDecimal    balance,
        String        label
    ) {}

}
