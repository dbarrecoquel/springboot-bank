package com.bank.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bank.domain.enums.UserRole;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
	name = "users",
	indexes = {
			@Index(name = "idx_user_email", columnList = "email", unique= true),
			@Index(name = "idx_user_phone", columnList = "phone_number", unique = true),
			@Index(name = "idx_user_national_id", columnList = "national_id", unique = true)
	}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"passwordHash", "accounts", "cards"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
	@EqualsAndHashCode.Include
	private UUID id;
	
	@Column(name = "first_name", length = 80, nullable = false)
	@NotBlank(message = "le prénom est obligatoire")
	@Size(max = 80)
	private String firstName;
	
	@Column(name = "last_name", length = 80, nullable = false)
	@NotBlank(message = "le nom est obligatoire")
	@Size(max = 80)
	private String lastName;
	
	@Column(name = "date_of_birth", nullable = false)
	@NotNull(message = "la date de naissance est obligatoire")
	@Past(message = "la date de naissance doit être dans le passé")
	private LocalDate dateOfBirth;
	
	@Column(name = "national_id", length = 255)
	private String nationalId;
	
	@Column(name = "nationality", length = 3)
	@Size(max = 3, message = "le code nationalité doit être sur 3 caractères, ISO 3166-1 alpha-3")
	private String nationality;
	
	@Column(name = "email", length = 150, nullable = false, unique = true)
	@NotBlank(message = "l'email est obligatoire")
	@Email(message = "format email invalide")
	@Size(max = 150)
	private String email;
	
	@Column(name = "phone_number", length = 20)
    @Pattern(
        regexp = "^\\+?[1-9]\\d{6,14}$",
        message = "Numéro de téléphone invalide (format E.164)"
    )
    private String phoneNumber;
	
	@Column(name = "address_line1", length = 150)
	private String addressLine1;
	
	@Column(name = "address_line2", length = 150)
	private String addressLine2;
	
	@Column(name = "city", length = 80)
	private String city;
	
	@Column(name = "postal_code", length = 20)
	private String postalCode;
	
	@Column(name = "country_code", length= 3)
	@Size(max = 3, message = "Le code pays doit être sur 3 caractères (ISO 3166-1 alpha-3)")
	private String countryCode;
	
	@Column(name = "password_hash", nullable = false)
	@NotBlank
	private String passwordHash;
	
	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts = 0;
	
	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;
	
	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;
	
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
 
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;
 
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;
    
    @Column(name = "kyc_verified", nullable = false)
    private boolean kycVerified = false;
 
    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;
    
    @ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(
	        name = "user_roles",
	    joinColumns = @JoinColumn(name = "user_id")
	)
	@Enumerated(EnumType.STRING)
	@Column(name = "role", length = 30, nullable = false)
	private Set<UserRole> roles = EnumSet.of(UserRole.CUSTOMER);
    
    @OneToMany(
            mappedBy = "owner",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Account> accounts = new ArrayList<>();
    
    @OneToMany(
            mappedBy = "owner",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Card> cards = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
 
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    @PrePersist
    protected void onCreate() {
    	this.createdAt = LocalDateTime.now();
    	this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate(){
    	this.updatedAt = LocalDateTime.now();
    }
    
    public static User create(String firstName, String lastName,
            LocalDate dateOfBirth, String email,
            String passwordHash) {
		User user = new User();
		user.firstName    = firstName;
		user.lastName     = lastName;
		user.dateOfBirth  = dateOfBirth;
		user.email        = email;
		user.passwordHash = passwordHash;
		user.enabled      = true;
		user.roles        = EnumSet.of(UserRole.CUSTOMER);
		return user;
	}
    
    public String getFullName() {
    	
    	return firstName + " " + lastName;
    }
    
    public boolean isAccountLocked() {
    	
    	return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }
    
    public void recordFailedLogin() {
    	this.failedLoginAttempts++;
    	
    	if (this.failedLoginAttempts >= 5)
    		this.lockedUntil = LocalDateTime.now().plusMinutes(30);
    }
    
    public void recordSuccessfulLogin() {
    	this.failedLoginAttempts = 0;
    	this.lockedUntil = null;
    	this.lastLoginAt = LocalDateTime.now();
    }
    
    public void verifyKyc () {
    	this.kycVerified = true;
    	this.kycVerifiedAt = LocalDateTime.now();
    }
    
    public boolean hasRole(UserRole role) {
    	return this.roles.contains(role);
    }
    
    public void addRole(UserRole role) {
    	this.roles.add(role);
    }
    
    public void removeRole(UserRole role) {
    	this.roles.remove(role);
    }
}
