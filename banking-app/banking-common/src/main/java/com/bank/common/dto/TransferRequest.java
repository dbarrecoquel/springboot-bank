package com.bank.common.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bank.domain.enums.CurrencyCode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TransferRequest(
		
		@NotNull(message = "l'identifiant du compte est obligatoire")
		UUID sourceAccountId,
	    @Pattern(
	            regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$",
	            message = "Format IBAN invalide"
	    )
	    String destinationIban,
	    UUID destinationAccountId,
	    
	    @NotBlank(message = "le nom du beneficiaire est obligatoire")
		@Size(max= 100, message= "le nom du beneficiaire ne peut pas dépasser 100 caractères")
		String beneficiaireName,
	    /**
	     * BIC/SWIFT de la banque du bénéficiaire.
	     * Obligatoire pour les virements SWIFT internationaux.
	     */
	    @Pattern(
	        regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$",
	        message = "Format BIC/SWIFT invalide (8 ou 11 caractères)"
	    )
	    String beneficiaryBic,
	    
	    @NotNull(message = "Le montant est obligatoire")
	    @DecimalMin(value = "0.01", message = "Le montant minimum est de 0.01")
	    @DecimalMax(value = "999999999.99", message = "Le montant dépasse le plafond autorisé")
	    @Digits(integer = 9, fraction = 2, message = "Format de montant invalide")
	    BigDecimal amount,
	    
	    @NotNull(message = "la devise est obligatoire")
		CurrencyCode currency,
		
		@NotBlank(message = "le motif du virement est obligatoire")
		@Size(max = 140, message = "Le motif ne doit pas depasser 140 caractères (norme SEPA)")
		String label,
		
		@Size(max = 35, message = "l'end to end id ne doit pas depasser 35 caractères")
		String endToEndId,
	    // ── Options ───────────────────────────────────────────────
		 
	    /**
	     * Virement instantané SCT Inst ({@code true}) ou standard J+1 ({@code false}).
	     * Ignoré si la banque destinataire ne supporte pas l'instantané.
	     */
	    boolean instant,
	    /**
	     * Code PIN ou second facteur d'authentification pour les montants élevés.
	     * Requis si le montant dépasse le seuil configuré dans {@code TransactionLimitConfig}.
	     */
	    @Size(min = 4, max = 6, message = "Le code de confirmation doit contenir entre 4 et 6 caractères")
	    String confirmationCode
) {
	
    public TransferRequest {
        boolean hasIban     = destinationIban      != null && !destinationIban.isBlank();
        boolean hasInternal = destinationAccountId != null;
        if (!hasIban && !hasInternal) {
            throw new IllegalArgumentException(
                "destinationIban ou destinationAccountId est obligatoire"
            );
        }
    }
}
