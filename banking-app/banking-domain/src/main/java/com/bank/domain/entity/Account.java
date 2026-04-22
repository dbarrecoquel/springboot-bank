package com.bank.domain.entity;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Id;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_account_iban",    columnList = "iban",    unique = true),
        @Index(name = "idx_account_user_id", columnList = "user_id"),
        @Index(name = "idx_account_status",  columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"owner", "transactions", "cards"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Account {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	@EqualsAndHashCode.Include
	private UUID id;
	
    @Column(name = "iban", length = 34, nullable = false, updatable = false)
    @NotBlank(message = "L'IBAN est obligatoire")
    @Pattern(
        regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$",
        message = "Format IBAN invalide"
    )
    private String iban;
    
    @Column(name = "account_number", length = 20, nullable = false, unique = true, updatable = false)
    @NotBlank
    private String accountNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    @NotNull(message = "Le type de compte est obligatoire")
    private AccountType type;
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @NotNull
    private AccountStatus status = AccountStatus.ACTIVE;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    @NotNull(message = "La devise est obligatoire")
    private CurrencyCode currency = CurrencyCode.EUR;

    /**
     * Solde courant du compte.
     * Précision : 19 chiffres significatifs, 4 décimales (conformité SEPA).
     */
    @Column(name = "balance", precision = 19, scale = 4, nullable = false)
    @NotNull
    @DecimalMin(value = "-999999999.9999", message = "Solde hors limites")
    private BigDecimal balance = BigDecimal.ZERO;
    

    /**
     * Limite de découvert autorisé (valeur positive, ex : 500.00 = découvert de 500 €).
     * 0 par défaut (pas de découvert).
     */
    @Column(name = "overdraft_limit", precision = 10, scale = 4, nullable = false)
    @NotNull
    @DecimalMin(value = "0.0", message = "La limite de découvert ne peut pas être négative")
    private BigDecimal overdraftLimit = BigDecimal.ZERO;
    
    /**
     * Taux d'intérêt annuel applicable (pour comptes épargne ou crédit).
     * Exprimé en pourcentage : 2.50 = 2,50 %.
     */
    @Column(name = "interest_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private BigDecimal interestRate;
    

    @Column(name = "label", length = 100)
    @Size(max = 100, message = "Le libellé ne doit pas dépasser 100 caractères")
    private String label;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @NotNull(message = "Le titulaire du compte est obligatoire")
    private User owner;
    
    @OneToMany(
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
        )
    @OrderBy("createdAt DESC")
    private List<Transaction> transactions = new ArrayList<>();
    
    @OneToMany(
        mappedBy = "account",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<Card> cards = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
 
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
 
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
 
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
 
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public static Account create(
            String iban,
            String accountNumber,
            AccountType type,
            CurrencyCode currency,
            User owner) {
 
        Account account = new Account();
        account.iban          = iban;
        account.accountNumber = accountNumber;
        account.type          = type;
        account.currency      = currency;
        account.owner         = owner;
        account.status        = AccountStatus.ACTIVE;
        account.balance       = BigDecimal.ZERO;
        account.overdraftLimit = BigDecimal.ZERO;
        return account;
    }
    /**
     * Crédite le compte du montant spécifié.
     *
     * @param amount montant positif à créditer
     * @throws IllegalArgumentException si le montant est nul ou négatif
     * @throws IllegalStateException    si le compte n'est pas actif
     */
    public void credit(BigDecimal amount) {
        validateActive();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant à créditer doit être strictement positif");
        }
        this.balance = this.balance.add(amount);
    }
    
    /**
     * Débite le compte du montant spécifié.
     *
     * @param amount montant positif à débiter
     * @throws IllegalArgumentException si le montant est nul ou négatif
     * @throws IllegalStateException    si le compte n'est pas actif ou si les fonds sont insuffisants
     */
    public void debit(BigDecimal amount) {
        validateActive();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant à débiter doit être strictement positif");
        }
        BigDecimal availableFunds = this.balance.add(this.overdraftLimit);
        if (amount.compareTo(availableFunds) > 0) {
            throw new IllegalStateException(
                String.format("Fonds insuffisants — disponible : %s %s, demandé : %s %s",
                    availableFunds, this.currency, amount, this.currency)
            );
        }
        this.balance = this.balance.subtract(amount);
    }
    
    /**
     * Bloque le compte 
     *
     * @throws IllegalStateException si le compte est déjà clôturé
     */
    public void block() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Impossible de bloquer un compte clôturé");
        }
        this.status = AccountStatus.BLOCKED;
    }
    
    /**
     * Clôture le compte.
     *
     * @throws IllegalStateException si le solde n'est pas nul
     */
    public void close() {
        if (this.balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException(
                "Le compte doit avoir un solde nul avant clôture — solde actuel : " + this.balance
            );
        }
        this.status    = AccountStatus.CLOSED;
        this.closedAt  = LocalDateTime.now();
    }
    
    /**
     * Indique si le compte peut être débité (actif et fonds suffisants).
     */
    public boolean canDebit(BigDecimal amount) {
        return this.status == AccountStatus.ACTIVE
            && amount != null
            && amount.compareTo(BigDecimal.ZERO) > 0
            && amount.compareTo(this.balance.add(this.overdraftLimit)) <= 0;
    }
    
    /**
     * Solde disponible = balance + overdraftLimit.
     */
    public BigDecimal availableBalance() {
        return this.balance.add(this.overdraftLimit);
    }
    
    private void validateActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                "Opération impossible — statut du compte : " + this.status
            );
        }
    }
    
    
}