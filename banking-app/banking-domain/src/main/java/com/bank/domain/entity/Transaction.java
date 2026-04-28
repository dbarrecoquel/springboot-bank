package com.bank.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;

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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
		name="transactions",
		indexes = {
			@Index(name = "idx_tx_account_id", columnList = "account_id"),
			@Index(name = "idx_tx_counterpart_iban", columnList = "counterpart_iban"),
			@Index(name = "idx_tx_status", columnList = "status"),
			@Index(name = "idx_tx_type", columnList = "type"),
			@Index(name = "idx_tx_reference", columnList = "reference", unique= true),
			@Index(name = "idx_tx_created_at", columnList = "created_at"),
			@Index(name = "idx_tx_card_id", columnList = "card_id")
			
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"account", "card"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	@EqualsAndHashCode.Include
	private UUID id;
	
	/**
     * Référence unique lisible, générée par le service métier.
     * Format : TXN-{YYYYMMDD}-{UUID court}, ex : TXN-20240127-A3F9B2.
     * Utilisée pour les communications client et le support.
     */
	
	@Column(name = "reference", length = 30, nullable = false, updatable = false, unique = true)
	@NotBlank
	private String reference;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "type", length = 40, nullable = false, updatable = false)
	@NotNull(message = "le type de transaction est obligatoire")
	private TransactionType type;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20, nullable = false)
	@NotNull
	private TransactionStatus status = TransactionStatus.PENDING;
	
    /**
     * Montant de l'opération dans la devise d'origine.
     * Toujours positif — le sens du flux est déterminé par {@code type}.
     */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false, updatable = false)
    @NotNull
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    @NotNull
    private CurrencyCode currency;
    
    /**
     * Montant converti en EUR pour les transactions en devise étrangère.
     * Nul si la devise est déjà EUR.
     */
    @Column(name = "amount_eur", precision = 19, scale = 4)
    private BigDecimal amountEur;
    
    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate;
    
    /**
     * Frais bancaires prélevés sur cette transaction (ex : frais SWIFT, commission de change).
     */
    @Column(name = "fees", precision = 10, scale = 4)
    @DecimalMin(value = "0.0")
    private BigDecimal fees = BigDecimal.ZERO;
    
    /**
     * Compte débité ou crédité (compte interne).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    @NotNull
    private Account account;
    

    /**
     * IBAN du compte contrepartie (peut être externe à la banque).
     */
    @Column(name = "counterpart_iban", length = 34, updatable = false)
    private String counterpartIban;
    
    /**
     * Nom du bénéficiaire ou de l'émetteur (pour affichage dans le relevé).
     */
    @Column(name = "counterpart_name", length = 100, updatable = false)
    private String counterpartName;
    
    /**
     * BIC/SWIFT de la banque contrepartie (pour virements internationaux).
     */
    @Column(name = "counterpart_bic", length = 11, updatable = false)
    private String counterpartBic;
 
    /**
     * Carte utilisée pour le paiement (nul si opération non-carte).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", updatable = false)
    private Card card;
    
    @Column(name = "label", length = 140, updatable = false)
    @Size(max = 140, message = "Le libellé ne doit pas dépasser 14O caractères (norme SEPA)")
    private String label;
    
    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;
    
    /**
     * Identifiant de bout-en-bout SEPA (End-to-End ID).
     */
    @Column(name = "end_to_end_id", length = 35, updatable = false)
    private String endToEndId;
    
    /**
     * Identifiant de mandat pour les prélèvements SEPA.
     */
    @Column(name = "mandate_id", length = 35, updatable = false)
    private String mandateId;
    
    /**
     * Score de risque calculé par {@code FraudDetectionService} (0.0 à 1.0).
     * Valeur > 0.7 déclenche le statut {@code FRAUD_SUSPECT}.
     */
    @Column(name = "fraud_score", precision = 4, scale = 3)
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal fraudScore;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Date de règlement définitif (renseignée à la transition vers {@code SETTLED}).
     */
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
 
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
    
    //Factory method
    /**
     * Crée une transaction en attente de traitement.
     *
     * @param reference     référence unique générée par le service
     * @param type          type d'opération
     * @param amount        montant positif
     * @param currency      devise
     * @param account       compte interne concerné
     * @param label         libellé de l'opération
     * @return une instance Transaction avec statut {@code PENDING}
     */
    
    public Transaction create(String reference, TransactionType type,
    		BigDecimal amount, CurrencyCode currency, Account account,
    		String label) {
    	
    	Transaction tx = new Transaction();
    	tx.reference = reference;
    	tx.type = type;
    	tx.amount = amount;
    	tx.currency = currency;
    	tx.account = account;
    	tx.label = label;
    	tx.status = TransactionStatus.PENDING;
    	tx.fees = BigDecimal.ZERO;
    	
    	return tx;
    	
    }
    /**
     * Applique une transition de statut en vérifiant sa légalité.
     *
     * @param target statut cible
     * @throws IllegalStateException si la transition est interdite
     */
    public void transitionTo(TransactionStatus target) {
    	
    	if (!this.status.canTransitionTo(target))
    	{
            throw new IllegalStateException(
                String.format("Transition interdite : %s → %s (transaction %s)",
                    this.status, target, this.reference)
            );
        }
    	
    	this.status = target;
    	
    	if (target == TransactionStatus.SETTLED)
    		this.settledAt = LocalDateTime.now();
    		
    }
    /**
     * Marque la transaction comme réglée et met à jour les soldes du compte.
     * Délègue le mouvement réel au service ({@code TransactionServiceImpl}).
     */
    
    public void settle() {
    	transitionTo(TransactionStatus.SETTLED);
    }
    public void refuse(String reason) {
    	transitionTo(TransactionStatus.REFUSED);
    	this.rejectionReason = reason;
    }
    public void flagAsFraudSuspect(BigDecimal score) {
    	transitionTo(TransactionStatus.FRAUD_SUSPECT);
    	this.fraudScore = score;
    }
    
    public boolean requiresAmlControl() {
    	return this.type.isAmlControlRequired() ||
    			(this.amount != null && this.amount.compareTo(new BigDecimal("10000")) >= 0);
    }
    
    public BigDecimal totalAmount() {
    	return this.amount.add(this.fees == null ? BigDecimal.ZERO : this.fees);
    }
    
}
