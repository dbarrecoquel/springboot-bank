package com.bank.api.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.CardDTO;
import com.bank.domain.enums.CurrencyCode;
import com.bank.security.JwtAuthFilter.BankingUserPrincipal;
import com.bank.service.api.CardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cartes bancaires", description = "Émission, activation, blocage et paramétrage des cartes")
public class CardController {
	private final CardService cardService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Cartes du client connecté")
    public ResponseEntity<ApiResponse<List<CardDTO>>> getMyCards(
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        List<CardDTO> cards = cardService.findByOwner(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(cards.size() + " carte(s)", cards));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary="détail d'une carte")
    public ResponseEntity<ApiResponse<CardDTO>> getCard(@PathVariable UUID id,@AuthenticationPrincipal BankingUserPrincipal principal){
    	CardDTO card = cardService.findById(id, principal.userId(), principal.roles());
    	return ResponseEntity.ok(ApiResponse.ok("carte", card));
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')")
    @Operation(summary = "Émettre une nouvelle carte bancaire",
               description = "Crée une carte associée à un compte. " +
                             "La carte est créée en statut INACTIVE jusqu'à activation.")
    public ResponseEntity<ApiResponse<CardDTO>> issueCard(
            @Valid @RequestBody IssueCardRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        CardDTO card = cardService.issueCard(
            request.accountId(),
            principal.userId(),
            request.cardholderName(),
            request.virtual(),
            request.currency()
        );
 
        log.info("[CARD] Émise — accountId={} owner={}",
                 request.accountId(), principal.userId());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Carte émise avec succès", card));
    }
 
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')")
    @Operation(summary = "Activer une carte inactive",
               description = "Passe la carte de INACTIVE à ACTIVE. " +
                             "Un code de confirmation peut être requis.")
    public ResponseEntity<ApiResponse<CardDTO>> activateCard(
            @PathVariable UUID id,
            @Valid @RequestBody ActivateCardRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        CardDTO card = cardService.activate(id, principal.userId(),
                                             request.confirmationCode());
 
        log.info("[CARD] Activée — id={} owner={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Carte activée avec succès", card));
    }
 
