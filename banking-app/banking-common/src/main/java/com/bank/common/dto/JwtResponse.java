package com.bank.common.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;

import com.bank.domain.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JwtResponse(
	 
		String accesToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
	    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime accessTokenExpiresAt,
	    
	    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
	    LocalDateTime refreshTokenExpiresAt,
	    
	    UUID userId,
	    String email,
	    String fullName,
	    Set<UserRole> roles,
	    /**
	     * Indique si l'utilisateur doit compléter la configuration du 2FA
	     * (premier login ou réinitialisation).
	     */
	    boolean mfaSetupRequired,
	    boolean passwordChangeRequired
	) {
	
    public static JwtResponse of(
            String accessToken, String refreshToken,
            long expiresIn, LocalDateTime accessTokenExpiresAt,
            LocalDateTime refreshTokenExpiresAt,
            UUID userId, String email, String fullName, Set<UserRole> roles) {
 
        return new JwtResponse(
            accessToken, refreshToken, "Bearer",
            expiresIn, accessTokenExpiresAt, refreshTokenExpiresAt,
            userId, email, fullName, roles,
            false, false
        );
    }
    public static JwtResponse withActions(
            String accessToken, String refreshToken,
            long expiresIn, LocalDateTime accessTokenExpiresAt,
            LocalDateTime refreshTokenExpiresAt,
            UUID userId, String email, String fullName, Set<UserRole> roles,
            boolean mfaSetupRequired, boolean passwordChangeRequired) {
 
        return new JwtResponse(
            accessToken, refreshToken, "Bearer",
            expiresIn, accessTokenExpiresAt, refreshTokenExpiresAt,
            userId, email, fullName, roles,
            mfaSetupRequired, passwordChangeRequired
        );
    }
    
    @Override
    public String toString() {
        return "JwtResponse[userId=" + userId
            + ", email=" + email
            + ", roles=" + roles
            + ", expiresIn=" + expiresIn
            + ", accessToken=***, refreshToken=***]";
    }
}
