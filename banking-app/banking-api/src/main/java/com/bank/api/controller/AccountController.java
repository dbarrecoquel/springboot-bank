package com.bank.api.controller;

import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.TransactionDTO;
import com.bank.domain.entity.Account;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.security.JwtAuthFilter.BankingUserPrincipal;
import com.bank.service.api.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Comptes bancaires", description="Gestion des comptes — ouverture, consultation, blocage, clôture")
public class AccountController {

	private AccountService accountService;
	
	@GetMapping
	@PreAuthorize("hasAnyRole('TELLER','MANAGER','COMPLIANCE','ADMIN')")
	@Operation(summary = "Liste paginée de tous les comptes", description = "Réservé aux opérateurs. Supporte le filtrage par statut et type.")
	public ResponseEntity<ApiResponse<Page<AccountDTO.Summary>>> getAllAccounts(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(required = false)     AccountStatus status,
            @RequestParam(required = false)     AccountType   type){
		
		Page<AccountDTO.Summary> accounts = accountService.findAll(
	            status, type, PageRequest.of(page, size, Sort.by("createdAt").descending()));

		return ResponseEntity.ok(ApiResponse.ok(accounts.getTotalElements() + " comptes trouvés", accounts));
		
	}
	
	@GetMapping("/me")
	@PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','COMPLIANCE','ADMIN')")
	@Operation(summary = "Comptes du client connecté")
	public ResponseEntity<ApiResponse<List<AccountDTO.Summary>>> getMyAccounts(@AuthenticationPrincipal BankingUserPrincipal principal){
		
		List<AccountDTO.Summary> accounts = accountService.findByOwnerId(principal.userId());
		
		return ResponseEntity.ok(ApiResponse.ok(accounts.size() + " compte(s)", accounts));
		
	}
	
