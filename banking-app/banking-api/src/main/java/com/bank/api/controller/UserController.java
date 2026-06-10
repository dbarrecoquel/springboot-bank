package com.bank.api.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.UserDTO;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.UserRole;
import com.bank.security.JwtAuthFilter.BankingUserPrincipal;
import com.bank.service.api.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description="Gestion des utilisateurs")
public class UserController {
	
	private final UserService userService;

	@GetMapping
	@PreAuthorize("hasAnyRole('TELLER','MANAGER','COMPLIANCE','ADMIN')")
	@Operation(summary = "Liste paginée de tous les utilisateurs", description = "Réservé aux opérateurs")
	public ResponseEntity<ApiResponse<Page<UserDTO.Summary>>> getAllUsers(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size){
		
		Page<UserDTO.Summary> users = userService.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));

		return ResponseEntity.ok(ApiResponse.ok(users.getTotalElements() + " comptes trouvés", users));
		
	}
	
	@PostMapping("/register")
	@Operation(summary = "Inscription d'un nouveau compte", description = "Créé un compte utilisateur")
	public ResponseEntity<ApiResponse<UserDTO.Profile>> register(@Valid @RequestBody RegisterRequest request){
		UserDTO.Profile profile = userService.register(request.firstName(), 
				request.lastName(), request.dateOfBirth(), request.email(), request.password(), request.phoneNumber());
		
        log.info("[USER] Inscription — email={}", request.email());
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Compte créé avec succès. " +
                "Veuillez vérifier votre email.", profile));

	}
	
	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Obtenir mon profil")
	public ResponseEntity<ApiResponse<UserDTO.Profile>> getMyProfil(@AuthenticationPrincipal BankingUserPrincipal principal){
		
		UserDTO.Profile profile = userService.getProfile(principal.userId());
		
		return ResponseEntity.ok(ApiResponse.ok("Profil récupéré", profile));
		
	}
	
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Modifier les informations d'un utilisateur")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        UserDTO user = userService.updateUser(
            id, request.firstName(), request.lastName(),
            request.addressLine1(), request.addressLine2(),
            request.city(), request.postalCode(), request.countryCode(),
            principal.userId()
        );
 
        log.info("[USER] Utilisateur modifié — id={} operator={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Utilisateur mis à jour", user));
    }
    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer un compte utilisateur")
    public ResponseEntity<ApiResponse<Void>> enableUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        userService.setEnabled(id, true, principal.userId());
 
        log.info("[USER] Activé — id={} operator={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Compte activé"));
    }
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un compte utilisateur")
    public ResponseEntity<ApiResponse<Void>> disableUser(
            @PathVariable UUID id,
            @Valid @RequestBody DisableUserRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        userService.setEnabled(id, false, principal.userId());
 
        log.warn("[USER] Désactivé — id={} reason={} operator={}",
                 id, request.reason(), principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Compte désactivé"));
    }

    @PutMapping("/{id}/kyc")
    @PreAuthorize("hasAnyRole('COMPLIANCE','MANAGER','ADMIN')")
    @Operation(summary = "Valider le KYC d'un utilisateur",
               description = "Confirme l'identité du client après vérification des pièces. " +
                             "Obligatoire avant l'ouverture d'un compte.")
    public ResponseEntity<ApiResponse<Void>> validateKyc(
            @PathVariable UUID id,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        userService.validateKyc(id, principal.userId());
 
        log.info("[USER] KYC validé — id={} operator={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("KYC validé avec succès"));
    }
 
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter un rôle à un utilisateur")
    public ResponseEntity<ApiResponse<Void>> addRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        userService.addRole(id, request.role(), principal.userId());
 
        log.info("[USER] Rôle ajouté — id={} role={} operator={}",
                 id, request.role(), principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(
            "Rôle " + request.role() + " ajouté"));
    }
    @DeleteMapping("/{id}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retirer un rôle à un utilisateur")
    public ResponseEntity<ApiResponse<Void>> removeRole(
            @PathVariable UUID     id,
            @PathVariable UserRole role,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        // Interdire de retirer le rôle CUSTOMER (rôle minimum obligatoire)
        if (role == UserRole.CUSTOMER) {
            return ResponseEntity.badRequest().body(
                ApiResponse.badRequest("Le rôle CUSTOMER ne peut pas être retiré", null));
        }
 
        userService.removeRole(id, role, principal.userId());
 
        log.info("[USER] Rôle retiré — id={} role={} operator={}",
                 id, role, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Rôle " + role + " retiré"));
    }
    @GetMapping("/pending-kyc")
    @PreAuthorize("hasAnyRole('COMPLIANCE','MANAGER','ADMIN')")
    @Operation(summary = "Utilisateurs en attente de validation KYC",
               description = "File de traitement compliance — triée par date d'inscription.")
    public ResponseEntity<ApiResponse<Page<UserDTO.Summary>>> getPendingKyc(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
 
        Page<UserDTO.Summary> pending = userService.findPendingKyc(
            PageRequest.of(page, size, Sort.by("createdAt").ascending()));
 
        return ResponseEntity.ok(ApiResponse.ok(
            pending.getTotalElements() + " utilisateur(s) en attente de KYC", pending));
    }

    public record RegisterRequest(
    		 
            @NotBlank(message = "Le prénom est obligatoire")
            @Size(max = 80)
            String firstName,
     
            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 80)
            String lastName,
     
            @Past(message = "La date de naissance doit être dans le passé")
            LocalDate dateOfBirth,
     
            @NotBlank(message = "L'email est obligatoire")
            @Email(message = "Format email invalide")
            @Size(max = 150)
            String email,
     
            @NotBlank(message = "Le mot de passe est obligatoire")
            @Size(min = 8, max = 128,
                  message = "Le mot de passe doit contenir entre 8 et 128 caractères")
            String password,
     
            @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$",
                     message = "Numéro de téléphone invalide (format E.164)")
            String phoneNumber
        ) {}
    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80)  String firstName,
            @NotBlank @Size(max = 80)  String lastName,
            @Size(max = 150)           String addressLine1,
            @Size(max = 150)           String addressLine2,
            @Size(max = 80)            String city,
            @Size(max = 20)            String postalCode,
            @Size(min = 2, max = 3)    String countryCode
        ) {}
    public record UpdatePhoneRequest(
            @NotBlank(message = "Le numéro de téléphone est obligatoire")
            @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$",
                     message = "Numéro de téléphone invalide (format E.164)")
            String phoneNumber,
     
            @Pattern(regexp = "^[0-9]{6}$",
                     message = "Le code OTP doit contenir exactement 6 chiffres")
            String otpCode
        ) {}
     
    public record UpdateUserRequest(
        @NotBlank @Size(max = 80)  String firstName,
        @NotBlank @Size(max = 80)  String lastName,
        @Size(max = 150)           String addressLine1,
        @Size(max = 150)           String addressLine2,
        @Size(max = 80)            String city,
        @Size(max = 20)            String postalCode,
        @Size(min = 2, max = 3)    String countryCode
    ) {}
 
    public record DisableUserRequest(
        @NotBlank(message = "Le motif de désactivation est obligatoire")
        @Size(max = 255)
        String reason
    ) {}
 
    public record RoleRequest(
        @NotNull(message = "Le rôle est obligatoire")
        UserRole role
    ) {}


}
