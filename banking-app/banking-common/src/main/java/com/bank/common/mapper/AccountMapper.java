package com.bank.common.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.bank.common.dto.AccountDTO;
import com.bank.domain.entity.Account;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "availableBalance", expression = "java(account.availableBalance())")
    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerFullName", expression = "java(account.getOwner().getFirstName() + \" \" + account.getOwner().getLastName())")
    @Mapping(target = "typeLabel", expression = "java(account.getType().name())")
    AccountDTO toDto(Account account);
    @Mapping(target = "label", source = "label")
    AccountDTO.Summary toSummary(Account account);
}
