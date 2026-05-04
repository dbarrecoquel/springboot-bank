package com.bank.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDTO(
	UUID id,
	String reference,
	TransactionType type,
	String typeLabel,
	TransactionStatus status,
	String statusLabel,
	BigDecimal amount,
	CurrencyCode currency,
	BigDecimal amountEur,
	BigDecimal exchangeRate,
	BigDecimal fees,
	BigDecimal totalAmount,
	UUID accountId,
	String counterpartIban,
	String counterpartName,
	String counterPartBic,
	String label,
	String rejectionReason,
	String endToEndId,
	String mandateId,
	BigDecimal fraudScore,
	
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime createdAt,
 
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime updatedAt,
 
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime settledAt
) {
	
	public record Summary(
			
		UUID id,
		String reference,
		TransactionType type,
		TransactionStatus status,
		BigDecimal amount,
		CurrencyCode currency,
		String counterpartName,
		String label,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
	)
	{}
}
