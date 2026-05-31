package com.bank.service;



import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.bank.domain.entity.Account;
import com.bank.domain.entity.Transaction;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.impl.FraudDetectionServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService — tests unitaires")
class FraudServiceTest {

	  @Mock
	  private TransactionRepository transactionRepository;
	  @Mock
	  private TransactionEventProducer eventProducer;
	  @Mock
	  private AuditLogRepository auditLogRepository;
	  	  
	  @InjectMocks
	  private FraudDetectionServiceImpl fraudDetectionService;
	  
	  private Account aliceAccount;
	  private User alice;
	  
	  @BeforeEach
	  void setUp() {
		  
	        alice = User.create("Alice", "Martin",
                    LocalDate.of(1985, 6, 15),
                    "alice@bank.com", "$2a$12$hash");
	        alice.setId(UUID.randomUUID());
	        
			aliceAccount = Account.create(
			    "FR7630006000011234567890189", "ACC-001",
			    AccountType.CURRENT, CurrencyCode.EUR, alice
			);
			aliceAccount.setId(UUID.randomUUID());
			aliceAccount.setBalance(new BigDecimal("3000.00"));
			
	        // Injecter les seuils de configuration via ReflectionTestUtils
	        // (simule @Value en test unitaire sans Spring context)
	        ReflectionTestUtils.setField(fraudDetectionService, "scoreThresholdMedium",  new BigDecimal("0.40"));
	        ReflectionTestUtils.setField(fraudDetectionService, "scoreThresholdHigh",    new BigDecimal("0.70"));
	        ReflectionTestUtils.setField(fraudDetectionService, "scoreThresholdCritical",new BigDecimal("0.90"));
	        ReflectionTestUtils.setField(fraudDetectionService, "velocityWindowMinutes", 60);
	        ReflectionTestUtils.setField(fraudDetectionService, "velocityMaxTransactions", 10);
	        ReflectionTestUtils.setField(fraudDetectionService, "velocityMaxAmount",     new BigDecimal("5000.00"));
	        ReflectionTestUtils.setField(fraudDetectionService, "amlThreshold",          new BigDecimal("10000.00"));


		  
	  }
	  
	  @Nested
	  @DisplayName("Calculate risk score")
	  class ScoreCalculationTest {
		  
		  @Test
		  @DisplayName("normal score")
		  void calculateRiskScore_normal(){
			  
            TransactionCreatedEvent event = buildEvent(
                    BigDecimal.valueOf(50.00), TransactionType.CARD_PAYMENT,
                    "FR7630006000015555666677778", "192.168.1.1"
             );
            
            given(transactionRepository.countRecentByAccount(eq(event.accountId()), any(LocalDateTime.class))).willReturn(2L);
            given(transactionRepository.sumDebitedAmountSince(eq(event.accountId()), any(LocalDateTime.class))).willReturn(BigDecimal.valueOf(120));
            
            BigDecimal score = fraudDetectionService.calculateRiskScore(event);
            
            assertThat(score).isNotNull();
            assertThat(score).isLessThan(BigDecimal.valueOf(0.40));
		  }
		  
		  @Test
		  @DisplayName("aml thresholdExceeded")
		  void calculateRiskScore_amlThresholdExceeded() {
			  
			  TransactionCreatedEvent event = buildEvent(
	                    BigDecimal.valueOf(15000.00), TransactionType.SEPA_TRANSFER,
	                    "FR7630006000015555666677778", "192.168.1.1"
	           );
	            
	           given(transactionRepository.countRecentByAccount(eq(event.accountId()), any(LocalDateTime.class))).willReturn(2L);
	           given(transactionRepository.sumDebitedAmountSince(eq(event.accountId()), any(LocalDateTime.class))).willReturn(BigDecimal.valueOf(120));
	            
	           BigDecimal score = fraudDetectionService.calculateRiskScore(event);
	            
	           assertThat(score).isNotNull();
	           assertThat(score).isGreaterThanOrEqualTo(BigDecimal.valueOf(0.40));
			  
		  }
		  
