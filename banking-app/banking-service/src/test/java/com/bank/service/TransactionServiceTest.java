package com.bank.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bank.common.exception.InsufficientFundsException;
import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.Card;
import com.bank.domain.entity.Transaction;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.domain.event.TransactionCreatedEvent;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.api.FraudDetectionService;
import com.bank.service.impl.TransactionServiceImpl;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

	@Mock
	private AccountRepository accountRepository;
	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private AuditLogRepository auditLogRepository;
	@Mock
	private TransactionEventProducer eventProducer;
	@Mock
	private FraudDetectionService fraudDetectionService;
	
	@InjectMocks
	private TransactionServiceImpl transactionService;
	
	private User alice;
	private User bob;
	
	private Account aliceAccount;
	private Account bobAccount;
	
	@BeforeEach()
	void setUp() {
		alice = buildUser("alice@bank.fr", "alice", "alice");
		bob = buildUser("bob@bank.fr","bob", "bob");
		
		aliceAccount = buildAccount("FR7630006000011234567890189", "ACC-001", AccountType.CURRENT, BigDecimal.valueOf(2000), alice);
		bobAccount = buildAccount("FR7630006000011234567890190", "ACC-002", AccountType.CURRENT, BigDecimal.valueOf(500), bob);
	}
	
	@Nested
	@DisplayName("Virements SEPA")
	class SepaTransfert{
		
		@Test
		@DisplayName("initiate - debite le compte source et crée la transaction")
		void initiate_SepaTransfer_sucess() {
			BigDecimal amount = BigDecimal.valueOf(150);
			
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));			
			given(fraudDetectionService.calculateRiskScore(any())).willReturn(BigDecimal.valueOf(0.5));
			given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
					Transaction tx = inv.getArgument(0);
					tx.setId(UUID.randomUUID());
					return tx;
				}
			);
			
			Transaction result = transactionService.initiateSepaTransfer(aliceAccount.getId(), alice.getId(), "FR7630006000015555666677778", "Bob bob", amount, CurrencyCode.EUR, "Remboursement diner", null, false);
			
			assertThat(result).isNotNull();
			assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
			assertThat(result.getAmount()).isEqualByComparingTo(amount);
			assertThat(result.getType()).isEqualTo(TransactionType.SEPA_TRANSFER);
			
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1850.00"));
			
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));
		}
		
		@Test
		@DisplayName("initiate - leve InsufficientFundsException si solde insuffisant")
		void initiateSepaTransfer_insufficientFunds() {
			BigDecimal amount = BigDecimal.valueOf(5000);
            given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
            
            assertThatThrownBy(() -> transactionService.initiateSepaTransfer(
                    aliceAccount.getId(), alice.getId(),
                    "FR7630006000015555666677778", "Bob bob",
                    amount, CurrencyCode.EUR, "Test", null, false
                )).isInstanceOf(InsufficientFundsException.class).hasMessageContaining("Fonds insuffisants");
            
            then(transactionRepository).should(never()).save(any());
            then(eventProducer).should(never()).publishTransactionCreated(any());
		}

		@Test
		@DisplayName("initiate - leve UnauthorizedOperationException account blocked")
		void initiateSepaTransfer_accountBlocked() {
			aliceAccount.setStatus(AccountStatus.BLOCKED);
			
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			assertThatThrownBy(() -> transactionService.initiateSepaTransfer(
	                    aliceAccount.getId(), alice.getId(),
	                    "FR7630006000015555666677778", "Bob bob",
	                    BigDecimal.valueOf(100), CurrencyCode.EUR, "Test", null, false
	                )).isInstanceOf(UnauthorizedOperationException.class);
	            
			then(transactionRepository).should(never()).save(any());
		}

        @Test
        @DisplayName("initiate — lève UnauthorizedOperationException si l'appelant n'est pas propriétaire")
        void initiateSepaTransfer_notOwner() {
        	UUID notOwnerId = UUID.randomUUID();
            given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
	        given(accountRepository.existsByIdAndOwnerId(aliceAccount.getId(), notOwnerId)).willReturn(false);
            
            assertThatThrownBy(() ->
	            transactionService.initiateSepaTransfer(
		                aliceAccount.getId(), notOwnerId,
		                "FR7630006000015555666677778", "Bob",
		                new BigDecimal("100.00"), CurrencyCode.EUR, "Test", null, false
		            )
	        ).isInstanceOf(UnauthorizedOperationException.class);
            
            then(transactionRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("initiate — déclenche alerte fraude si score > seuil")
        void initiateSepaTransfer_highFraudScore() {
            BigDecimal amount = new BigDecimal("9500.00");
            aliceAccount.setBalance(BigDecimal.valueOf(10000));
            given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
            given(fraudDetectionService.calculateRiskScore(any())).willReturn(new BigDecimal("0.85"));
            given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
					Transaction tx = inv.getArgument(0);
					tx.setId(UUID.randomUUID());
					return tx;
				}
            );
 
            transactionService.initiateSepaTransfer(
                aliceAccount.getId(), alice.getId(),
                "FR7630006000015555666677778", "Bob Durand",
                amount, CurrencyCode.EUR, "Virement", null, false
            );
 
            then(fraudDetectionService).should(times(1)).calculateRiskScore(any());
            then(fraudDetectionService).should(times(1)).analyze(any());
        }
	}
	
	@Nested
	class InternalTransfer {
        @Test
        @DisplayName("initiate — débite source et crédite destination")
        void initiateInternalTransfer_success(){
        	BigDecimal amount = BigDecimal.valueOf(300);
        	given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
        	given(accountRepository.findByIdWithLock(bobAccount.getId())).willReturn(Optional.of(bobAccount));
        	given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
					Transaction tx = inv.getArgument(0);
					tx.setId(UUID.randomUUID());
					return tx;
				}
			);
        	
        	Transaction result = transactionService.initiateInternalTransfer(aliceAccount.getId(),bobAccount.getId(), alice.getId(), amount, CurrencyCode.EUR, "Rembourssement");
        	
        	assertThat(result).isNotNull();
        	assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1700));
        	assertThat(bobAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(800));
        	
        	then(transactionRepository).should().save(any());
        }
        
        @Test
        @DisplayName("Leve une exception si source == dest")
        void initiateInternalTransfert_sameAccount() {
            assertThatThrownBy(() ->
	            transactionService.initiateInternalTransfer(
	                aliceAccount.getId(), aliceAccount.getId(),
	                alice.getId(), new BigDecimal("100.00"),
	                CurrencyCode.EUR, "Test"
	            )
	        ).isInstanceOf(IllegalArgumentException.class)
	         .hasMessageContaining("identiques");
        }
	}
	
	@Nested
	@DisplayName("Depôt especes")
	class CashDeposit {
		
		@Test
		@DisplayName("deposit - credite le compte")
		void cashDeposit_success() {
			BigDecimal deposit = BigDecimal.valueOf(500);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.save(any())).willAnswer(inv -> {
					Transaction tx = inv.getArgument(0);
					tx.setId(UUID.randomUUID());
					return tx;
				}
			);
			
			Transaction result = transactionService.cashDeposit(aliceAccount.getId(), alice.getId(), deposit, CurrencyCode.EUR);
			
			assertThat(result.getType()).isEqualTo(TransactionType.CASH_DEPOSIT);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(2500));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));
		}

        @Test
        @DisplayName("deposit — lève exception si montant négatif ou nul")
        void cashDeposit_invalidAmount() {
            assertThatThrownBy(() ->
                transactionService.cashDeposit(
                    aliceAccount.getId(), alice.getId(),
                    BigDecimal.ZERO, CurrencyCode.EUR
                )
            ).isInstanceOf(IllegalArgumentException.class);
 
            assertThatThrownBy(() ->
                transactionService.cashDeposit(
                    aliceAccount.getId(), alice.getId(),
                    new BigDecimal("-50.00"), CurrencyCode.EUR
                )
            ).isInstanceOf(IllegalArgumentException.class);
        }
	}
	
	@Nested
	@DisplayName("cashWithdrawal retrait espece")
	class CashWithDrawal{
		
		@Test
		@DisplayName("credite le compte success")
		void cashWithDrawl_success() {
			BigDecimal credit = BigDecimal.valueOf(500);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.save(any())).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
				}
			);
			
			Transaction result = transactionService.cashWithdrawal(aliceAccount.getId(), UUID.randomUUID(), alice.getId(), credit, CurrencyCode.EUR);
			
			assertThat(result.getType()).isEqualTo(TransactionType.CASH_WITHDRAWAL);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));	
		}

		@Test
		@DisplayName("credite le compte insufficient fund")
		void cashWithDrawl_insuficientFund() {
			BigDecimal credit = BigDecimal.valueOf(5000);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			
			assertThatThrownBy(() -> transactionService.cashWithdrawal(aliceAccount.getId(), UUID.randomUUID(), alice.getId(), credit, CurrencyCode.EUR)).isInstanceOfAny(InsufficientFundsException.class).hasMessageContaining("Fonds insuffisants");
            
            then(transactionRepository).should(never()).save(any());
            then(eventProducer).should(never()).publishTransactionCreated(any());
		}
	}
	
	@Nested
	@DisplayName("international transfer")
	class InternationalTransfer {
		@Test
        @DisplayName("initiate — débite source et crédite destination")
        void initiateInternationalTransfer_success(){
        	BigDecimal amount = BigDecimal.valueOf(300);
        	given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
        	given(fraudDetectionService.calculateRiskScore(any())).willReturn(BigDecimal.valueOf(0.5));
        	given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
					Transaction tx = inv.getArgument(0);
					tx.setId(UUID.randomUUID());
					return tx;
				}
			);
        	
        	Transaction result = transactionService.initiateInternationalTransfer(aliceAccount.getId(), alice.getId(), "FRTOTO","TOTO", "TOTO", amount, CurrencyCode.EUR, "remboursement");
        	
        	assertThat(result).isNotNull();
        	assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1700));
        	
        	then(transactionRepository).should().save(any());
        	then(eventProducer).should(times(1)).publishTransactionCreated(any());
        }

		@Test
		@DisplayName("initiate - leve InsufficientFundsException si solde insuffisant")
		void initiateInternationalTransfer_insufficientFunds() {
			BigDecimal amount = BigDecimal.valueOf(5000);
            given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
            
            assertThatThrownBy(() -> transactionService.initiateInternationalTransfer(aliceAccount.getId(), alice.getId(), "FRTOTO","TOTO", "TOTO", amount, CurrencyCode.EUR, "remboursement")).isInstanceOf(InsufficientFundsException.class).hasMessageContaining("Fonds insuffisants");
            
            then(transactionRepository).should(never()).save(any());
            then(eventProducer).should(never()).publishTransactionCreated(any());
		}

		@Test
		@DisplayName("initiate - leve UnauthorizedOperationException account blocked")
		void initiateInternationalTransfer_accountBlocked() {
			aliceAccount.setStatus(AccountStatus.BLOCKED);
			
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			assertThatThrownBy(() -> transactionService.initiateInternationalTransfer(
					 aliceAccount.getId(), alice.getId(), "FRTOTO","TOTO", "TOTO", BigDecimal.valueOf(50), CurrencyCode.EUR, "remboursement")).isInstanceOf(UnauthorizedOperationException.class);
	            
			then(transactionRepository).should(never()).save(any());
		}

        @Test
        @DisplayName("initiate — lève UnauthorizedOperationException si l'appelant n'est pas propriétaire")
        void initiateInternationalTransfer_notOwner() {
        	UUID notOwnerId = UUID.randomUUID();
            given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
	        given(accountRepository.existsByIdAndOwnerId(aliceAccount.getId(), notOwnerId)).willReturn(false);
            
            // CORRECTION : Passage de notOwnerId au lieu de alice.getId() pour forcer le cas de test
            assertThatThrownBy(() ->
	            transactionService.initiateInternationalTransfer(
						 aliceAccount.getId(), notOwnerId, "FRTOTO","TOTO", "TOTO", BigDecimal.valueOf(50), CurrencyCode.EUR, "remboursement")
            ).isInstanceOf(UnauthorizedOperationException.class);
			            
            then(transactionRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("initiate — déclenche alerte fraude si score > seuil")
        void initiateInternationalTransfer_highFraudScore() {
            BigDecimal amount = new BigDecimal("9500.00");
            aliceAccount.setBalance(BigDecimal.valueOf(10000));
            given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
            given(fraudDetectionService.calculateRiskScore(any())).willReturn(new BigDecimal("0.85"));
            given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
					Transaction tx = inv.getArgument(0);
					tx.setId(UUID.randomUUID());
					return tx;
				}
            );
 
            transactionService.initiateInternationalTransfer(
					 aliceAccount.getId(), alice.getId(), "FRTOTO","TOTO", "TOTO", amount, CurrencyCode.EUR, "remboursement");
 
            then(fraudDetectionService).should(times(1)).calculateRiskScore(any());
            then(fraudDetectionService).should(times(1)).analyze(any());
        }
	}

	@Nested
	@DisplayName("Card payment")
	class CardPayment {
		
		@Test
		@DisplayName("Card payment success")
		void cardPayment_success() {
			BigDecimal credit = BigDecimal.valueOf(500);
			
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			// CORRECTION : Mock de la détection de fraude pour éviter le NullPointerException
			given(fraudDetectionService.calculateRiskScore(any())).willReturn(BigDecimal.valueOf(0.1));
			given(transactionRepository.save(any())).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
				}
			);
			
			Transaction result = transactionService.cardPayment(aliceAccount.getId(), UUID.randomUUID(), alice.getId(), credit, CurrencyCode.EUR, "merchandName", "achat");
			
			assertThat(result.getType()).isEqualTo(TransactionType.CARD_PAYMENT);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));	
		}

		@Test
		@DisplayName("credite le compte insufficient fund")
		void cardPayment_insuficientFund() {
			BigDecimal credit = BigDecimal.valueOf(5000);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			
			assertThatThrownBy(() -> transactionService.cardPayment(aliceAccount.getId(), UUID.randomUUID(), alice.getId(), credit, CurrencyCode.EUR, "merchandName", "achat")).isInstanceOfAny(InsufficientFundsException.class).hasMessageContaining("Fonds insuffisants");
            
            then(transactionRepository).should(never()).save(any());
            then(eventProducer).should(never()).publishTransactionCreated(any());
		}
	}

	@Nested
	@DisplayName("Card Refund")
	class CardRefund {
		
		private Transaction originalPayment;
		
		@BeforeEach
		void setUp() { // Renommé correctement en 'setUp'
			originalPayment = buildTransaction(aliceAccount, TransactionType.CARD_PAYMENT, TransactionStatus.SETTLED, BigDecimal.valueOf(100));
			originalPayment.setCounterpartName("Amazon");
			originalPayment.setReference("TXN-ORIG-123");
		}
		
		@Test
		@DisplayName("card refund success")
		void cardRefund_success() {
			BigDecimal refund = BigDecimal.valueOf(40);
			UUID originalTxID = originalPayment.getId();
			
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.findById(originalTxID)).willReturn(Optional.of(originalPayment));
			// CORRECTION : Stub local nécessaire pour générer l'ID lors du save() du remboursement
			given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
			});
			
			Transaction result = transactionService.cardRefund(aliceAccount.getId(), originalTxID, alice.getId(), refund, CurrencyCode.EUR, "trop perçu");
			
			assertThat(result).isNotNull();
			assertThat(result.getType()).isEqualTo(TransactionType.CARD_REFUND);
			assertThat(result.getAmount()).isEqualByComparingTo(refund);
			// CORRECTION : Le statut attendu est SETTLED d'après le code métier, pas "Amazon"
			assertThat(result.getStatus()).isEqualTo(TransactionStatus.SETTLED); 
			
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(2040));
			
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));
		}

		@Test
		@DisplayName("card refund invalid amount")
		void cardRefund_invalidAmount() {
			assertThatThrownBy(() -> transactionService.cardRefund(aliceAccount.getId(), originalPayment.getId(), alice.getId(), BigDecimal.ZERO, CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() ->
				transactionService.cardRefund(
					aliceAccount.getId(), originalPayment.getId(), alice.getId(),
					BigDecimal.valueOf(-10.00), CurrencyCode.EUR, "Test"
				)
			).isInstanceOf(IllegalArgumentException.class);
			then(transactionRepository).should(never()).findById(any());
		}

		@Test
		@DisplayName("card invalid original id")
		void cardRefund_invalidOrigina() {
			UUID originalTxID = UUID.randomUUID();
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.findById(originalTxID)).willReturn(Optional.empty());
			
			// CORRECTION : Passage de originalTxID au lieu de originalPayment.getId()
			assertThatThrownBy(() ->
				transactionService.cardRefund(
					aliceAccount.getId(), originalTxID, alice.getId(),
					BigDecimal.valueOf(10.00), CurrencyCode.EUR, "Test"
				)
			).isInstanceOf(IllegalArgumentException.class)
			 .hasMessageContaining("Transaction d'origine introuvable");
			
			then(transactionRepository).should(never()).save(any());
		}

		@Test
		@DisplayName("invalid transaction type")
		void cardRefund_invalidTransactionType() {
			Transaction tx = buildTransaction(aliceAccount, TransactionType.SEPA_TRANSFER, TransactionStatus.SETTLED, BigDecimal.valueOf(100));
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.findById(tx.getId())).willReturn(Optional.of(tx));
			
			assertThatThrownBy(() ->
				transactionService.cardRefund(
					aliceAccount.getId(), tx.getId(), alice.getId(),
					BigDecimal.valueOf(50), CurrencyCode.EUR, "Test"
				)
			).isInstanceOf(IllegalArgumentException.class)
			 .hasMessageContaining("La transaction d'origine n'est pas un paiement carte");
		}
		
		@Test
		@DisplayName("card refund amount excelled")
		void cardRefund_amountExceeded() {
			BigDecimal exceededAmount = BigDecimal.valueOf(200);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.findById(originalPayment.getId())).willReturn(Optional.of(originalPayment));
			
			assertThatThrownBy(() ->
				transactionService.cardRefund(
					aliceAccount.getId(), originalPayment.getId(), alice.getId(),
					exceededAmount, CurrencyCode.EUR, "Test"
				)
			).isInstanceOf(IllegalArgumentException.class)
			 .hasMessageContaining("Le montant remboursé ne peut pas dépasser le montant d'origine");
		}
	}

	@Nested
	@DisplayName("Direct débit")
	class DirectDebit{
		
		@Test
		@DisplayName("direct debit success")
		void directDebit_success() {
			
			BigDecimal debit = BigDecimal.valueOf(500);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.save(any())).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
				}
			);
			
			Transaction result = transactionService.directDebit(aliceAccount.getId(), UUID.randomUUID().toString(), "test","FRTEST", debit, CurrencyCode.EUR, "direct debit");
			
			assertThat(result).isNotNull();
			assertThat(result.getType()).isEqualTo(TransactionType.DIRECT_DEBIT);
			assertThat(result.getAmount()).isEqualByComparingTo(debit);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
			
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));
		}
	
		@Test
		@DisplayName("insufficient fund")
		void directDebit_insuficientFund() {
		
				BigDecimal debit = BigDecimal.valueOf(5000);
				given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
				
				assertThatThrownBy(() -> transactionService.directDebit(aliceAccount.getId(), UUID.randomUUID().toString(), "test","FRTEST", debit, CurrencyCode.EUR, "direct debit")).isInstanceOfAny(InsufficientFundsException.class).hasMessageContaining("Fonds insuffisants");
	            
	            then(transactionRepository).should(never()).save(any());
	            then(eventProducer).should(never()).publishTransactionCreated(any());
			
		}
		
	}
	
	@Nested
	@DisplayName("Direct debit refund")
	class DirectDebitRefund{
		
		private Transaction originalTx;
		
		@BeforeEach
		void setUp() {
			
			originalTx = buildTransaction(aliceAccount, TransactionType.DIRECT_DEBIT, TransactionStatus.SETTLED, BigDecimal.valueOf(100));
			originalTx.setReference("TXN-ORIG-123");
			originalTx.setCreatedAt(LocalDateTime.now());
		}
		
		@Test
		@DisplayName("Direct debit refund success")
		void directDebitRefund_success() {
			BigDecimal refund = BigDecimal.valueOf(100);
			UUID originalTxID = originalTx.getId();
			
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.findById(originalTxID)).willReturn(Optional.of(originalTx));
			given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
			});
			
			Transaction result = transactionService.directDebitRefund(aliceAccount.getId(), originalTxID, alice.getId());
			
			assertThat(result).isNotNull();
			assertThat(result.getAmount()).isEqualByComparingTo(refund);
			assertThat(result.getType()).isEqualTo(TransactionType.DIRECT_DEBIT_REFUND);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(2100));
			
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));
			
		}
		
		@Test
		@DisplayName("direct debit transaction type invalid")
		void directDebitRefund_invalidType() {
			
			Transaction tx = buildTransaction(aliceAccount, TransactionType.SEPA_TRANSFER, TransactionStatus.SETTLED, BigDecimal.valueOf(100));
			
			given(transactionRepository.findById(tx.getId())).willReturn(Optional.of(tx));
			
			
			assertThatThrownBy(() -> transactionService.directDebitRefund(aliceAccount.getId(), tx.getId(), alice.getId())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("La transaction d'origine n'est pas un prélèvement SEPA");
			
		    then(transactionRepository).should(never()).save(any());
            then(eventProducer).should(never()).publishTransactionCreated(any());
			
		}
		@Test
		@DisplayName("should respect delay")
		void directDebitRefund_invalidDelay()
		{
			originalTx.setCreatedAt(LocalDateTime.now().minusWeeks(10));
			given(transactionRepository.findById(originalTx.getId())).willReturn(Optional.of(originalTx));
			
			assertThatThrownBy(()-> transactionService.directDebitRefund(aliceAccount.getId(),originalTx.getId(),alice.getId())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Le délai légal de remboursement (8 semaines) est dépassé");
			
			
		}
		
	}
	
	@Nested
	@DisplayName("credit interest")
	class CreditInterest{
		
		@Test
		@DisplayName("credit interest success")
		void creditInterest_success() {
			
			BigDecimal amount = BigDecimal.valueOf(100);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.save(any())).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
				}
			);
			
			Transaction result = transactionService.creditInterest(aliceAccount.getId(), amount, CurrencyCode.EUR, "test");
			
			assertThat(result).isNotNull();
			assertThat(result.getType()).isEqualTo(TransactionType.INTEREST_CREDIT);
			assertThat(result.getAmount()).isEqualByComparingTo(amount);
			assertThat(result.getStatus()).isEqualTo(TransactionStatus.SETTLED);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(2100));
			
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
		}
		@Test
		@DisplayName("creditInterest invalid amount")
		void creditInterest_invalidAmount() {
			assertThatThrownBy(() -> transactionService.creditInterest(aliceAccount.getId(), BigDecimal.ZERO, CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> transactionService.creditInterest(aliceAccount.getId(), BigDecimal.valueOf(-10), CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			
		}	
	}
	
	@Nested
	@DisplayName("debit interest")
	class DebitInterest{
		
		@Test
		@DisplayName("debit interest success")
		void debitInterest_success() {
			
			BigDecimal amount = BigDecimal.valueOf(10);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.save(any())).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
				}
			);

			Transaction result = transactionService.debitInterest(aliceAccount.getId(), amount, CurrencyCode.EUR, "test");
			
			assertThat(result).isNotNull();
			assertThat(result.getType()).isEqualTo(TransactionType.INTEREST_DEBIT);
			assertThat(result.getAmount()).isEqualByComparingTo(amount);
			assertThat(result.getStatus()).isEqualTo(TransactionStatus.SETTLED);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1990));
			
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
			
		}
		@Test
		@DisplayName("debitInterest invalid amount")
		void debitInterest_invalidAmount() {
			assertThatThrownBy(() -> transactionService.debitInterest(aliceAccount.getId(), BigDecimal.ZERO, CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> transactionService.debitInterest(aliceAccount.getId(), BigDecimal.valueOf(-10), CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			
		}	
	}
	@Nested
	@DisplayName("apply Fee")
	class ApplyFee{
		
		@Test
		@DisplayName("debit interest success")
		void applyFee_success() {
			
			BigDecimal amount = BigDecimal.valueOf(10);
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(transactionRepository.save(any())).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
				}
			);

			Transaction result = transactionService.applyFee(aliceAccount.getId(), amount, CurrencyCode.EUR, "test");
			
			assertThat(result).isNotNull();
			assertThat(result.getType()).isEqualTo(TransactionType.FEE);
			assertThat(result.getAmount()).isEqualByComparingTo(amount);
			assertThat(result.getStatus()).isEqualTo(TransactionStatus.SETTLED);
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1990));
			
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
			
		}
		@Test
		@DisplayName("applyFee invalidAmount")
		void debitInterest_invalidAmount() {
			assertThatThrownBy(() -> transactionService.applyFee(aliceAccount.getId(), BigDecimal.ZERO, CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> transactionService.applyFee(aliceAccount.getId(), BigDecimal.valueOf(-10), CurrencyCode.EUR, "test")).isInstanceOf(IllegalArgumentException.class);
			
		}	
	}
	@Nested
	@DisplayName("Change de devises (Currency Exchange)")
	class CurrencyExchange {

		private Account usdAccount;

		@BeforeEach
		void setUpExchange() {
			// On crée un compte secondaire en USD pour Alice pour tester le change
			usdAccount = Account.create("US7630006000011234567890199", "ACC-USD-01", AccountType.CURRENT, CurrencyCode.USD, alice);
			usdAccount.setId(UUID.randomUUID());
			usdAccount.setBalance(BigDecimal.ZERO); // Solde initial à 0 USD
		}

		@Test
		@DisplayName("currencyExchange - débite la source, convertit et crédite la destination (Cas Nominal)")
		void currencyExchange_success() {
			// Given
			BigDecimal exchangeAmount = BigDecimal.valueOf(100); // 100 EUR
			BigDecimal exchangeRate = BigDecimal.valueOf(1.10);  // 1 EUR = 1.10 USD
			
			// Le code métier utilise toCurrency.getDecimalPlaces(). 
			// Si CurrencyCode est un Enum standard sans cette méthode, ajuste selon ton modèle.
			BigDecimal expectedConvertedAmount = new BigDecimal("110.00"); 

			// Mock du verrouillage des deux comptes
			// Note : Si lockTwoAccounts appelle accountRepository, on l'adapte ici.
			// Ici on présume que la méthode appelle findByIdWithLock ou une méthode du service
			// Vu qu'on ne peut pas mocker une méthode privée du service testé, 
			// on part du principe que loadAndLock ou la méthode interne utilise le repository.
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(accountRepository.findByIdWithLock(usdAccount.getId())).willReturn(Optional.of(usdAccount));
			
			// Si lockTwoAccounts est codé directement dans TransactionServiceImpl via le repo, 
			// assure-toi que tes mocks interceptent bien les deux IDs.
			
			given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> {
				Transaction tx = inv.getArgument(0);
				tx.setId(UUID.randomUUID());
				return tx;
			});

			// When
			Transaction result = transactionService.currencyExchange(
				aliceAccount.getId(),
				usdAccount.getId(),
				alice.getId(),
				exchangeAmount,
				CurrencyCode.EUR,
				CurrencyCode.USD,
				exchangeRate
			);

			// Then
			assertThat(result).isNotNull();
			assertThat(result.getType()).isEqualTo(TransactionType.CURRENCY_EXCHANGE);
			assertThat(result.getStatus()).isEqualTo(TransactionStatus.SETTLED);
			assertThat(result.getAmount()).isEqualByComparingTo(exchangeAmount);
			assertThat(result.getExchangeRate()).isEqualByComparingTo(exchangeRate);
			
			// Vérification des soldes après l'opération
			assertThat(aliceAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1900)); // 2000 - 100
			assertThat(usdAccount.getBalance()).isEqualByComparingTo(expectedConvertedAmount);     // 0 + 110

			// Vérification des appels d'infrastructure
			then(accountRepository).should(times(1)).save(aliceAccount);
			then(accountRepository).should(times(1)).save(usdAccount);
			then(transactionRepository).should(times(1)).save(any(Transaction.class));
			then(eventProducer).should(times(1)).publishTransactionCreated(any(TransactionCreatedEvent.class));
		}

		@Test
		@DisplayName("currencyExchange - lève une exception si le montant est négatif ou nul")
		void currencyExchange_invalidAmount() {
			// Given / When / Then
			assertThatThrownBy(() ->
				transactionService.currencyExchange(
					aliceAccount.getId(), usdAccount.getId(), alice.getId(),
					BigDecimal.ZERO, CurrencyCode.EUR, CurrencyCode.USD, BigDecimal.valueOf(1.10)
				)
			).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("currencyExchange - lève IllegalArgumentException si les devises sont identiques")
		void currencyExchange_sameCurrencies() {
			// Given / When / Then
			assertThatThrownBy(() ->
				transactionService.currencyExchange(
					aliceAccount.getId(), usdAccount.getId(), alice.getId(),
					BigDecimal.valueOf(100), CurrencyCode.EUR, CurrencyCode.EUR, BigDecimal.valueOf(1.0)
				)
			).isInstanceOf(IllegalArgumentException.class)
			 .hasMessageContaining("Les devises source et destination doivent être différentes");
		}

		@Test
		@DisplayName("currencyExchange - lève IllegalArgumentException si le taux de change est nul ou négatif")
		void currencyExchange_invalidExchangeRate() {
			// Given / When / Then
			assertThatThrownBy(() ->
				transactionService.currencyExchange(
					aliceAccount.getId(), usdAccount.getId(), alice.getId(),
					BigDecimal.valueOf(100), CurrencyCode.EUR, CurrencyCode.USD, BigDecimal.ZERO
				)
			).isInstanceOf(IllegalArgumentException.class)
			 .hasMessageContaining("Le taux de change doit être strictement positif");

			assertThatThrownBy(() ->
				transactionService.currencyExchange(
					aliceAccount.getId(), usdAccount.getId(), alice.getId(),
					BigDecimal.valueOf(100), CurrencyCode.EUR, CurrencyCode.USD, BigDecimal.valueOf(-0.5)
				)
			).isInstanceOf(IllegalArgumentException.class)
			 .hasMessageContaining("Le taux de change doit être strictement positif");
		}

		@Test
		@DisplayName("currencyExchange - lève InsufficientFundsException si le compte source n'a pas assez d'argent")
		void currencyExchange_insufficientFunds() {
			// Given
			BigDecimal excessiveAmount = BigDecimal.valueOf(2500); // Solde d'Alice = 2000
			given(accountRepository.findByIdWithLock(aliceAccount.getId())).willReturn(Optional.of(aliceAccount));
			given(accountRepository.findByIdWithLock(usdAccount.getId())).willReturn(Optional.of(usdAccount));

			// When / Then
			assertThatThrownBy(() ->
				transactionService.currencyExchange(
					aliceAccount.getId(), usdAccount.getId(), alice.getId(),
					excessiveAmount, CurrencyCode.EUR, CurrencyCode.USD, BigDecimal.valueOf(1.10)
				)
			).isInstanceOf(InsufficientFundsException.class);

			then(transactionRepository).should(never()).save(any());
			then(eventProducer).should(never()).publishTransactionCreated(any());
		}
	}
	private User buildUser(String email, String firstName, String lastName) {
		User user = User.create(firstName, lastName, LocalDate.of(1985, 10, 1), email, "$2a$12$hashedPassword");
		user.setId(UUID.randomUUID());
		return user;
	}
	
	private Account buildAccount(String iban, String number, AccountType type, BigDecimal balance, User owner) {
		Account acc = Account.create(iban, number, type, CurrencyCode.EUR, owner);
		acc.setBalance(balance);
		acc.setId(UUID.randomUUID());
		return acc;
	}

    private Transaction buildTransaction(Account account, TransactionType type, TransactionStatus status, BigDecimal amount) {
		Transaction tx = Transaction.create(
			"TXN-TEST-" + UUID.randomUUID().toString().substring(0, 6),
			type, amount, CurrencyCode.EUR, account, "Test"
		);
		tx.setStatus(status);
		tx.setId(UUID.randomUUID());
		return tx;
    }
}