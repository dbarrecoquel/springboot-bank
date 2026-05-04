package com.bank.common.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bank.common.dto.UserDTO;
import com.bank.domain.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // ===== FULL DTO =====
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    UserDTO toDto(User user);

    // ===== PROFILE =====
    UserDTO.Profile toProfile(User user);

    // ===== SUMMARY =====
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    UserDTO.Summary toSummary(User user);
}