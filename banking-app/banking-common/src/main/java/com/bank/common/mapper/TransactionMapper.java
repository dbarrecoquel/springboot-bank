package com.bank.common.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.bank.common.dto.TransactionDTO;
import com.bank.domain.entity.Transaction;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "typeLabel", source = "type", qualifiedByName = "enumToLabel")
    @Mapping(target = "statusLabel", source = "status", qualifiedByName = "enumToLabel")
    @Mapping(target = "amountEur", source = "amountEur")
    @Mapping(target = "totalAmount", expression = "java(transaction.totalAmount())")
    TransactionDTO toDto(Transaction transaction);

    // ===== SUMMARY =====
    @Mapping(target = "counterpartName", source = "counterpartName")
    TransactionDTO.Summary toSummary(Transaction transaction);

    // ===== ENUM → LABEL =====
    @Named("enumToLabel")
    default String enumToLabel(Enum<?> e) {
        return e != null ? e.name() : null;
    }
}