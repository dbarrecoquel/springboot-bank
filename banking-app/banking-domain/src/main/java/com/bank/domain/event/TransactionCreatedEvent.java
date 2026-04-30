package com.bank.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionType;

public record TransactionCreatedEvent (
		UUID eventId,
		LocalDateTime occurredAt,
		UUID transactionId,
		String reference,
		TransactionType type,
		BigDecimal amount,
		CurrencyCode currency,
		UUID accountId,
		String counterpartIban,
		String counterpartName,
		UUID userId,
		String initiatorIp,
		UUID cardId
		
){
	//validation à la construction :
	
	public TransactionCreatedEvent {
        if (eventId       == null) throw new IllegalArgumentException("eventId est obligatoire");
        if (occurredAt    == null) throw new IllegalArgumentException("occurredAt est obligatoire");
        if (transactionId == null) throw new IllegalArgumentException("transactionId est obligatoire");
        if (reference     == null || reference.isBlank()) throw new IllegalArgumentException("reference est obligatoire");
        if (type          == null) throw new IllegalArgumentException("type est obligatoire");
        if (amount        == null || amount.signum() <= 0) throw new IllegalArgumentException("amount doit être positif");
        if (currency      == null) throw new IllegalArgumentException("currency est obligatoire");
        if (accountId     == null) throw new IllegalArgumentException("accountId est obligatoire");
        if (userId        == null) throw new IllegalArgumentException("userId est obligatoire");
	}
	
	public static  TransactionCreatedEvent of(UUID transactionId, String reference, TransactionType type,
		    BigDecimal amount, CurrencyCode currency, UUID accountId,
		    String counterpartIban, String counterpartName, UUID userId,
		    String initiatorIp, UUID cardId) {

		return new TransactionCreatedEvent(
		    UUID.randomUUID(),
		    LocalDateTime.now(),
		    transactionId,
		    reference,
		    type,
		    amount,
		    currency,
		    accountId,
		    counterpartIban,
		    counterpartName,
		    userId,
		    initiatorIp,
		    cardId
		);
		
	}
    /**
     * Indique si cet événement doit déclencher une analyse anti-fraude.
     */
	public boolean requiresFraudAnalysis() {
		return type.isAmlControlRequired() || amount.compareTo(new BigDecimal("10000")) >= 0;
	}
    /**
     * Indique si cet événement concerne une opération transfrontalière.
     */
	public boolean isCrossBorder() {
		return type.isCrossBorder();
	}
}
