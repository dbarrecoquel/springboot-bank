package com.bank.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// 🛠️ CORRECTIFS DES IMPORTS DE TEST DE TRANCHE JPA :
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bank.domain.entity.Account;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.UserRepository;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/clean-db.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AccountRepository — Integration Tests")
public class AccountRepositoryTest {
	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
	    .withDatabaseName("banking_test")
	    .withUsername("testuser")
	    .withPassword("testpass")
	    .withReuse(true);
	
	@DynamicPropertySource
	static void overideDataSource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.datasource.hikari.auto-commit", () -> "false");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
	}
	
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestEntityManager em;
    
    private User alice;
    private User bob;
    private Account aliceCurrentAccount;
    private Account aliceSavingsAccount;
    private Account bobCurrentAccount;
    
    @BeforeEach()
    void setUp() {
    	alice = buildUser("alice@bank.com","alice","alice");
    	bob = buildUser("bob@bank.com", "bob", "bob");
    	em.persistAndFlush(alice);
    	em.persistAndFlush(bob);
    	
        aliceCurrentAccount = buildAccount(
                "FR7630006000011234567890189", "ACC-001",
                AccountType.CURRENT, CurrencyCode.EUR,
                new BigDecimal("1500.00"), alice
            );
        
        aliceSavingsAccount = buildAccount(
                "FR7630006000019876543210456", "ACC-002",
                AccountType.SAVINGS, CurrencyCode.EUR,
                new BigDecimal("5000.00"), alice
            );
     
        bobCurrentAccount = buildAccount(
                "FR7630006000015555666677778", "ACC-003",
                AccountType.CURRENT, CurrencyCode.EUR,
                new BigDecimal("250.00"), bob
            );
     
        em.persistAndFlush(aliceCurrentAccount);
        em.persistAndFlush(aliceSavingsAccount);
        em.persistAndFlush(bobCurrentAccount);
        em.clear(); 
    }
    
    @Nested
    @DisplayName("Recherches simples")
    class FindTests {
    	
    	@Test
    	@DisplayName("findByIban - retourne le compte par iban")
    	void findByIban_found() {
    		Optional<Account> account = accountRepository.findByIban("FR7630006000011234567890189");
    		assertThat(account).isPresent();
    		assertThat(account.get().getAccountNumber()).isEqualTo("ACC-001");
    		assertThat(account.get().getOwner().getEmail()).isEqualTo("alice@bank.com");
    	}
    	
    	@Test
    	@DisplayName("findByIban - not found")
    	void findByIban_notFound() {
    		Optional<Account> account = accountRepository.findByIban("TOTO");
    		assertThat(account).isEmpty();
    	}
    	
    	@Test
    	@DisplayName("findByAccountNumber - found")
    	void findByAccountNumber_found() {
    		Optional<Account> account = accountRepository.findByAccountNumber("ACC-002");
    		assertThat(account).isPresent();
    		assertThat(account.get().getType()).isEqualTo(AccountType.SAVINGS);
    	}
    	
    	@Test
    	@DisplayName("findByAccountNumber - not found")
    	void findByAccountNumber_notFound() {
    		Optional<Account> account = accountRepository.findByAccountNumber("ACC-004");
    		assertThat(account).isEmpty();
    	}
    	
    	@Test
    	@DisplayName("existByIban - true")
    	void existByIban_true() {
    		assertThat(accountRepository.existsByIban("FR7630006000011234567890189")).isTrue();
    	}
    	
    	@Test
    	@DisplayName("existByIban - false")
    	void existByIban_false() {
    		assertThat(accountRepository.existsByIban("TOTO")).isFalse();
    	}
    	
    }
    
    @Nested
    @DisplayName("Recherche par propriétaire") 
    class FindByOwnerTests{
    	
    	@Test
    	@DisplayName("findByOwnerIdOrderByCreatedAt")
    	void findByOwnerId_allAccounts() {
    		List<Account> accounts = accountRepository.findByOwnerIdOrderByCreatedAtDesc(alice.getId());
    		
    		assertThat(accounts).hasSize(2);
    		assertThat(accounts).extracting(Account::getAccountNumber).containsExactlyInAnyOrder("ACC-001","ACC-002");
    		
    	}
    	
    	@Test
    	@DisplayName("findByOwnerId - paginé")
    	void findByOwnerId_pageable() {
    		Page<Account> accounts = accountRepository.findByOwnerId(alice.getId(),  PageRequest.of(0, 1, Sort.by("createdAt").descending()));
    		
    		assertThat(accounts.getTotalElements()).isEqualTo(2);
    		assertThat(accounts.getContent()).hasSize(1);
    	}
    	
    	@Test
    	@DisplayName("findByOwnerIdAndStatus - active")
    	void findByOwnerId_status() {
    		List<Account> accounts = accountRepository.findByOwnerIdAndStatus(alice.getId(), AccountStatus.ACTIVE);
    		assertThat(accounts).hasSize(2);
    		
    	}
    	
    	@Test
    	@DisplayName("findByOwnerIdAndStatus - blocked")
    	void findByOwnerId_blocked() {
    		List<Account> accounts = accountRepository.findByOwnerIdAndStatus(alice.getId(), AccountStatus.BLOCKED);
    		assertThat(accounts).isEmpty();
    	}
    	
    	@Test
    	@DisplayName("findByOwnerIdAndType - savings")
    	void findByOwnerId_savings() {
    		List<Account> accounts = accountRepository.findByOwnerIdAndType(alice.getId(), AccountType.SAVINGS);
    		assertThat(accounts).hasSize(1);
    		assertThat(accounts.get(0).getIban()).isEqualTo("FR7630006000019876543210456");
    	}
    	
    	@Test
    	@DisplayName("existByIdAndOwnerdId - true")
    	void existByOwnerIdAndId_true() {
    		assertThat(accountRepository.existsByIdAndOwnerId(aliceCurrentAccount.getId(), alice.getId())).isTrue();  		
    	}
    	
    	@Test
    	@DisplayName("existByIdAndOwnerdId - false")
    	void existByOwnerIdAndId_false() {
    		assertThat(accountRepository.existsByIdAndOwnerId(aliceCurrentAccount.getId(), bob.getId())).isFalse();  		
    	}
    	
    	@Test
    	@DisplayName("findByOwnerId - no account")
    	void findByOwnerId_noAccount() {
            User charlie = buildUser("charlie@bank.com", "Charlie", "Moreau");
            em.persistAndFlush(charlie);
            em.clear();
            List<Account> accounts =
                    accountRepository.findByOwnerIdOrderByCreatedAtDesc(charlie.getId());

            assertThat(accounts).isEmpty();
    	}
    	
    }
    
    @Nested
    @DisplayName("Requetes join fetch")
    class JointFetchTests {
    	
    	@Test
    	@DisplayName("findByIdWithOwner - charge le compte et son proprietaire sans N+1")
    	void findByIdWithOwner_loadOwners() {
    		
    		Optional<Account> result = accountRepository.findByIdWithOwner(aliceCurrentAccount.getId());
    		assertThat(result.isPresent()).isTrue();
    		Account account = result.get();
    		assertThat(account).isNotNull();
    		assertThat(account.getOwner().getEmail()).isEqualTo("alice@bank.com");
    		assertThat(account.getOwner().getFirstName()).isEqualTo("alice");
    	}
    	@Test
    	@DisplayName("findByIdWithOwner - not found")
    	void findByIdWithOwner_noFound() {
    		Optional<Account> result = accountRepository.findByIdWithOwner(UUID.randomUUID());
    		assertThat(result).isEmpty();
    	}
    	
    }
    
    @Nested
    @DisplayName("Mise a jours ciblées")
    class UpdateTests {
    	
    	@Test
    	@DisplayName("updateBalance - met a jour le solde et retourne une ligne modifié")
    	void updateBalance_success() {
    		
    		BigDecimal newBalance = new BigDecimal("2000.00");
    		LocalDateTime now = LocalDateTime.now();
    		
    		int updated = accountRepository.updateBalance(aliceCurrentAccount.getId(), newBalance, now);
    		
    		assertThat(updated).isEqualTo(1);
    		
    		em.clear();
    		Account reloaded = accountRepository.findById(aliceCurrentAccount.getId()).orElseThrow();
    		assertThat(reloaded.getBalance()).isEqualByComparingTo(newBalance);
    	}
    	
    	@Test
    	@DisplayName("updateBalance - not found")
    	void updateBalance_idNotFound() {
    		BigDecimal newBalance = new BigDecimal("2000.00");
    		LocalDateTime now = LocalDateTime.now();
    		
    		int updated = accountRepository.updateBalance(UUID.randomUUID(), newBalance, now);
    		
    		assertThat(updated).isEqualTo(0);
    	}
    	
    	@Test
    	@DisplayName("updateStatus to blocked")
    	void updateStatus_toBlocked() {
    		
    		int updated = accountRepository.updateStatus(aliceCurrentAccount.getId(), AccountStatus.BLOCKED, LocalDateTime.now());
    		assertThat(updated).isEqualTo(1);
    		
    		em.clear();
    		
    		Account reloaded = accountRepository.findById(aliceCurrentAccount.getId()).orElseThrow();
    		assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.BLOCKED);
    		
    	}
    	
    	@Test
    	@DisplayName("updateStatus to closed")
    	void updateStatusToClosed() {
    		
    		int updated = accountRepository.updateStatus(aliceCurrentAccount.getId(), AccountStatus.CLOSED, LocalDateTime.now());
    		assertThat(updated).isEqualTo(1);
    		
    		em.clear();
    		Account reloaded = accountRepository.findById(aliceCurrentAccount.getId()).orElseThrow();
    		assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.CLOSED);
    		
    	}  	
    	
    }
    
    @Nested
    @DisplayName("Agregation test")
    class AggregationTests {
    	
    	@Test
    	@DisplayName("sumBalanceByOwnerAndCurrency — somme des soldes actifs d'Alice en EUR")
    	void sumBalance_activeAccounts() {
    		
    		Optional<BigDecimal> total = accountRepository.sumBalanceByOwnerAndCurrency(alice.getId(), CurrencyCode.EUR);
    		
    		assertThat(total).isPresent();
    		assertThat(total.get()).isEqualByComparingTo(new BigDecimal("6500.00"));
    		
    	}
    	
        @Test
        @DisplayName("sumBalanceByOwnerAndCurrency — retourne vide si aucun compte actif")
        void sumBalance_noActiveAccounts() {
            accountRepository.updateStatus(
                aliceCurrentAccount.getId(), AccountStatus.CLOSED, LocalDateTime.now());
            accountRepository.updateStatus(
                aliceSavingsAccount.getId(), AccountStatus.CLOSED, LocalDateTime.now());
            em.clear();
 
            Optional<BigDecimal> total = accountRepository
                .sumBalanceByOwnerAndCurrency(alice.getId(), CurrencyCode.EUR);
 
            // COALESCE(SUM(...), 0) retourne 0 ou empty selon l'implémentation
            assertThat(total.map(v -> v.compareTo(BigDecimal.ZERO) == 0).orElse(true))
                .isTrue();
        }
        
        @Test
        @DisplayName("countByStatus")
        void countByStatus_returnStats() {
        	
        	List<Object[]>  list = accountRepository.countByStatus();
        	
        	assertThat(list).isNotEmpty();
        	//"SELECT a.status, COUNT(a) FROM Account a GROUP BY a.status"
        	boolean hasActive = list.stream().anyMatch(row -> AccountStatus.ACTIVE.name().equals(row[0].toString())
                    && ((Number) row[1]).longValue() == 3);
        	
        	assertThat(hasActive).isTrue();

        }
        @Test
        @DisplayName("findOverdrawnAccounts — retourne les comptes avec solde négatif")
        void findOverdrawnAccounts_detected() {
            // Mettre le compte de Bob en découvert
            accountRepository.updateBalance(
                bobCurrentAccount.getId(), new BigDecimal("-50.00"), LocalDateTime.now());
            em.clear();
 
            Page<Account> overdrawn = accountRepository.findOverdrawnAccounts(
                PageRequest.of(0, 10));
 
            assertThat(overdrawn.getTotalElements()).isEqualTo(1);
            assertThat(overdrawn.getContent().get(0).getAccountNumber())
                .isEqualTo("ACC-003");
        }
        
        @Test
        @DisplayName("findOverDrawnAccounts - no accounts")
        void findOverdrawnAccounts_noAccount() {
        	
        	Page<Account> overdrawn = accountRepository.findOverdrawnAccounts(
                    PageRequest.of(0, 10));
        	
        	assertThat(overdrawn.getTotalElements()).isZero();
        	
        }

        @Test
        @DisplayName("findDomantAccount")
        void findDormantAccount_detected() {
        	
            em.getEntityManager()
            .createQuery("UPDATE Account a SET a.updatedAt = :old WHERE a.id = :id")
            .setParameter("old", LocalDateTime.now().minusYears(2))
            .setParameter("id", aliceSavingsAccount.getId())
            .executeUpdate();
	        em.flush();
	        em.clear();
          
            LocalDateTime threshold = LocalDateTime.now().minusYears(1);
            Page<Account> dormant = accountRepository.findDormantAccounts(
                threshold, PageRequest.of(0, 10));
 
            assertThat(dormant.getContent())
                .extracting(Account::getAccountNumber)
                .contains("ACC-002");

        }

    }
    @Nested
    @DisplayName("Verrouillage pessimiste")
    class LockTests {
 
        @Test
        @DisplayName("findByIdWithLock — charge le compte sans exception (lock disponible)")
        void findByIdWithLock_success() {
            assertThatNoException().isThrownBy(() ->
                accountRepository.findByIdWithLock(aliceCurrentAccount.getId())
            );
        }
 
        @Test
        @DisplayName("findByIdWithReadLock — charge le compte en lecture partagée")
        void findByIdWithReadLock_success() {
            Optional<Account> result =
                accountRepository.findByIdWithReadLock(aliceCurrentAccount.getId());
 
            assertThat(result).isPresent();
            assertThat(result.get().getBalance())
                .isEqualByComparingTo(new BigDecimal("1500.00"));
        }
    }

    @Nested
    @DisplayName("CRUD Tests")
    class CrudTests {
    	
    	@Test
    	@DisplayName("save et persist un nouveau compte")
    	void save_newAccount() {
    		
    		User diana = buildUser("diana@bank.com","diana", "diana");
    		em.persistAndFlush(diana);
    		
    		Account newAccount = Account.create("FR7614508059400370188111222", "ACC-NEW-001",
                    AccountType.CURRENT, CurrencyCode.EUR, diana);
    		newAccount.setLabel("Compte principal Diana");
    		
    		Account saved = accountRepository.save(newAccount);
    		assertThat(saved).isNotNull();
    		assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    		assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    		assertThat(saved.getCreatedAt()).isNotNull();
    		assertThat(saved.getVersion()).isZero();
    		
    	}
        @Test
        @DisplayName("findById — retourne le compte persisté")
        void findById_found() {
            Optional<Account> found =
                accountRepository.findById(aliceCurrentAccount.getId());
 
            assertThat(found).isPresent();
            assertThat(found.get().getIban())
                .isEqualTo("FR7630006000011234567890189");
        }
 
        @Test
        @DisplayName("findById — retourne vide pour ID inexistant")
        void findById_notFound() {
            assertThat(accountRepository.findById(UUID.randomUUID())).isEmpty();
        }
        
        @Test
        @DisplayName("delete Account")
        void delete_removeAccount() {
        	
        	UUID id = aliceCurrentAccount.getId();
        	accountRepository.deleteById(id);
        	em.flush();
        	em.clear();
        	
        	Optional<Account> removed = accountRepository.findById(id);
        	
        	assertThat(removed).isEmpty();
        	
        }
    	
    }
    
    private User buildUser(String email, String firstName, String lastName) {
        return User.create(
            firstName, lastName,
            LocalDate.of(1985, 6, 15),
            email,
            "$2a$10$hashedPasswordForTesting"
        );
    }
 
    private Account buildAccount(String iban, String accountNumber,
                                  AccountType type, CurrencyCode currency,
                                  BigDecimal balance, User owner) {
        Account account = Account.create(iban, accountNumber, type, currency, owner);
        account.setBalance(balance);
        return account;
    }
}