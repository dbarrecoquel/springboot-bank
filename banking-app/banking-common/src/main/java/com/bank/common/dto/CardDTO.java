package com.bank.common.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bank.domain.enums.CurrencyCode;

public record CardDTO(
        UUID         id,
        String       panMasked,
        String       cardholderName,
        String       expiryDate,
        String       status,
        String       statusLabel,
        boolean      virtual,
        boolean      contactlessEnabled,
        boolean      onlinePaymentsEnabled,
        boolean      internationalPaymentsEnabled,
        boolean      pinBlocked,
        BigDecimal   dailyPaymentLimit,
        BigDecimal   dailyWithdrawalLimit,
        CurrencyCode currency,
        UUID         accountId,
        String       accountIban,
        java.time.LocalDateTime activatedAt,
        java.time.LocalDateTime createdAt
    ) {}