	@GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "Détail d'un compte")
	public ResponseEntity<ApiResponse<AccountDTO>> getMyAccount(@PathVariable UUID id, @AuthenticationPrincipal BankingUserPrincipal principal){
		
		AccountDTO account = accountService.findById(id,principal.userId(), principal.roles());
		
		return ResponseEntity.ok(ApiResponse.ok("Compte trouvé", account));
	}
	
	@PostMapping
	@PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Ouvrir un nouveau compte",
    description = "Un client peut ouvrir un compte pour lui-même. " +
                  "Un opérateur peut ouvrir un compte pour n'importe quel client.")
	public ResponseEntity<ApiResponse<AccountDTO>> openAccount(@Valid @RequestBody OpenAccountRequest request,
			@AuthenticationPrincipal BankingUserPrincipal principal){
		
		UUID ownerId = request.ownerId() != null ? request.ownerId() : principal.userId();
		
		AccountDTO account = accountService.openAccount(ownerId,request.type,request.currency, request.label, request.overdraftLimit);
		
        log.info("[ACCOUNT] Compte ouvert — ownerId={} type={} currency={}",
                ownerId, request.type(), request.currency());
        
        
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("Compte crée", account));
	}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Mettre à jour le libellé d'un compte")
    public ResponseEntity<ApiResponse<AccountDTO>> updateAccount(@PathVariable UUID id,
    		@Valid @RequestBody UpdateAccountRequest request,
    		@AuthenticationPrincipal BankingUserPrincipal principal){
    	
    	AccountDTO account = accountService.updateLabel(id, request.label, principal.userId(), principal.roles());
    	
    	return ResponseEntity.ok(ApiResponse.ok("Compte mis à jour", account));
    }
    @PutMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "Bloquer un compte",
               description = "Suspend toutes les opérations débitrices. Réversible.")
    public ResponseEntity<ApiResponse<AccountDTO>> blockAccount(@PathVariable UUID id,
    		@Valid @RequestBody BlockAccountRequest request,
    		@AuthenticationPrincipal BankingUserPrincipal principal){
    	
    	AccountDTO account = accountService.blockAccount(id, request.reason(), principal.userId());
    	
        log.warn("[ACCOUNT] Compte bloqué — id={} reason={} operator={}",
                id, request.reason(), principal.userId());

       return ResponseEntity.ok(ApiResponse.ok("Compte bloqué", account));

    	
    }
    @PutMapping("/{id}/unblock")
    @PreAuthorize("hasAnyRole('MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "DéBloquer un compte")
    public ResponseEntity<ApiResponse<AccountDTO>> unblockAccount(@PathVariable UUID id,
    		@Valid @RequestBody BlockAccountRequest request,
    		@AuthenticationPrincipal BankingUserPrincipal principal){
    	
    	AccountDTO account = accountService.unblockAccount(id, request.reason(), principal.userId());
    	
        log.warn("[ACCOUNT] Compte débloqué — id={} reason={} operator={}",
                id, request.reason(), principal.userId());

       return ResponseEntity.ok(ApiResponse.ok("Compte débloqué", account));

    	
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary="Clôturer un compte")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable UUID id,
    		@Valid @RequestBody CloseAccountRequest request,
    		@AuthenticationPrincipal BankingUserPrincipal principal) { 
    	
    	accountService.closeAccount(id,request.reason(),principal.userId());
    	log.info("[ACCOUNT] Compte clôturé — id={} operator={}", id, principal.userId());
    	
    	return ResponseEntity.ok(ApiResponse.ok("Compte cloturé"));
    }
    
    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER', 'MANAGER', 'COMPLIANCE', 'ADMIN')")
    @Operation(summary = "Sold disponible d'un compte")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(@PathVariable UUID id,
    		@AuthenticationPrincipal BankingUserPrincipal principal){
    	AccountDTO account = accountService.findById(id, principal.userId(), principal.roles());
    	
    	BalanceResponse balance = new BalanceResponse(
    			account.balance(),
    			account.availableBalance(),
    			account.overdraftLimit(),
    			account.currency()
    			);
    	
    	return ResponseEntity.ok(ApiResponse.ok("Solde récupéré", balance));
    }
    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "Relevé de compte paginé")
    public ResponseEntity<ApiResponse<Page<TransactionDTO.Summary>>> getTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Date de début ISO 8601")
            @RequestParam(required = false)    String from,
            @Parameter(description = "Date de fin ISO 8601")
            @RequestParam(required = false)    String to,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        Page<TransactionDTO.Summary> transactions = accountService.getTransactions(
            id, principal.userId(), principal.roles(),
            from, to, PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
 
        return ResponseEntity.ok(ApiResponse.ok(
            transactions.getTotalElements() + " transaction(s)", transactions));
    }

	public record OpenAccountRequest(
			@NotNull(message = "le type de compte est obligatoire")
			AccountType type,
			@NotNull(message = "la devise est obligatoire")
			CurrencyCode currency,
	        @Size(max = 100, message = "Le libellé ne doit pas dépasser 100 caractères")
	        String label,
	        @DecimalMin(value = "0.0", message = "La limite de découvert ne peut pas être négative")
	        BigDecimal overdraftLimit,
	        UUID ownerId
			
	) {}
	
	public record UpdateAccountRequest(
			@NotBlank(message = "le libéllé est obligatoire")
			@Size(max = 100, message = "Le libellé ne doit pas dépasser 100 caractères")
			String label) {}
    public record BlockAccountRequest(
            @NotBlank(message = "Le motif de blocage est obligatoire")
            @Size(max = 255)
            String reason
        ) {}
     
    public record UnblockAccountRequest(
        @NotBlank(message = "Le motif de déblocage est obligatoire")
        @Size(max = 255)
        String reason
    ) {}
 
    public record CloseAccountRequest(
        @NotBlank(message = "Le motif de clôture est obligatoire")
        @Size(max = 255)
        String reason
    ) {}
    public record BalanceResponse(
            BigDecimal   balance,
            BigDecimal   availableBalance,
            BigDecimal   overdraftLimit,
            CurrencyCode currency
        ) {}

}
