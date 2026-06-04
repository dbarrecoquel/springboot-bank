package com.bank.api.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.UserDTO;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.service.api.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

}