    @PutMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "Bloquer une carte",
               description = "Un client peut bloquer sa propre carte (opposition). " +
                             "Un opérateur peut bloquer n'importe quelle carte.")
    public ResponseEntity<ApiResponse<CardDTO>> blockCard(
            @PathVariable UUID id,
            @Valid @RequestBody BlockCardRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        CardDTO card = cardService.block(id, principal.userId(),
                                          principal.roles(), request.reason());
 
        log.warn("[CARD] Bloquée — id={} reason={} operator={}",
                 id, request.reason(), principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Carte bloquée", card));
    }
    @PutMapping("/{id}/unblock")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Débloquer une carte",
               description = "Réservé aux opérateurs. " +
                             "Réinitialise également le compteur de tentatives PIN.")
    public ResponseEntity<ApiResponse<CardDTO>> unblockCard(
            @PathVariable UUID id,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        CardDTO card = cardService.unblock(id, principal.userId());
 
        log.info("[CARD] Débloquée — id={} operator={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Carte débloquée", card));
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')")
    @Operation(summary = "Annuler définitivement une carte",
               description = "La carte passe en statut CANCELLED. Opération irréversible.")
    public ResponseEntity<ApiResponse<Void>> cancelCard(
            @PathVariable UUID id,
            @Valid @RequestBody CancelCardRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        cardService.cancel(id, principal.userId(),
                           principal.roles(), request.reason());
 
        log.info("[CARD] Annulée — id={} reason={} owner={}",
                 id, request.reason(), principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Carte annulée"));
    }
    @PutMapping("/{id}/limits")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')")
    @Operation(summary = "Modifier les plafonds journaliers d'une carte",
               description = "Un client peut modifier ses plafonds dans les limites autorisées. " +
                             "Un manager peut dépasser ces limites.")
    public ResponseEntity<ApiResponse<CardDTO>> updateLimits(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLimitsRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        CardDTO card = cardService.updateLimits(
            id, principal.userId(), principal.roles(),
            request.dailyPaymentLimit(),
            request.dailyWithdrawalLimit()
        );
 
        log.info("[CARD] Plafonds mis à jour — id={} paymentLimit={} withdrawalLimit={}",
                 id, request.dailyPaymentLimit(), request.dailyWithdrawalLimit());
        return ResponseEntity.ok(ApiResponse.ok("Plafonds mis à jour", card));
    }
    @PutMapping("/{id}/settings")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')")
    @Operation(summary = "Configurer les options de la carte",
               description = "Active ou désactive : sans contact, paiements en ligne, " +
                             "paiements internationaux.")
    public ResponseEntity<ApiResponse<CardDTO>> updateSettings(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSettingsRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        CardDTO card = cardService.updateSettings(
            id, principal.userId(), principal.roles(),
            request.contactlessEnabled(),
            request.onlinePaymentsEnabled(),
            request.internationalPaymentsEnabled()
        );
 
        log.info("[CARD] Paramètres mis à jour — id={} contactless={} online={} intl={}",
                 id, request.contactlessEnabled(),
                 request.onlinePaymentsEnabled(),
                 request.internationalPaymentsEnabled());
        return ResponseEntity.ok(ApiResponse.ok("Paramètres mis à jour", card));
    }
 
    @PostMapping("/{id}/pin/reset")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')")
    @Operation(summary = "Réinitialiser le PIN d'une carte bloquée",
               description = "Envoie un code OTP par SMS pour confirmer la réinitialisation.")
    public ResponseEntity<ApiResponse<Void>> resetPin(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPinRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        cardService.resetPin(id, principal.userId(),
                              principal.roles(), request.otpCode());
 
        log.info("[CARD] PIN réinitialisé — id={} owner={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("PIN réinitialisé avec succès"));
    }
 



    public record IssueCardRequest(
    		 
            @NotNull(message = "L'identifiant du compte est obligatoire")
            UUID accountId,
     
            @NotBlank(message = "Le nom du porteur est obligatoire")
            @Size(max = 26, message = "Le nom du porteur ne doit pas dépasser 26 caractères")
            String cardholderName,
     
            @NotNull(message = "La devise est obligatoire")
            CurrencyCode currency,
     
            boolean virtual
        ) {}
     
    public record ActivateCardRequest(
        @Size(min = 4, max = 6, message = "Le code de confirmation doit contenir 4 à 6 caractères")
        String confirmationCode
    ) {}
 
    public record BlockCardRequest(
        @NotBlank(message = "Le motif de blocage est obligatoire")
        @Size(max = 255)
        String reason
    ) {}
 
    public record CancelCardRequest(
        @NotBlank(message = "Le motif d'annulation est obligatoire")
        @Size(max = 255)
        String reason
    ) {}
 
    public record UpdateLimitsRequest(
 
        @NotNull(message = "Le plafond de paiement journalier est obligatoire")
        @DecimalMin(value = "0.0", message = "Le plafond de paiement ne peut pas être négatif")
        BigDecimal dailyPaymentLimit,
 
        @NotNull(message = "Le plafond de retrait journalier est obligatoire")
        @DecimalMin(value = "0.0", message = "Le plafond de retrait ne peut pas être négatif")
        BigDecimal dailyWithdrawalLimit
    ) {}
 
    public record UpdateSettingsRequest(
        boolean contactlessEnabled,
        boolean onlinePaymentsEnabled,
        boolean internationalPaymentsEnabled
    ) {}
 
    public record ResetPinRequest(
        @Pattern(regexp = "^[0-9]{6}$",
                 message = "Le code OTP doit contenir exactement 6 chiffres")
        String otpCode
    ) {}

}