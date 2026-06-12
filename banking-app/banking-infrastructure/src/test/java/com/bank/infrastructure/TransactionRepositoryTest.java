package com.bank.infrastructure;

import com.bank.domain.entity.Account;
import com.bank.domain.entity.Transaction;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.infrastructure.persistence.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/clean-db.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("TransactionRepository — Integration Tests")
public class TransactionRepositoryTest {

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
	
	@Autowired TransactionRepository transactionRepository;
	@Autowired AccountRepository accountRepository;
	@Autowired UserRepository userRepository;
	@PersistenceContext EntityManager em;
	
	private User alice;
	private Account aliceAccount;
	private Account bobAccount;
	
	// Transactions de référence
    private Transaction tx1_settled_sepa;      // Virement SEPA réglé — 200 €
    private Transaction tx2_pending_card;      // Paiement carte en attente — 50 €
    private Transaction tx3_settled_card;      // Paiement carte réglé — 75 €
    private Transaction tx4_fraud_suspect;     // Suspicion fraude — 8000 €
    private Transaction tx5_cancelled;         // Annulée — 100 €
    private Transaction tx6_old_settled;       // Ancienne transaction (il y a 3 mois)

    @BeforeEach
    void setUp() {
    	alice = buildUser("alice"+ UUID.randomUUID()+"@bank.com", "alice", "martin");
    	User bob = buildUser("bob"+ UUID.randomUUID()+"@bank.com", "bob","durand");
    	em.persist(alice);
    	em.persist(bob);
    	
    	aliceAccount = buildAccount("FR7630006000011234567890189", AccountType.CURRENT, new BigDecimal("3000.00"), alice);
        bobAccount = buildAccount("FR7630006000015555666677778", AccountType.CURRENT, new BigDecimal("1000.00"), bob);
        em.persist(aliceAccount);
        em.persist(bobAccount);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recent = now.minusHours(2);
        LocalDateTime old = now.minusMonths(3);
        
        tx1_settled_sepa = buildTx("TXN-001", TransactionType.SEPA_TRANSFER, TransactionStatus.SETTLED, new BigDecimal("200.00"), aliceAccount, recent);
        tx1_settled_sepa.setCounterpartIban("FR7630006000015555666677778");
        tx1_settled_sepa.setCounterpartName("Bob Durand");

        tx2_pending_card = buildTx("TXN-002", TransactionType.CARD_PAYMENT, TransactionStatus.PENDING, new BigDecimal("50.00"), aliceAccount, recent);
        tx3_settled_card = buildTx("TXN-003", TransactionType.CARD_PAYMENT, TransactionStatus.SETTLED, new BigDecimal("75.00"), aliceAccount, recent);

        tx4_fraud_suspect = buildTx("TXN-004", TransactionType.SEPA_TRANSFER, TransactionStatus.FRAUD_SUSPECT, new BigDecimal("8000.00"), aliceAccount, recent);
        tx4_fraud_suspect.setFraudScore(new BigDecimal("0.85"));
        
        tx5_cancelled = buildTx("TXN-005", TransactionType.SEPA_TRANSFER, TransactionStatus.CANCELLED, new BigDecimal("100.00"), aliceAccount, recent);
        tx6_old_settled = buildTx("TXN-006", TransactionType.CARD_PAYMENT, TransactionStatus.SETTLED, new BigDecimal("30.00"), aliceAccount, old);
        
    	em.persist(tx1_settled_sepa);
    	em.persist(tx2_pending_card);
    	em.persist(tx3_settled_card);
    	em.persist(tx4_fraud_suspect);
    	em.persist(tx5_cancelled);
    	em.persist(tx6_old_settled);
    	
    	em.flush();

    	// FORCE LES DATES ET L'IBAN PAR SQL NATIVE
    	em.createNativeQuery("UPDATE transactions SET created_at = :dt, updated_at = :dt, counterpart_iban = :iban WHERE id = :id")
    	  .setParameter("dt", recent).setParameter("iban", "FR7630006000015555666677778").setParameter("id", tx1_settled_sepa.getId()).executeUpdate();
    	em.createNativeQuery("UPDATE transactions SET created_at = :dt, updated_at = :dt WHERE id = :id")
    	  .setParameter("dt", recent).setParameter("id", tx2_pending_card.getId()).executeUpdate();
    	em.createNativeQuery("UPDATE transactions SET created_at = :dt, updated_at = :dt WHERE id = :id")
    	  .setParameter("dt", recent).setParameter("id", tx3_settled_card.getId()).executeUpdate();
    	em.createNativeQuery("UPDATE transactions SET created_at = :dt, updated_at = :dt WHERE id = :id")
    	  .setParameter("dt", recent).setParameter("id", tx4_fraud_suspect.getId()).executeUpdate();
    	em.createNativeQuery("UPDATE transactions SET created_at = :dt, updated_at = :dt WHERE id = :id")
    	  .setParameter("dt", recent).setParameter("id", tx5_cancelled.getId()).executeUpdate();
    	em.createNativeQuery("UPDATE transactions SET created_at = :dt, updated_at = :dt WHERE id = :id")
    	  .setParameter("dt", old).setParameter("id", tx6_old_settled.getId()).executeUpdate();

    	em.flush();
    	em.clear(); // Vider le cache pour forcer Hibernate à recharger depuis la DB
    }

