package com.bank.infrastructure.messaging;
import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.bank.domain.event.AccountBlockedEvent;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {
	
	
	private final KafkaTemplate<String, Object> kafkaTemplate;
	
	
    /**
     * Publie un événement de création de transaction.
     *
     * <p>Appelé par {@code TransactionServiceImpl} immédiatement après
     * la persistance de la transaction en base (statut PENDING).</p>
     *
     * @param event événement à publier
     */
	public void publishTransactionCreated(TransactionCreatedEvent event) {
		
		String key = event.accountId().toString();
		
		CompletableFuture<SendResult<String, Object>> future =
				kafkaTemplate.send(KafkaConfig.TOPIC_TRANSACTIONS, key, event);
	
		future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                    "[KAFKA] Échec publication TransactionCreatedEvent — " +
                    "ref={} accountId={} eventId={} error={}",
                    event.reference(), event.accountId(), event.eventId(), ex.getMessage(), ex
                );
            } else {
                log.debug(
                    "[KAFKA] TransactionCreatedEvent publié — " +
                    "ref={} topic={} partition={} offset={}",
                    event.reference(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            }
        });

	}
	
    /**
     * Publie une alerte fraude.
     *
     * <p>Appelé par {@code FraudDetectionServiceImpl} après calcul du score de risque.
     * L'envoi est synchrone (join) pour les alertes CRITICAL — on attend l'accusé
     * de réception Kafka avant de continuer, car le blocage du compte doit
     * être certain avant de répondre à la requête cliente.</p>
     *
     * @param event alerte à publier
     * @param waitForAck {@code true} pour bloquer jusqu'à confirmation Kafka
     *                   (utiliser uniquement pour severity CRITICAL)
     */
    public void publishFraudAlert(FraudAlertEvent event, boolean waitForAck) {
        String key = event.accountId().toString();
 
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(KafkaConfig.TOPIC_FRAUD_ALERTS, key, event);
 
        if (waitForAck) {
            try {
                SendResult<String, Object> result = future.join();
                log.info(
                    "[KAFKA] FraudAlertEvent (CRITICAL) publié — " +
                    "txRef={} accountId={} severity={} partition={} offset={}",
                    event.transactionReference(), event.accountId(), event.severity(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            } catch (Exception ex) {
                log.error(
                    "[KAFKA] ÉCHEC CRITIQUE publication FraudAlertEvent — " +
                    "txRef={} accountId={} severity={} error={}",
                    event.transactionReference(), event.accountId(),
                    event.severity(), ex.getMessage(), ex
                );
                // Propager l'exception — le service appelant doit gérer ce cas
                throw new RuntimeException("Impossible de publier l'alerte fraude critique", ex);
            }
        } else {
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error(
                        "[KAFKA] Échec publication FraudAlertEvent — " +
                        "txRef={} severity={} error={}",
                        event.transactionReference(), event.severity(), ex.getMessage()
                    );
                } else {
                    log.info(
                        "[KAFKA] FraudAlertEvent publié — " +
                        "txRef={} severity={} partition={} offset={}",
                        event.transactionReference(), event.severity(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                    );
                }
            });
        }
    }
    
    /**
     * Surcharge pratique — publication asynchrone (non bloquante).
     */
    public void publishFraudAlert(FraudAlertEvent event) {
        publishFraudAlert(event, false);
    }
    
    /**
     * Publie un événement de blocage de compte.
     *
     * <p>Appelé par {@code AccountServiceImpl} après le changement de statut
     * en base. Les consommateurs (notification, carte) réagissent en cascade.</p>
     *
     * @param event événement de blocage
     */
    public void publishAccountBlocked(AccountBlockedEvent event) {
        String key = event.accountId().toString();
 
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(KafkaConfig.TOPIC_ACCOUNT_EVENTS, key, event);
 
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                    "[KAFKA] Échec publication AccountBlockedEvent — " +
                    "accountId={} reason={} error={}",
                    event.accountId(), event.reason(), ex.getMessage(), ex
                );
            } else {
                log.info(
                    "[KAFKA] AccountBlockedEvent publié — " +
                    "accountId={} reason={} automatic={} partition={} offset={}",
                    event.accountId(), event.reason(), event.automatic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            }
        });
    }



}