		  @Test
		  @DisplayName("multiple factors risk")
		  void calculateRiskScore_multipleFactors() {
			  
			 TransactionCreatedEvent event = buildEvent(
	                    BigDecimal.valueOf(15000.00), TransactionType.INTERNATIONAL_TRANSFER,
	                    "NG12345678901234567890", "41.202.219.1"
	           );
			  given(transactionRepository.countRecentByAccount(eq(event.accountId()), any(LocalDateTime.class))).willReturn(15L);
	          given(transactionRepository.sumDebitedAmountSince(eq(event.accountId()), any(LocalDateTime.class))).willReturn(BigDecimal.valueOf(9000));
	          
	          BigDecimal score = fraudDetectionService.calculateRiskScore(event);
	          
	          assertThat(score).isGreaterThanOrEqualTo(BigDecimal.valueOf(0.70));
		  }
        
	        
	       @ParameterizedTest(name = "amount={0} → score >= {1}")
	       @CsvSource({
	            "50.00,  0.00",   // transaction normale
	            "1000.00,0.00",   // montant élevé mais raisonnable
	            "9999.00,0.20",   // juste sous le seuil AML
	            "10000.00,0.40",  // seuil AML exact
	            "50000.00,0.70",  // montant très élevé
	       })
	       void calculateScore_scalesWithAmount(String amountStr, String minScoreStr) {
	            BigDecimal amount   = new BigDecimal(amountStr);
	            BigDecimal minScore = new BigDecimal(minScoreStr);
	 
	            TransactionCreatedEvent event = buildEvent(
	                amount, TransactionType.SEPA_TRANSFER,
	                "FR7630006000015555666677778", "192.168.1.1"
	            );
	            given(transactionRepository.countRecentByAccount(any(), any()))
	                .willReturn(1L);
	            given(transactionRepository.sumDebitedAmountSince(any(), any()))
	                .willReturn(amount);
	 
	            BigDecimal score = fraudDetectionService.calculateRiskScore(event);
	 
	            assertThat(score).isGreaterThanOrEqualTo(minScore);
	       }


	  }
	  
	  @Nested
	  @DisplayName("Velocity checks")
	  class VelocityChecksTest{
		  
	        @Test
	        @DisplayName("score augmente quand le nombre de transactions dépasse le seuil")
	        void velocityCheck_countExceeded() {
	            // Given — 11 transactions (> seuil de 10)
	            TransactionCreatedEvent event = buildEvent(
	                BigDecimal.valueOf(100.00), TransactionType.CARD_PAYMENT,
	                "FR7630006000015555666677778", "192.168.1.1"
	            );
	            given(transactionRepository.countRecentByAccount(any(), any()))
	                .willReturn(11L); // dépasse velocityMaxTransactions = 10
	            given(transactionRepository.sumDebitedAmountSince(any(), any()))
	                .willReturn( BigDecimal.valueOf(1100.00));
	 
	            // When
	            BigDecimal score = fraudDetectionService.calculateRiskScore(event);
	 
	            // Then — le velocity check doit augmenter le score
	            assertThat(score).isGreaterThan(BigDecimal.valueOf(0.20));
	        }
	        
	        @Test
	        @DisplayName("score de velocité sur le montant")
	        void velocityCheck_amountExceeded() {
	            // Given — 4 500 € débités en 1h (proche du seuil de 5 000 €)
	            TransactionCreatedEvent event = buildEvent(
	                BigDecimal.valueOf(600.00), TransactionType.CARD_PAYMENT,
	                null, "192.168.1.1"
	            );
	            given(transactionRepository.countRecentByAccount(any(), any()))
	                .willReturn(5L);
	            given(transactionRepository.sumDebitedAmountSince(any(), any()))
	                .willReturn(BigDecimal.valueOf(5100.00)); // 90% du seuil velocity
	 
	            // When
	            BigDecimal score = fraudDetectionService.calculateRiskScore(event);
	 
	            // Then
	            assertThat(score).isGreaterThan(BigDecimal.ZERO);
	        }
	        @Test
	        @DisplayName("pas d'augmentation de score si velocity dans les limites")
	        void velocityCheck_withinLimits() {
	        	
	        	TransactionCreatedEvent event = buildEvent(BigDecimal.valueOf(200), TransactionType.CARD_PAYMENT, null, "192.168.0.1");
	        	
	        	given(transactionRepository.countRecentByAccount(any(),any())).willReturn(1L);
	        	given(transactionRepository.sumDebitedAmountSince(any(),any() )).willReturn(BigDecimal.valueOf(600));
	        	
	        	BigDecimal score = fraudDetectionService.calculateRiskScore(event);
	        	
	        	assertThat(score).isLessThan(BigDecimal.valueOf(0.40));
	        	
	        }


	  }
	  @Nested
	  @DisplayName("Analyse des alertes")
	  class AlertTriggerTests{
		  