    // ─────────────────────────────────────────────────────────
    //  Tests — Recherches de base
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Recherche simple")
    class FindTests {
    	
    	@Test
    	@DisplayName("FindByReference - retourne la transaction correspondante")
    	void findByReference_found() {
    		Optional<Transaction> result = transactionRepository.findByReference("TXN-001");
    		assertThat(result).isPresent();
    		assertThat(result.get().getAmount()).isEqualByComparingTo("200.00");
    		assertThat(result.get().getType()).isEqualTo(TransactionType.SEPA_TRANSFER);
    	}
    	
    	@Test
    	@DisplayName("findByReference - retourne vide pour les resultats non trouvés")
    	void findByReference_notFound() {
    		Optional<Transaction> result = transactionRepository.findByReference("TXN-NOTFOUND");
    		assertThat(result).isEmpty();
    	}
    	
    	@Test
    	@DisplayName("existByReferece - return true")
    	void existByReference_true() {
    		assertThat(transactionRepository.existsByReference("TXN-002")).isTrue();
    	}
    	
    	@Test
    	@DisplayName("existByReference - return false")
    	void existByReference_false() {
    		assertThat(transactionRepository.existsByReference("TXN-NOTEXIST")).isFalse();
    	}
    }
    
    // ─────────────────────────────────────────────────────────
    //  Tests — Relevé de compte
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Relevé de compte")
    class StatementsTests {
    	
