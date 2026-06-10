package com.bank.common.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bank.common.dto.CardDTO;
import com.bank.domain.entity.Card;

@Mapper(componentModel = "spring")
public interface CardMapper {

    @Mapping(target = "status", source = "status")
    @Mapping(target = "statusLabel", expression = "java(card.getStatus().getLabel())")
    @Mapping(target = "expiryDate", expression = "java(card.getExpiryDate() != null ? card.getExpiryDate().toString() : null)")
    @Mapping(target = "accountId", expression = "java(card.getAccount() != null ? card.getAccount().getId() : null)")
    @Mapping(target = "accountIban", expression = "java(card.getAccount() != null ? card.getAccount().getIban() : null)")
    CardDTO toDto(Card card);
}