		  @Test
		  @DisplayName("should not trigger event")
		  void shouldNotTriggerAlert() {
			  
			  	TransactionCreatedEvent event = buildEvent(BigDecimal.valueOf(200), TransactionType.CARD_PAYMENT, null, "192.168.0.1");
	        	
	        	given(transactionRepository.countRecentByAccount(any(),any())).willReturn(1L);
	        	given(transactionRepository.sumDebitedAmountSince(any(),any() )).willReturn(BigDecimal.valueOf(30));
	        	
	        	fraudDetectionService.analyze(event);
	        	
	        	then(eventProducer).should(never()).publishFraudAlert(any());
			  
		  }
		  
		  @Test
		  @DisplayName("should trigger medium")
		  void shouldTriggerMedium() {
			  	TransactionCreatedEvent event = buildEvent(BigDecimal.valueOf(12000), TransactionType.SEPA_TRANSFER, "FR7630006000015555666677778", "192.168.0.1");
	        	
	        	given(transactionRepository.countRecentByAccount(any(),any())).willReturn(2L);
	        	given(transactionRepository.sumDebitedAmountSince(any(),any() )).willReturn(BigDecimal.valueOf(12000));
	        	
	        	fraudDetectionService.analyze(event);
	        	
	        	then(eventProducer).should(times(1)).publishFraudAlert(any(FraudAlertEvent.class),eq(false));
		  }
		  
        @Test
        @DisplayName("alerte bloquante publiée si score CRITICAL")
        void analyze_criticalScore_blockingAlert() {
            // Given — score critique : gros montant international + velocity élevée
            TransactionCreatedEvent event = buildEvent(
                BigDecimal.valueOf(45000.00), TransactionType.INTERNATIONAL_TRANSFER,
                "NG12345678901234567890", "41.202.219.1"
            );
            given(transactionRepository.countRecentByAccount(any(), any()))
                .willReturn(12L); // dépasse velocity
            given(transactionRepository.sumDebitedAmountSince(any(), any()))
                .willReturn(BigDecimal.valueOf(45000.00));
 
            // When
            fraudDetectionService.analyze(event);
 
            // Then — alerte bloquante (waitForAck = true)
            then(eventProducer).should(times(1))
                .publishFraudAlert(any(FraudAlertEvent.class), eq(true));
        }
        @Test
        @DisplayName("l'alerte publiée contient les bonnes données")
        void analyze_alertPayload_correct() {
            // Given
            TransactionCreatedEvent event = buildEvent(
            		BigDecimal.valueOf(15000.00), TransactionType.SEPA_TRANSFER,
                "DE89370400440532013000", "192.168.1.1"
            );
            given(transactionRepository.countRecentByAccount(any(), any())).willReturn(2L);
            given(transactionRepository.sumDebitedAmountSince(any(), any()))
                .willReturn(BigDecimal.valueOf(15000.00));
 
            ArgumentCaptor<FraudAlertEvent> captor =
                ArgumentCaptor.forClass(FraudAlertEvent.class);
 
            // When
            fraudDetectionService.analyze(event);
 
            // Then
            then(eventProducer).should().publishFraudAlert(captor.capture(), any(Boolean.class));
            FraudAlertEvent alert = captor.getValue();
 
            assertThat(alert.transactionId()).isEqualTo(event.transactionId());
            assertThat(alert.accountId()).isEqualTo(event.accountId());
            assertThat(alert.userId()).isEqualTo(event.userId());
            assertThat(alert.riskScore()).isNotNull();
            assertThat(alert.riskScore()).isGreaterThan(BigDecimal.ZERO);
            assertThat(alert.triggeredRules()).isNotBlank();
            assertThat(alert.eventId()).isNotNull();
            assertThat(alert.occurredAt()).isNotNull();
        }	  
  
	  }
	  