    	@Test
    	@DisplayName("findByAccountIdOrderByCreatedAtDesc - retourne toutes les transactions du compte")
    	void findByAccountId_allTransactions() {
    		Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(aliceAccount.getId(), PageRequest.of(0, 20));
    		assertThat(transactions.getTotalElements()).isEqualTo(6);
    		
    		assertThat(transactions.getContent())
                .extracting(Transaction::getCreatedAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    	}
    	
    	@Test
    	@DisplayName("findByAccountIdOrderByCreatedAtDesc - verifier la pagination")
    	void findByAccountId_pagination() {
    		Page<Transaction> page1 = transactionRepository.findByAccountIdOrderByCreatedAtDesc(aliceAccount.getId(), PageRequest.of(0, 2));
    		Page<Transaction> page2 = transactionRepository.findByAccountIdOrderByCreatedAtDesc(aliceAccount.getId(), PageRequest.of(1, 2));
    		assertThat(page1.getTotalPages()).isEqualTo(3);
    		assertThat(page1.getContent()).hasSize(2);
    		assertThat(page2.getContent()).hasSize(2);
    	}
    	
    	@Test
    	@DisplayName("findByAccountIdAndStatus — filtre par statut SETTLED")
    	void findbyAccountIdAndStatus_settled() {
            Page<Transaction> settled = transactionRepository.findByAccountIdAndStatusOrderByCreatedAtDesc(
                        aliceAccount.getId(), TransactionStatus.SETTLED, PageRequest.of(0, 10));
     
            assertThat(settled.getTotalElements()).isEqualTo(3);
            assertThat(settled.getContent())
                .extracting(Transaction::getReference)
                .containsExactlyInAnyOrder("TXN-001", "TXN-003", "TXN-006");
    	}
    	
    	@Test
    	@DisplayName("findByAccountIdAndStatus - filtre par statut FRAUD_SUSPECT")
    	void findByAccountIdAndStatus_fraudSuspect() {
    		Page<Transaction> suspicious = transactionRepository.findByAccountIdAndStatusOrderByCreatedAtDesc(
    				aliceAccount.getId(), TransactionStatus.FRAUD_SUSPECT, PageRequest.of(0, 10));
    		assertThat(suspicious.getTotalElements()).isEqualTo(1);
    		assertThat(suspicious.getContent().get(0).getReference()).isEqualTo("TXN-004");
    	}
    	
    	@Test
    	@DisplayName("findByAccountIdAndType - filtre par type CardPayment") 
    	void findByAccountIdAndType_cardPayment() {
    		Page<Transaction> cardsTx = transactionRepository.findByAccountIdAndTypeOrderByCreatedAtDesc(
    				aliceAccount.getId(), TransactionType.CARD_PAYMENT, PageRequest.of(0, 10));
    		assertThat(cardsTx.getTotalElements()).isEqualTo(3);
    	}
    	
    	@Test
    	@DisplayName("findByAccountIdAndPeriod - transactions dans la période")
    	void findByAccountIdAndPeriods_filtered() {
    		LocalDateTime from = LocalDateTime.now().minusMonths(1);
    		LocalDateTime to = LocalDateTime.now();
    		
    		Page<Transaction> recent = transactionRepository.findByAccountIdAndPeriod(aliceAccount.getId(), from, to, PageRequest.of(0,10));
    		assertThat(recent.getTotalElements()).isEqualTo(5);
    		assertThat(recent.getContent()).extracting(Transaction::getReference).doesNotContain("TXN-006");
    	}
    	
        @Test
        @DisplayName("findForStatement — charge les transactions avec le compte en JOIN FETCH")
        void findForStatement_loadsAccount() {
        	LocalDateTime from = LocalDateTime.now().minusMonths(1);
    		LocalDateTime to = LocalDateTime.now();
            List<Transaction> statement = transactionRepository.findForStatement(aliceAccount.getId(), from, to);
     
            assertThat(statement).hasSize(5);
            statement.forEach(tx ->
                assertThat(tx.getAccount().getIban()).isEqualTo("FR7630006000011234567890189")
            );
        }
    }
    
    // ─────────────────────────────────────────────────────────
    //  Tests — Gestion des statuts
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Gestion des status")
    class StatusTests {
    	
    	@Test
    	@DisplayName("updateStatus — passe PENDING en PROCESSING et retourne 1")
    	void updateStatus_pendingToProcessing() {
    		int updated = transactionRepository.updateStatus(tx2_pending_card.getId(), TransactionStatus.PROCESSING, LocalDateTime.now());
    		assertThat(updated).isEqualTo(1);
    		
    		em.clear();
    		Transaction reloaded = transactionRepository.findById(tx2_pending_card.getId()).orElseThrow();
    		assertThat(reloaded.getStatus()).isEqualByComparingTo(TransactionStatus.PROCESSING);
    	}
    	
    	@Test
    	@DisplayName("updateStatus — retourne 0 pour ID inexistant")
    	void updateStatus_idNotFound() {
    		int updated = transactionRepository.updateStatus(UUID.randomUUID(), TransactionStatus.PROCESSING, LocalDateTime.now());
    		assertThat(updated).isZero();
    	}
    	
        @Test
        @DisplayName("settle — règle une transaction APPROVED et enregistre la date")
        void settle_approvedTransaction() {
        	transactionRepository.updateStatus(tx2_pending_card.getId(), TransactionStatus.APPROVED, LocalDateTime.now());
        	em.clear();
        	
        	LocalDateTime settlementTime = LocalDateTime.now();
        	int updated = transactionRepository.settle(tx2_pending_card.getId(), settlementTime);
        	assertThat(updated).isEqualTo(1);
        	
        	em.clear();
        	Transaction reloaded = transactionRepository.findById(tx2_pending_card.getId()).orElseThrow();
        	assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        	assertThat(reloaded.getSettledAt()).isNotNull();
        }
        
