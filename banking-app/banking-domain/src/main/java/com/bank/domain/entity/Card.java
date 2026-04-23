package com.bank.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bank.domain.enums.CardStatus;
import com.bank.domain.enums.CurrencyCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table (
		name ="cards",
		indexes = {
				@Index(name = "idx_card_account_id", columnList = "account_id"),
				@Index(name = "idx_card_owner_id", columnList = "owner_id"),
				@Index(name = "idx_card_status", columnList = "status")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"panEncrypted","cvvHash","account","onwer"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    
    /**
     * PAN chiffré AES-256. Ne jamais logger ni exposer dans un DTO.
     */
    @Column(name = "pan_encrypted", nullable = false, updatable = false)
    @NotBlank
    private String panEncrypted;
    
    /**
     * PAN masqué pour affichage IHM : {@code **** **** **** 4242}.
     */
    @Column(name = "pan_masked", length = 19, nullable = false, updatable = false)
    @NotBlank
    @Pattern(
        regexp = "^\\*{4} \\*{4} \\*{4} \\d{4}$",
        message = "Format PAN masqué invalide"
    )
    private String panMasked;
    
    /**
     * Hash BCrypt du CVV. Non réversible — la vérification se fait par comparaison de hash.
     */
    @Column(name = "cvv_hash", nullable = false, updatable = false)
    @NotBlank
    private String cvvHash;
    
    @Column(name = "cardholder_name", length = 26, nullable = false)
    @NotBlank(message = "Le nom du porteur est obligatoire")
    @Size(max = 26, message = "Le nom du porteur ne doit pas dépasser 26 caractères (norme ISO 7813)")
    private String cardholderName;
 
    @Column(name = "expiry_date", nullable = false)
    @NotNull(message = "La date d'expiration est obligatoire")
    @Future(message = "La date d'expiration doit être dans le futur")
    private LocalDate expiryDate;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @NotNull
    private CardStatus status = CardStatus.INACTIVE;
 
    @Column(name = "virtual", nullable = false)
    private boolean virtual = false;
 
    @Column(name = "contactless_enabled", nullable = false)
    private boolean contactlessEnabled = true;
 
    @Column(name = "online_payments_enabled", nullable = false)
    private boolean onlinePaymentsEnabled = true;
 
    @Column(name = "international_payments_enabled", nullable = false)
    private boolean internationalPaymentsEnabled = false;
    
    /**
     * Plafond de paiement journalier (€ ou devise du compte).
     */
    @Column(name = "daily_payment_limit", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal dailyPaymentLimit = new BigDecimal("1000.00");
 
    /**
     * Plafond de retrait DAB journalier.
     */
    @Column(name = "daily_withdrawal_limit", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal dailyWithdrawalLimit = new BigDecimal("500.00");
 
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    private CurrencyCode currency = CurrencyCode.EUR;
 
    // ─────────────────────────────────────────────────────────
    //  Tentatives PIN
    // ─────────────────────────────────────────────────────────
 
    @Column(name = "pin_attempts", nullable = false)
    private int pinAttempts = 0;
 
    @Column(name = "pin_blocked", nullable = false)
    private boolean pinBlocked = false;
 
    // ─────────────────────────────────────────────────────────
    //  Relations
    // ─────────────────────────────────────────────────────────
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    @NotNull
    private Account account;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    @NotNull
    private User owner;
 
    // ─────────────────────────────────────────────────────────
    //  Audit
    // ─────────────────────────────────────────────────────────
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
 
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
 
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
 
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
    
    public void activate() {
        if (this.status != CardStatus.INACTIVE) {
            throw new IllegalStateException("Seule une carte inactive peut être activée — statut actuel : " + this.status);
        }
        this.status      = CardStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }
    
    public void block() {
        if (this.status == CardStatus.EXPIRED || this.status == CardStatus.CANCELLED) {
            throw new IllegalStateException("Impossible de bloquer une carte expirée ou annulée");
        }
        this.status    = CardStatus.BLOCKED;
        this.blockedAt = LocalDateTime.now();
    }
    public void cancel() {
        this.status = CardStatus.CANCELLED;
    }
    public boolean isUsable() {
        return this.status == CardStatus.ACTIVE
            && !this.pinBlocked
            && this.expiryDate.isAfter(LocalDate.now());
    }
    public void recordFailedPin() {
        this.pinAttempts++;
        if (this.pinAttempts >= 3) {
            this.pinBlocked = true;
            this.status     = CardStatus.BLOCKED;
            this.blockedAt  = LocalDateTime.now();
        }
    }
    public void resetPin() {
        this.pinAttempts = 0;
        this.pinBlocked  = false;
    }
 
    public boolean isExpired() {
        return this.expiryDate.isBefore(LocalDate.now());
    }
    
}
