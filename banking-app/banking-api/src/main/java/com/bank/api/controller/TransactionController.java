package com.bank.api.controller;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.TransactionDTO;
import com.bank.domain.entity.Transaction;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.security.JwtAuthFilter.BankingUserPrincipal;
import com.bank.service.api.TransactionService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Opérations bancaires — virements, paiements, dépôts, retraits")
public class TransactionController {

	private final TransactionService transactionService;
	
	@GetMapping("/api/v1/transactions/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "Détail d'une transaction")
    public ResponseEntity<ApiResponse<TransactionDTO>> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        TransactionDTO tx = transactionService.findById(id, principal.userId(),
                                                         principal.roles());
        return ResponseEntity.ok(ApiResponse.ok("Transaction trouvée", tx));
    }
 
	// ─────────────────────────────────────────────────────────
    //  GET /api/v1/transactions — liste paginée (opérateurs)
    // ─────────────────────────────────────────────────────────
 
    @GetMapping("/api/v1/transactions")
    @PreAuthorize("hasAnyRole('TELLER','MANAGER','COMPLIANCE','ADMIN')")
    @Operation(summary = "Liste paginée des transactions",
               description = "Filtrage par statut, type et période.")
    public ResponseEntity<ApiResponse<Page<TransactionDTO.Summary>>> getAllTransactions(
            @RequestParam(defaultValue = "0")   int               page,
            @RequestParam(defaultValue = "20")  int               size,
            @RequestParam(required = false)     TransactionStatus status,
            @RequestParam(required = false)     TransactionType   type,
            @RequestParam(required = false)     String            from,
            @RequestParam(required = false)     String            to) {
 
        Page<TransactionDTO.Summary> transactions = transactionService.findAll(
            status, type, from, to,
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
 
        return ResponseEntity.ok(ApiResponse.ok(
            transactions.getTotalElements() + " transaction(s)", transactions));
    }
    //  POST /api/v1/transfers/sepa — virement SEPA
    // ─────────────────────────────────────────────────────────
 
    @PostMapping("/api/v1/transfers/sepa")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Initier un virement SEPA",
               description = "Virement SEPA standard (J+1) ou instantané (SCT Inst).")
    public ResponseEntity<ApiResponse<TransactionDTO>> initiateSepaTransfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal,
            HttpServletRequest httpRequest) {
 
        Transaction tx = transactionService.initiateSepaTransfer(
            request.sourceAccountId(),
            principal.userId(),
            request.destinationIban(),
            request.beneficiaryName(),
            request.amount(),
            request.currency(),
            request.label(),
            request.endToEndId(),
            request.instant()
        );
 
        log.info("[TX] SEPA initié — ref={} from={} amount={} {}",
                 tx.getReference(), request.sourceAccountId(),
                 request.amount(), request.currency());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Virement SEPA initié", toDto(tx)));

    
    	
    }
    @PostMapping("/api/v1/transfers/internal")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Initier un virement interne",
               description = "Réglement immédiat.")
    public ResponseEntity<ApiResponse<TransactionDTO>> initiateInternalTransfer(
            @Valid @RequestBody InternalTransferRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal,
            HttpServletRequest httpRequest) {
 
        Transaction tx = transactionService.initiateInternalTransfer(
            request.sourceAccountId(),
            request.destinationAccountId(),
            principal.userId(),
            request.amount(),
            request.currency(),
            request.label()
        );
 
        log.info("[TX] Vivrement interne initié — ref={} from={} amount={} {}",
                 tx.getReference(), request.sourceAccountId(),
                 request.amount(), request.currency());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Virement Interne initié", toDto(tx)));

    
    	
    }
    @PostMapping("/api/v1/transfers/swift")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Initier un virement international swift",
               description = "Virement hors zone SEPA")
    public ResponseEntity<ApiResponse<TransactionDTO>> initiateSwiftTransfer(
            @Valid @RequestBody SwiftTransferRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal,
            HttpServletRequest httpRequest) {
 
        Transaction tx = transactionService.initiateInternationalTransfer(
            request.sourceAccountId(),
            principal.userId(),
            request.destinationIban(),
            request.beneficiaryName(),
            request.beneficiaryBic(),
            request.amount(),
            request.currency(),
            request.label()
        );
        log.info("[TX] Vivrement international initié — ref={} from={} amount={} {}",
                 tx.getReference(), request.sourceAccountId(),
                 request.amount(), request.currency());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Virement International initié", toDto(tx)));

    
    	
    }
    @PostMapping("/api/v1/transfers/swift")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Initier un virement international swift",
               description = "Virement hors zone SEPA")
    public ResponseEntity<ApiResponse<TransactionDTO>> cardPayment(
            @Valid @RequestBody CardPaymentRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal,
            HttpServletRequest httpRequest) {
 
        Transaction tx = transactionService.cardPayment(
            request.accountId(),
            request.cardId(),
            principal.userId(),
            request.amount(),
            request.currency(),
            request.merchantName(),
            request.label()
        );
        log.info("[TX] card payment initié — ref={} from={} amount={} {}",
                 tx.getReference(), request.accountId(),
                 request.amount(), request.currency());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Virement International initié", toDto(tx)));

    
    	
    }
    @PostMapping("/api/v1/transactions/cash-deposit")
    @PreAuthorize("hasAnyRole('TELLER','MANAGER','ADMIN')")
    @Operation(summary = "Enregistrer un dépôt d'espèces",
               description = "Réservé aux guichetiers. Crédite le compte immédiatement.")
    public ResponseEntity<ApiResponse<TransactionDTO>> cashDeposit(
            @Valid @RequestBody CashRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        Transaction tx = transactionService.cashDeposit(
            request.accountId(),
            principal.userId(),
            request.amount(),
            request.currency()
        );
 
        log.info("[TX] Dépôt espèces — ref={} account={} amount={} {}",
                 tx.getReference(), request.accountId(),
                 request.amount(), request.currency());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Dépôt enregistré", toDto(tx)));
    }
    
    @PostMapping("/api/v1/transactions/cash-withdrawal")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER', 'MANAGER', 'ADMIN')")
    @Operation(summary="Enregistrer un retrait au DAB")
    public ResponseEntity<ApiResponse<TransactionDTO>> cashWithDrawal(@Valid @RequestBody CashWithdrawalRequest request,
    		@AuthenticationPrincipal BankingUserPrincipal principal){
    	
    	Transaction tx = transactionService.cashWithdrawal(
    			request.accountId(), 
    			request.cardId(),
    			principal.userId(),
    			request.amount(), 
    			request.currency());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Retrait enregistré", toDto(tx)));

    }
    @PostMapping("/api/v1/transactions/cash-refund")
    @PreAuthorize("hasAnyRole('CUSTOMER','TELLER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Enregistrer un remboursement de paiement carte",
            description = "Le montant remboursé ne peut pas dépasser le montant d'origine.")
    public ResponseEntity<ApiResponse<TransactionDTO>> cardRefund(@Valid @RequestBody CardRefundRequest request,
    		@AuthenticationPrincipal BankingUserPrincipal principal){
    	
    	Transaction tx = transactionService.cardRefund(
    			request.accountId(), 
    			request.originalTransactionId(),
    			principal.userId(),
    			request.amount(), 
    			request.currency(),
    			request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Remboursement enregistré", toDto(tx)));

    }
    @PostMapping("/api/v1/transactions/direct-debit")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    @Operation(summary = "Exécuter un prélèvement SEPA automatique",
               description = "Réservé aux services internes et intégrations automatisées.")
    public ResponseEntity<ApiResponse<TransactionDTO>> directDebit(
            @Valid @RequestBody DirectDebitRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        Transaction tx = transactionService.directDebit(
            request.accountId(),
            request.mandateId(),
            request.creditorName(),
            request.creditorIban(),
            request.amount(),
            request.currency(),
            request.label()
        );
 
        log.info("[TX] Prélèvement SEPA — ref={} mandate={} amount={} {}",
                 tx.getReference(), request.mandateId(),
                 request.amount(), request.currency());
 
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Prélèvement enregistré", toDto(tx)));
    }
    @PutMapping("/api/v1/transactions/{id}/confirm")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    @Operation(summary = "Confirmer une transaction suspecte comme légitime",
               description = "Passe la transaction de FRAUD_SUSPECT à CONFIRMED. " +
                             "Déclenche la reprise du traitement.")
    public ResponseEntity<ApiResponse<TransactionDTO>> confirmTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        TransactionDTO tx = transactionService.confirm(id, principal.userId());
 
        log.info("[TX] Confirmée par compliance — id={} operator={}", id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction confirmée", tx));
    }
    @PutMapping("/api/v1/transactions/{id}/block")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    @Operation(summary = "Bloquer définitivement une transaction suspecte",
               description = "Passe la transaction en BLOCKED. Les fonds réservés sont libérés.")
    public ResponseEntity<ApiResponse<TransactionDTO>> blockTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody BlockTransactionRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        TransactionDTO tx = transactionService.blockTransaction(
            id, request.reason(), principal.userId());
 
        log.warn("[TX] Bloquée — id={} reason={} operator={}",
                 id, request.reason(), principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction bloquée", tx));
    }
    public record BlockTransactionRequest(
    		 
            @NotBlank(message = "Le motif est obligatoire")
            @Size(max = 255)
            String reason
        ) {}

    public record DirectDebitRequest(
    		 
            @NotNull UUID    accountId,
     
            @NotBlank
            @Size(max = 35) String mandateId,
     
            @NotBlank
            @Size(max = 100) String creditorName,
     
            @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$",
                     message = "Format IBAN invalide")
            String creditorIban,
     
            @NotNull
            @DecimalMin("0.01") BigDecimal amount,
     
            @NotNull CurrencyCode currency,
     
            @jakarta.validation.constraints.NotBlank
            @Size(max = 140) String label
        ) {}

    public record CardRefundRequest(
    		 
            @NotNull UUID accountId,
            @NotNull UUID originalTransactionId,
     
            @NotNull
            @DecimalMin("0.01") BigDecimal amount,
     
            @NotNull CurrencyCode currency,
     
            @NotBlank
            @Size(max = 255) String reason
        ) {}

    public record CashWithdrawalRequest(
    		@NotNull UUID accountId,
    		@NotNull UUID cardId,
    		@NotNull
    		@DecimalMin("0.01") BigDecimal amount,
    		@NotNull CurrencyCode currency
    		) {}
    public record CardPaymentRequest(
    		 
            @NotNull UUID    accountId,
            @NotNull UUID    cardId,
     
            @NotNull
            @DecimalMin("0.01") BigDecimal amount,
     
            @NotNull CurrencyCode currency,
     
            @NotBlank
            @Size(max = 100) String merchantName,
     
            @Size(max = 140) String label
        ) {}
    public record CashRequest(
    		 
            @NotNull UUID accountId,
     
            @NotNull
            @DecimalMin("0.01") BigDecimal amount,
     
            @NotNull CurrencyCode currency
        ) {}

    public record SwiftTransferRequest(
    		 
            @NotNull UUID    sourceAccountId,
     
            @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$",
                     message = "Format IBAN invalide")
            String destinationIban,
     
            @jakarta.validation.constraints.NotBlank
            @Size(max = 100) String beneficiaryName,
     
            @Pattern(regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$",
                     message = "Format BIC/SWIFT invalide")
            String beneficiaryBic,
     
            @NotNull
            @DecimalMin("0.01") BigDecimal amount,
     
            @NotNull CurrencyCode currency,
     
            @NotBlank
            @Size(max = 140) String label
        ) {}

    public record InternalTransferRequest(
    		 
            @NotNull(message = "Le compte source est obligatoire")
            UUID sourceAccountId,
     
            @NotNull(message = "Le compte destinataire est obligatoire")
            UUID destinationAccountId,
     
            @NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
            BigDecimal amount,
     
            @NotNull(message = "La devise est obligatoire")
            CurrencyCode currency,
     
            @NotBlank(message = "Le motif est obligatoire")
            @Size(max = 140, message = "Le motif ne doit pas dépasser 140 caractères")
            String label
        ) {}

    public record TransferRequest(
    		@NotNull(message = "Le compte source est obligatoire")
    		UUID sourceAccountId,
    		@NotNull(message = "L iban source est obligatoire")
    		String destinationIban,
    		@NotNull(message = "Le nom est obligatoire")
    		String beneficiaryName,
    		@NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
    		BigDecimal amount,
    		@NotNull(message = "La devise est obligatoire")
    		CurrencyCode currency,
    		@NotBlank(message = "Le motif est obligatoire")
            @Size(max = 140, message = "Le motif ne doit pas dépasser 140 caractères")
    		String label,
    		String endToEndId,
    		boolean instant
    		) {}
    
    private TransactionDTO toDto(Transaction tx) {
        return new TransactionDTO(
            tx.getId(),
            tx.getReference(),
            tx.getType(),
            tx.getType().getLabel(),
            tx.getStatus(),
            tx.getStatus().getLabel(),
            tx.getAmount(),
            tx.getCurrency(),
            tx.getAmountEur(),
            tx.getExchangeRate(),
            tx.getFees(),
            tx.totalAmount(),
            tx.getAccount() != null ? tx.getAccount().getId() : null,
            tx.getCounterpartIban(),
            tx.getCounterpartName(),
            tx.getCounterpartBic(),
            tx.getLabel(),
            tx.getRejectionReason(),
            tx.getEndToEndId(),
            tx.getMandateId(),
            null,   // fraudScore masqué pour les non-compliance
            tx.getCreatedAt(),
            tx.getUpdatedAt(),
            tx.getSettledAt()
        );
    }

}
