package com.bank.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(

	    @NotBlank(message = "L'email est obligatoire")
	    @Email(message = "Format email invalide")
	    @Size(max = 150)
	    String email,
	 
	    @NotBlank(message = "Le mot de passe est obligatoire")
	    @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caractères")
	    String password,
	    /**
	     * Code OTP (One-Time Password) pour le second facteur d'authentification.
	     * Obligatoire si le compte a activé le 2FA (TOTP / SMS OTP).
	     * Nul si 2FA non activé.
	     */
	    @Pattern(
	        regexp = "^[0-9]{6}$",
	        message = "Le code OTP doit contenir exactement 6 chiffres"
	    )
	    String otpCode,
	 
	    /**
	     * Identifiant de l'appareil — permet de détecter une connexion
	     * depuis un nouveau device et de déclencher une vérification supplémentaire.
	     */
	    @Size(max = 255)
	    String deviceId,
	    /**
	     * Mémorise la session sur cet appareil (génère un refresh token longue durée).
	     */
	    boolean rememberMe
) {
	
    @Override
    public String toString() {
        return "LoginRequest[email=" + email
            + ", otpCode=" + (otpCode != null ? "***" : "null")
            + ", deviceId=" + deviceId
            + ", rememberMe=" + rememberMe
            + ", password=***]";
    }
}
