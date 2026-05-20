package com.bank.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
