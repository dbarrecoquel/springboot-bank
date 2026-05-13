package com.bank.infrastructure.messaging;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bank.domain.entity.AuditLog;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.event.AccountBlockedEvent;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudAlertConsumer {
 
    private final TransactionRepository  transactionRepository;
    private final AccountRepository      accountRepository;
    private final AuditLogRepository     auditLogRepository;
    private final TransactionEventProducer eventProducer;

    
    @KafkaListener(
            topics                = KafkaConfig.TOPIC_FRAUD_ALERTS,
            groupId               = KafkaConfig.GROUP_FRAUD_DETECTION,
            containerFactory      = "fraudAlertListenerContainerFactory"
     )
    @Transactional
    public void consume(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION)  int partition,
            @Header(KafkaHeaders.OFFSET)              long offset,
            Acknowledgment ack) {
 
        log.info(
            "[FRAUD] Alerte reçue — eventId={} txRef={} accountId={} severity={} " +
            "score={} rules=[{}] partition={} offset={}",
            event.eventId(), event.transactionReference(), event.accountId(),
            event.severity(), event.riskScore(), event.triggeredRules(),
            partition, offset
        );
 
        try {
            dispatch(event);
            ack.acknowledge();
 
            log.info(
                "[FRAUD] Alerte traitée — eventId={} action={}",
                event.eventId(), event.recommendedAction()
            );
 
        } catch (Exception ex) {
            log.error(
                "[FRAUD] Erreur traitement alerte — eventId={} txRef={} error={}",
                event.eventId(), event.transactionReference(), ex.getMessage(), ex
            );
            // Ne pas acquitter — le DefaultErrorHandler retentera
            throw ex;
        }
    }
    private void dispatch(FraudAlertEvent event) {
        switch (event.recommendedAction()) {
            case MONITOR               -> handleMonitor(event);
            case REQUEST_STRONG_AUTH   -> handleStrongAuth(event);
            case BLOCK_TRANSACTION     -> handleBlockTransaction(event);
            case BLOCK_ACCOUNT         -> handleBlockAccount(event, false);
            case BLOCK_ACCOUNT_AND_CARD-> handleBlockAccount(event, true);
        }
    }
    private void handleMonitor(FraudAlertEvent event) {
        log.info(
            "[FRAUD] Surveillance passive — txRef={} score={} rules=[{}]",
            event.transactionReference(), event.riskScore(), event.triggeredRules()
        );
        auditLogRepository.save(AuditLog.success(
            "FRAUD_ALERT_MONITORED",
            "Transaction",
            event.transactionId().toString(),
            event.userId(),
            "Score: " + event.riskScore() + " — surveillance passive"
        ));
    }
    private void handleStrongAuth(FraudAlertEvent event) {
        log.info(
            "[FRAUD] Re-authentification forte requise — txRef={} score={}",
            event.transactionReference(), event.riskScore()
        );
 
        // Passer la transaction en FRAUD_SUSPECT en attente de 3DS/OTP
        int updated = transactionRepository.flagFraud(
            event.transactionId(),
            event.riskScore(),
            LocalDateTime.now()
        );
 
        if (updated == 0) {
            log.warn("[FRAUD] Transaction introuvable pour flagFraud — txId={}",
                     event.transactionId());
        }
 
        auditLogRepository.save(AuditLog.success(
            "FRAUD_STRONG_AUTH_REQUIRED",
            "Transaction",
            event.transactionId().toString(),
            event.userId(),
            "Score: " + event.riskScore() + " — 3DS/OTP requis"
        ));
    }
    
    private void handleBlockTransaction(FraudAlertEvent event) {
        log.warn(
            "[FRAUD] Blocage transaction — txRef={} score={} rules=[{}]",
            event.transactionReference(), event.riskScore(), event.triggeredRules()
        );
 
        transactionRepository.updateStatus(
            event.transactionId(),
            com.bank.domain.enums.TransactionStatus.BLOCKED,
            LocalDateTime.now()
        );
 
        auditLogRepository.save(AuditLog.failure(
            "TRANSACTION_BLOCKED",
            "Transaction",
            event.transactionId().toString(),
            event.userId(),
            "Fraude détectée — score: " + event.riskScore()
                + " — règles: [" + event.triggeredRules() + "]"
        ));
    }
    private void handleBlockAccount(FraudAlertEvent event, boolean alsoBlockCards) {
        log.error(
            "[FRAUD] Blocage compte{} — accountId={} score={} rules=[{}]",
            alsoBlockCards ? " + cartes" : "",
            event.accountId(), event.riskScore(), event.triggeredRules()
        );
 
        // 1. Bloquer la transaction
        transactionRepository.updateStatus(
            event.transactionId(),
            com.bank.domain.enums.TransactionStatus.BLOCKED,
            LocalDateTime.now()
        );
 
        // 2. Récupérer le solde courant pour l'événement de blocage
        BigDecimal balance = accountRepository.findById(event.accountId())
            .map(a -> a.getBalance())
            .orElse(BigDecimal.ZERO);
 
        // 3. Bloquer le compte
        accountRepository.updateStatus(
            event.accountId(),
            com.bank.domain.enums.AccountStatus.BLOCKED,
            LocalDateTime.now()
        );
 
        // 4. Bloquer les cartes si demandé
        if (alsoBlockCards) {
            // Déclenché via l'événement AccountBlockedEvent consommé par CardService
            log.info("[FRAUD] Blocage des cartes délégué à AccountBlockedEvent — accountId={}",
                     event.accountId());
        }
 
        // 5. Journaliser
        auditLogRepository.save(
            AuditLog.failure(
                alsoBlockCards ? "ACCOUNT_AND_CARDS_BLOCKED" : "ACCOUNT_BLOCKED",
                "Account",
                event.accountId().toString(),
                event.userId(),
                "Fraude détectée — score: " + event.riskScore()
                    + " — règles: [" + event.triggeredRules() + "]"
            ).withIpAddress(event.initiatorIp())
        );
 
        // 6. Publier AccountBlockedEvent pour les consommateurs aval
        AccountBlockedEvent blockedEvent = AccountBlockedEvent.automatic(
            event.accountId(),
            "[IBAN masqué]",          // l'IBAN sera résolu par le service compte
            event.userId(),
            balance,
            CurrencyCode.EUR,          // devise par défaut — à affiner si multi-devise
            AccountBlockedEvent.BlockReason.FRAUD_CONFIRMED,
            "Fraude automatique détectée — score: " + event.riskScore(),
            event.eventId(),
            event.transactionId()
        );
 
        eventProducer.publishAccountBlocked(blockedEvent);
    }





	
}