        @Test
        @DisplayName("settle — ne règle pas une transaction non-APPROVED (garde d'intégrité)")
        void settle_nonApprovedTransaction_notUpdated() {
        	int updated = transactionRepository.settle(tx2_pending_card.getId(), LocalDateTime.now());
        	assertThat(updated).isZero();
        	
        	em.clear();
        	Transaction reloaded = transactionRepository.findById(tx2_pending_card.getId()).orElseThrow();
        	assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.PENDING);
        }
        
        @Test
        @DisplayName("flagFraud — enregistre le score et passe en FRAUD_SUSPECT")
        void flagFraud_updatesScoreAndStatus() {
            BigDecimal score = new BigDecimal("0.92");
            int updated = transactionRepository.flagFraud(tx2_pending_card.getId(), score, LocalDateTime.now());
            assertThat(updated).isEqualTo(1);
 
            em.clear();
            Transaction reloaded = transactionRepository.findById(tx2_pending_card.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.FRAUD_SUSPECT);
            assertThat(reloaded.getFraudScore()).isEqualByComparingTo(score);
        }
        
        @Test
        @DisplayName("findStuckTransactions — détecte les transactions bloquées depuis trop longtemps")
        void findStuckTransactions_detected() {
            em.createNativeQuery("UPDATE transactions SET created_at = :old WHERE id = :id")
              .setParameter("old", LocalDateTime.now().minusMinutes(35))
              .setParameter("id", tx2_pending_card.getId())
              .executeUpdate();
            em.flush();
            em.clear();
 
            LocalDateTime timeout = LocalDateTime.now().minusMinutes(30);
            List<Transaction> stuck = transactionRepository.findStuckTransactions(timeout);
 
            assertThat(stuck).extracting(Transaction::getReference).contains("TXN-002");
        }
 
