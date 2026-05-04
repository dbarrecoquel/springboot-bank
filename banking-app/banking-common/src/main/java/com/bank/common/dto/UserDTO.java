package com.bank.common.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;

import com.bank.domain.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDTO(
		
		UUID id,
		String firstName,
		String lastName,
		String fullName,
	    @JsonFormat(pattern = "yyyy-MM-dd")
	    LocalDate dateOfBirth,
	    String nationality,
	    String email,
	    String phoneNumber,
	    String addressLine1,
	    String addressLine2,
	    String city,
	    String postalCode,
	    String countryCode,
	    boolean enabled,
	    boolean emailVerified,
	    boolean phoneVerified,
	    boolean kycVerified,
	    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime kycVerifiedAt,
	    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime lastLoginAt,
	    Set<UserRole> roles,
	    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime createdAt,
	    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime updatedAt
		
) {
	public record Profile(
			UUID id,
			String firstName,
			String lastName,
			String email,
			String phoneNumber,
			boolean emailVerified,
			boolean phoneVerified,
			boolean kycVerified
	) {
		
	}
	
	public record Summary(
			
			UUID id,
			String fullName,
			String email,
			String phoneNumber,
			boolean enabled,
			boolean kycVerified,
			Set<UserRole> roles,
	        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	        LocalDateTime createdAt
	){
		
	}
}