	    @Nested
	    @DisplayName("Contrôles AML (LCB-FT)")
	    class AmlTests {
	 
	        @Test
	        @DisplayName("virement international déclenche contrôle AML renforcé")
	        void internationalTransfer_requiresAmlControl() {
	            TransactionCreatedEvent event = buildEvent(
	            		BigDecimal.valueOf(500.00), TransactionType.INTERNATIONAL_TRANSFER,
	                "US12345678901234567890", "192.168.1.1"
	            );
	 
	            assertThat(event.requiresFraudAnalysis()).isTrue();
	        }
	 
	        @Test
	        @DisplayName("transaction > 10 000 € déclenche contrôle AML")
	        void largeTransaction_requiresAmlControl() {
	            TransactionCreatedEvent event = buildEvent(
	            	BigDecimal.valueOf(10000.00), TransactionType.SEPA_TRANSFER,
	                "FR7630006000015555666677778", "192.168.1.1"
	            );
	 
	            assertThat(event.requiresFraudAnalysis()).isTrue();
	        }
	 
	        @Test
	        @DisplayName("transaction standard < 10 000 € ne déclenche pas AML obligatoire")
	        void normalTransaction_noAmlRequired() {
	            TransactionCreatedEvent event = buildEvent(
	                BigDecimal.valueOf(500.00), TransactionType.CARD_PAYMENT,
	                "FR7630006000015555666677778", "192.168.1.1"
	            );
	 
	            assertThat(event.requiresFraudAnalysis()).isFalse();
	        }
	    }
	    @Nested
	    @DisplayName("Sévérité des alertes")
	    class SeverityTests {
	 
	        @ParameterizedTest(name = "score={0} → severity={1}")
	        @CsvSource({
	            "0.30, LOW",
	            "0.40, MEDIUM",
	            "0.69, MEDIUM",
	            "0.70, MEDIUM",
	            "0.89, HIGH",
	            "0.90, HIGH",
	            "0.94, HIGH",
	            "0.95, CRITICAL",
	            "1.00, CRITICAL",
	        })
	        @DisplayName("fromScore retourne la bonne sévérité")
	        void fromScore_correctSeverity(String scoreStr, String expectedSeverity) {
	            BigDecimal score    = new BigDecimal(scoreStr);
	            FraudAlertEvent.Severity severity = FraudAlertEvent.Severity.fromScore(score);
	 
	            assertThat(severity.name()).isEqualTo(expectedSeverity);
	        }
	 
	        @Test
	        @DisplayName("CRITICAL requiert une action immédiate")
	        void critical_requiresImmediateAction() {
	            FraudAlertEvent alert = FraudAlertEvent.of(
	                UUID.randomUUID(), "TXN-001",
	                aliceAccount.getId(), alice.getId(),
	                new BigDecimal("0.97"), "AMOUNT_THRESHOLD,VELOCITY",
	                "Score critique", false, "192.168.1.1", "FR"
	            );
	 
	            assertThat(alert.requiresImmediateAction()).isTrue();
	            assertThat(alert.requiresComplianceNotification()).isTrue();
	        }
	 
	        @Test
	        @DisplayName("LOW ne requiert pas d'action immédiate")
	        void low_noImmediateAction() {
	            FraudAlertEvent alert = FraudAlertEvent.of(
	                UUID.randomUUID(), "TXN-002",
	                aliceAccount.getId(), alice.getId(),
	                new BigDecimal("0.25"), "MONITORING",
	                "Score faible", false, "192.168.1.1", "FR"
	            );
	 
	            assertThat(alert.requiresImmediateAction()).isFalse();
	            assertThat(alert.requiresComplianceNotification()).isFalse();
	        }
	    }

	  private TransactionCreatedEvent buildEvent(BigDecimal amount,
	            TransactionType type,
	            String counterpartIban,
	            String ipAddress) {
			return TransactionCreatedEvent.of(
			UUID.randomUUID(),
			"TXN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
			type,
			amount,
			CurrencyCode.EUR,
			aliceAccount.getId(),
			counterpartIban,
			"Bénéficiaire Test",
			alice.getId(),
			ipAddress,
			null
		);
	}

}