        @Test
        @DisplayName("findByStatusOrderByCreatedAtAsc — liste les FRAUD_SUSPECT pour compliance")
        void findByStatus_fraudSuspect() {
        	Page<Transaction> suspects = transactionRepository.findByStatusOrderByCreatedAtAsc(TransactionStatus.FRAUD_SUSPECT, PageRequest.of(0, 10));
        	assertThat(suspects.getTotalElements()).isEqualTo(1);
        	assertThat(suspects.getContent().get(0).getReference()).isEqualTo("TXN-004");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Tests — Anti-fraude (velocity + montants)
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Détection de fraude — velocity check")
    class FraudDetectionTests {
    	
        @Test
        @DisplayName("countRecentByAccount — compte les transactions actives dans la fenêtre")
        void countRecent_activeTransactions() {
            LocalDateTime since = LocalDateTime.now().minusHours(4);
            long count = transactionRepository.countRecentByAccount(aliceAccount.getId(), since);
 
            assertThat(count).isEqualTo(4L);
        }
 
        @Test
        @DisplayName("countRecentByAccount — retourne 0 pour compte sans transaction récente")
        void countRecent_noRecentTransactions() {
            LocalDateTime since = LocalDateTime.now().minusMinutes(1);
            long count = transactionRepository.countRecentByAccount(bobAccount.getId(), since);
 
            assertThat(count).isZero();
        }
        
        @Test
        @DisplayName("sumDebitedAmountSince — cumule les montants des débits récents")
        void sumDebited_recentTransactions() {
            LocalDateTime since = LocalDateTime.now().minusHours(4);
            BigDecimal total = transactionRepository.sumDebitedAmountSince(aliceAccount.getId(), since);
 
            assertThat(total).isNotNull();
            assertThat(total).isGreaterThan(BigDecimal.ZERO);
        }
        
        @Test
        @DisplayName("sumDebitedAmountSince — retourne 0 pour compte sans débit récent")
        void sumDebited_noDebit() {
            LocalDateTime since = LocalDateTime.now().minusMinutes(1);
            BigDecimal total = transactionRepository.sumDebitedAmountSince(bobAccount.getId(), since);
 
            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        }
        
        @Test
        @DisplayName("findRecentByCounterpartIban — détecte virements répétés vers même IBAN")
        void findRecentByCounterpart_detected() {
            // 1. On prépare une date fixe pour "maintenant" dans le cadre de ce test
            LocalDateTime testNow = LocalDateTime.now();
            LocalDateTime dynamicSince = testNow.minusHours(1);
            
            // 2. On met à jour TXN-001 (générée au setUp) pour s'assurer qu'elle entre dans le créneau (-30 min)
            LocalDateTime tx1Time = testNow.minusMinutes(30);
            em.createNativeQuery("UPDATE transactions SET created_at = :dt, counterpart_iban = :iban WHERE reference = 'TXN-001'")
              .setParameter("dt", tx1Time)
              .setParameter("iban", "FR7630006000015555666677778")
              .executeUpdate();

            // 3. On crée TXN-007 très proche (-10 min)
            Transaction tx7 = buildTx("TXN-007", TransactionType.SEPA_TRANSFER,
                TransactionStatus.PENDING, new BigDecimal("300.00"),
                aliceAccount, testNow.minusMinutes(10));
            tx7.setCounterpartIban("FR7630006000015555666677778");
            em.persist(tx7);
            em.flush();
            
            // On force aussi la date de TXN-007 en DB pour contourner les triggers/hooks d'audit
            em.createNativeQuery("UPDATE transactions SET created_at = :dt, counterpart_iban = :iban WHERE id = :id")
              .setParameter("dt", testNow.minusMinutes(10))
              .setParameter("iban", "FR7630006000015555666677778")
              .setParameter("id", tx7.getId())
              .executeUpdate();
            
            em.flush();
            em.clear(); // On vide le cache pour forcer la relecture SQL globale des deux entités

            // 4. Exécution de la requête avec la même référence temporelle
            List<Transaction> repeated = transactionRepository.findRecentByCounterpartIban(
                aliceAccount.getId(), "FR7630006000015555666677778", dynamicSince);
 
            // 5. Assertions
            assertThat(repeated).hasSize(2);
            assertThat(repeated)
                .extracting(Transaction::getReference)
                .containsExactlyInAnyOrder("TXN-001", "TXN-007");
        }
 
        @Test
        @DisplayName("findRecentByCounterpartIban — retourne vide si IBAN différent")
        void findRecentByCounterpart_differentIban() {
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            List<Transaction> result = transactionRepository.findRecentByCounterpartIban(
                aliceAccount.getId(), "DE89370400440532013000", since);
 
            assertThat(result).isEmpty();
        }
    }
    
    // ─────────────────────────────────────────────────────────
    //  Tests — Reporting et agrégations
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Reporting et agrégations")
    class ReportingTests {
 
        @Test
        @DisplayName("sumSettledByTypeAndPeriod — cumule les montants réglés par type")
        void sumSettled_cardPayments() {
            LocalDateTime from = LocalDateTime.now().minusMonths(1);
            LocalDateTime to   = LocalDateTime.now();
 
            BigDecimal total = transactionRepository.sumSettledByTypeAndPeriod(
                aliceAccount.getId(), List.of(TransactionType.CARD_PAYMENT), from, to
            );
 
            assertThat(total).isEqualByComparingTo("75.00");
        }
 
        @Test
        @DisplayName("sumSettledByTypeAndPeriod — inclut les vieux réglés si période élargie")
        void sumSettled_allPeriod() {
            LocalDateTime from = LocalDateTime.now().minusYears(1);
            LocalDateTime to   = LocalDateTime.now();
 
            BigDecimal total = transactionRepository.sumSettledByTypeAndPeriod(
                aliceAccount.getId(), List.of(TransactionType.CARD_PAYMENT), from, to
            );
 
            assertThat(total).isEqualByComparingTo("105.00");
        }
 
        @Test
        @DisplayName("volumeByTypeAndPeriod — retourne les statistiques par type")
        void volumeByType_returnsStats() {
            LocalDateTime from = LocalDateTime.now().minusMonths(1);
            LocalDateTime to   = LocalDateTime.now();
 
            List<Object[]> stats = transactionRepository.volumeByTypeAndPeriod(from, to);
 
            assertThat(stats).isNotEmpty();
            boolean hasCardPayment = stats.stream().anyMatch(row ->
                TransactionType.CARD_PAYMENT.name().equals(row[0].toString()));
            assertThat(hasCardPayment).isTrue();
        }
 
        @Test
        @DisplayName("findAmlCandidates — retourne les transactions >= seuil AML")
        void findAmlCandidates_aboveThreshold() {
            LocalDateTime from = LocalDateTime.now().minusMonths(1);
            LocalDateTime to   = LocalDateTime.now();
 
            Transaction bigTx = buildTx("TXN-BIG", TransactionType.SEPA_TRANSFER,
                TransactionStatus.SETTLED, new BigDecimal("15000.00"), aliceAccount, LocalDateTime.now().minusMinutes(5));
            em.persist(bigTx);
            em.flush();
            
            em.createNativeQuery("UPDATE transactions SET created_at = :dt WHERE id = :id")
              .setParameter("dt", LocalDateTime.now().minusMinutes(5))
              .setParameter("id", bigTx.getId()).executeUpdate();
            em.flush();
            em.clear();
 
            Page<Transaction> amlCandidates = transactionRepository.findAmlCandidates(
                new BigDecimal("10000.00"), from, to, PageRequest.of(0, 10));
 
            assertThat(amlCandidates.getTotalElements()).isEqualTo(1);
            assertThat(amlCandidates.getContent().get(0).getReference()).isEqualTo("TXN-BIG");
        }
 
        @Test
        @DisplayName("findAmlCandidates — ne retourne pas les transactions en dessous du seuil")
        void findAmlCandidates_belowThreshold() {
            LocalDateTime from = LocalDateTime.now().minusMonths(1);
            LocalDateTime to   = LocalDateTime.now();
 
            Page<Transaction> amlCandidates = transactionRepository.findAmlCandidates(
                new BigDecimal("10000.00"), from, to, PageRequest.of(0, 10));
 
            assertThat(amlCandidates.getTotalElements()).isZero();
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Tests — CRUD de base
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("CRUD de base")
    class CrudTests {
 
        @Test
        @DisplayName("save — persiste une nouvelle transaction avec les bons champs")
        void save_newTransaction() {
            Transaction tx = Transaction.create(
                "TXN-NEW-001", TransactionType.CARD_PAYMENT,
                new BigDecimal("123.45"), CurrencyCode.EUR,
                aliceAccount, "Achat Amazon"
            );
 
            Transaction saved = transactionRepository.save(tx);
 
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getVersion()).isZero();
            assertThat(saved.getFees()).isEqualByComparingTo(BigDecimal.ZERO);
        }
 
        @Test
        @DisplayName("findById — retourne la transaction persistée")
        void findById_found() {
            Optional<Transaction> found = transactionRepository.findById(tx1_settled_sepa.getId());
 
            assertThat(found).isPresent();
            assertThat(found.get().getReference()).isEqualTo("TXN-001");
        }
 
        @Test
        @DisplayName("totalAmount — calcule montant + frais")
        void totalAmount_withFees() {
            Transaction tx = Transaction.create(
                "TXN-FEES", TransactionType.INTERNATIONAL_TRANSFER,
                new BigDecimal("500.00"), CurrencyCode.EUR,
                aliceAccount, "Virement international"
            );
            tx.setFees(new BigDecimal("12.50"));
            Transaction saved = transactionRepository.save(tx);
            em.flush();
            em.clear();
 
            Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.totalAmount()).isEqualByComparingTo("512.50");
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
	
	private Account buildAccount(String iban, AccountType type, BigDecimal balance, User owner) {
		Account account = Account.create(iban, "ACC-" + UUID.randomUUID().toString().substring(0, 8), type, CurrencyCode.EUR, owner);
		account.setBalance(balance);
		return account;
	}
	
	private Transaction buildTx(String reference, TransactionType type, TransactionStatus status, BigDecimal amount, Account account, LocalDateTime createdAt) {
		Transaction tx = Transaction.create(reference, type, amount, CurrencyCode.EUR, account, reference);
		tx.setCreatedAt(createdAt);
		tx.setStatus(status);
		tx.setUpdatedAt(createdAt);
		return tx;
	}
}