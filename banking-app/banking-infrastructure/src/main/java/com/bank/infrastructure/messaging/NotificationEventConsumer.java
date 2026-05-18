package com.bank.infrastructure.messaging;

import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Notification;
import com.bank.domain.entity.Notification.Channel;
import com.bank.domain.event.AccountBlockedEvent;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;
import com.bank.infrastructure.notification.EmailAdapter;
import com.bank.infrastructure.notification.PushAdapter;
import com.bank.infrastructure.notification.SmsAdapter;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

	private final EmailAdapter emailAdapter;
	private final UserRepository userRepository;
	private final PushAdapter pushAdapter;
	private final SmsAdapter smsAdapter;
	private final AuditLogRepository auditLogRepository;
	
    @KafkaListener(
            topics           = KafkaConfig.TOPIC_TRANSACTIONS,
            groupId          = KafkaConfig.GROUP_NOTIFICATIONS,
            containerFactory = "notificationListenerContainerFactory"
    )
    @Transactional
    public void onTransactionCreated(
    		@Payload TransactionCreatedEvent event,
    		@Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    		@Header(KafkaHeaders.OFFSET) long offset,
    		Acknowledgment ack) {
    	
        log.debug(
                "[NOTIF] TransactionCreatedEvent reçu — ref={} userId={} partition={} offset={}",
                event.reference(), event.userId(), partition, offset
            );
    	
        try {
        	userRepository.findByIdWithAccounts(event.userId()).ifPresentOrElse(
        			user -> {
        				String subject = buildTransactionSubject(event);
        				String body = buildTransactionBody(event);
        				
        				if (user.isEmailVerified()) {
        					Notification email = Notification.email(user.getId(), user.getEmail(),subject,body, "transaction.created");
        	                email.setSourceType("Transaction");
                            email.setSourceId(event.transactionId().toString());
                            send(email);

        				}
                        if (user.isPhoneVerified()
                                && event.amount().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                            String smsBody = buildTransactionSms(event);
                            Notification sms = Notification.sms(
                                user.getId(), user.getPhoneNumber(), smsBody
                            );
                            sms.setSourceType("Transaction");
                            sms.setSourceId(event.transactionId().toString());
                            send(sms);
                        }

        			},() -> log.warn(
                            "[NOTIF] Utilisateur introuvable pour notification — userId={}",
                            event.userId()
                        )
        			
        		);
        	  ack.acknowledge();
        } catch (Exception ex) {
            log.error(
                    "[NOTIF] Erreur traitement TransactionCreatedEvent — ref={} error={}",
                    event.reference(), ex.getMessage(), ex
                );
                throw ex;
        }
    }
    // ─────────────────────────────────────────────────────────
    //  Listener — FraudAlertEvent
    // ─────────────────────────────────────────────────────────
 
    @KafkaListener(
        topics           = KafkaConfig.TOPIC_FRAUD_ALERTS,
        groupId          = KafkaConfig.GROUP_NOTIFICATIONS,
        containerFactory = "notificationListenerContainerFactory"
    )
    @Transactional
    public void onFraudAlert(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset,
            Acknowledgment ack) {
 
        log.warn(
            "[NOTIF] FraudAlertEvent reçu — txRef={} userId={} severity={} partition={} offset={}",
            event.transactionReference(), event.userId(), event.severity(), partition, offset
        );
 
        try {
            userRepository.findById(event.userId()).ifPresentOrElse(
                user -> {
                    // Notification client — message volontairement vague (ne pas exposer les règles)
                    String clientSubject = "Alerte sécurité — activité inhabituelle détectée";
                    String clientBody    = buildFraudAlertClientBody(event);
 
                    // Email client
                    if (user.isEmailVerified()) {
                        send(Notification.email(
                            user.getId(), user.getEmail(), clientSubject, clientBody,
                            "fraud.alert.client"
                        ));
                    }
 
                    // SMS client — systématique pour les alertes fraude
                    if (user.isPhoneVerified()) {
                        send(Notification.sms(
                            user.getId(), user.getPhoneNumber(),
                            "BANQUE ALERTE : activité suspecte détectée sur votre compte. " +
                            "Si vous n'êtes pas à l'origine de cette opération, " +
                            "appelez le 09 XX XX XX XX immédiatement."
                        ));
                    }
 
                    // Notification interne compliance si sévérité >= MEDIUM
                    if (event.requiresComplianceNotification()) {
                        sendComplianceAlert(event);
                    }
 
                    auditLogRepository.save(AuditLog.success(
                        "FRAUD_NOTIFICATION_SENT",
                        "User",
                        user.getId().toString(),
                        user.getId(),
                        "Canaux: email=" + user.isEmailVerified()
                            + " sms=" + user.isPhoneVerified()
                            + " severity=" + event.severity()
                    ));
                },
                () -> log.warn(
                    "[NOTIF] Utilisateur introuvable pour alerte fraude — userId={}",
                    event.userId()
                )
            );
 
            ack.acknowledge();
 
        } catch (Exception ex) {
            log.error(
                "[NOTIF] Erreur traitement FraudAlertEvent — txRef={} error={}",
                event.transactionReference(), ex.getMessage(), ex
            );
            throw ex;
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Listener — AccountBlockedEvent
    // ─────────────────────────────────────────────────────────
 
    @KafkaListener(
        topics           = KafkaConfig.TOPIC_ACCOUNT_EVENTS,
        groupId          = KafkaConfig.GROUP_NOTIFICATIONS,
        containerFactory = "notificationListenerContainerFactory"
    )
    @Transactional
    public void onAccountBlocked(
            @Payload AccountBlockedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset,
            Acknowledgment ack) {
 
        log.warn(
            "[NOTIF] AccountBlockedEvent reçu — accountId={} reason={} automatic={} " +
            "partition={} offset={}",
            event.accountId(), event.reason(), event.automatic(), partition, offset
        );
 
        try {
            userRepository.findById(event.userId()).ifPresentOrElse(
                user -> {
                    String subject = "Votre compte a été bloqué";
                    String body    = buildAccountBlockedBody(event);
 
                    // Email — systématique
                    if (user.isEmailVerified()) {
                        send(Notification.email(
                            user.getId(), user.getEmail(), subject, body,
                            "account.blocked"
                        ));
                    }
 
                    // SMS — systématique pour un blocage
                    if (user.isPhoneVerified()) {
                        send(Notification.sms(
                            user.getId(), user.getPhoneNumber(),
                            "BANQUE : Votre compte " + event.maskedIban() +
                            " a été temporairement bloqué. " +
                            "Contactez votre conseiller ou appelez le 09 XX XX XX XX."
                        ));
                    }
 
                    // Push — si tokens disponibles
                    // (en pratique, le token FCM est récupéré depuis un service dédié)
                    log.info(
                        "[NOTIF] Notifications blocage compte envoyées — " +
                        "userId={} email={} sms={}",
                        user.getId(), user.isEmailVerified(), user.isPhoneVerified()
                    );
 
                    // Notification interne compliance si blocage réglementaire
                    if (event.isRegulatoryOrigin()) {
                        sendRegulatoryBlockAlert(event);
                    }
 
                    auditLogRepository.save(AuditLog.success(
                        "ACCOUNT_BLOCK_NOTIFICATION_SENT",
                        "Account",
                        event.accountId().toString(),
                        event.userId(),
                        "Raison: " + event.reason().getLabel()
                            + " — canaux: email + sms"
                    ));
                },
                () -> log.warn(
                    "[NOTIF] Utilisateur introuvable pour notification blocage — userId={}",
                    event.userId()
                )
            );
 
            ack.acknowledge();
 
        } catch (Exception ex) {
            log.error(
                "[NOTIF] Erreur traitement AccountBlockedEvent — accountId={} error={}",
                event.accountId(), ex.getMessage(), ex
            );
            throw ex;
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Envoi via les adapters
    // ─────────────────────────────────────────────────────────
 
    private void send(Notification notification) {
        try {
            switch (notification.getChannel()) {
                case EMAIL -> emailAdapter.send(notification);
                case SMS   -> smsAdapter.send(notification);
                case PUSH  -> pushAdapter.send(notification);
                default    -> log.warn("[NOTIF] Canal non géré : {}", notification.getChannel());
            }
        } catch (Exception ex) {
            // Échec d'envoi loggué mais non propagé — la notification est
            // persistée avec statut FAILED pour retry ultérieur
            log.error(
                "[NOTIF] Échec envoi {} — userId={} error={}",
                notification.getChannel(), notification.getUserId(), ex.getMessage()
            );
            notification.markFailed(ex.getMessage());
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Notifications internes — compliance
    // ─────────────────────────────────────────────────────────
 
    private void sendComplianceAlert(FraudAlertEvent event) {
        String body = String.format(
            """
            [COMPLIANCE INTERNE]
            Alerte fraude — sévérité : %s
            Transaction : %s
            Compte      : %s
            Score risque : %s
            Règles      : [%s]
            Action      : %s
            IP          : %s (%s)
            Horodatage  : %s
            """,
            event.severity(), event.transactionReference(),
            event.accountId(), event.riskScore(),
            event.triggeredRules(), event.recommendedAction(),
            event.initiatorIp(), event.ipCountryCode(),
            event.occurredAt()
        );
 
        log.info("[NOTIF] Alerte compliance envoyée — txRef={} severity={}",
                 event.transactionReference(), event.severity());
        // En production : emailAdapter.sendToCompliance(body) ou Slack webhook
    }
 
    private void sendRegulatoryBlockAlert(AccountBlockedEvent event) {
        log.info(
            "[NOTIF] Alerte réglementaire envoyée compliance — accountId={} reason={} " +
            "tracfin={}",
            event.accountId(), event.reason(), event.requiresTracfinReport()
        );
        // En production : emailAdapter.sendToCompliance(...) ou appel TRACFIN API
    }
 
    // ─────────────────────────────────────────────────────────
    //  Construction des messages
    // ─────────────────────────────────────────────────────────
 
    private String buildTransactionSubject(TransactionCreatedEvent event) {
        return switch (event.type()) {
            case SEPA_TRANSFER, INTERNAL_TRANSFER, INTERNATIONAL_TRANSFER ->
                "Virement de " + event.amount() + " " + event.currency() + " effectué";
            case CARD_PAYMENT ->
                "Paiement de " + event.amount() + " " + event.currency() + " enregistré";
            case CASH_DEPOSIT ->
                "Dépôt de " + event.amount() + " " + event.currency() + " crédité";
            case CASH_WITHDRAWAL ->
                "Retrait de " + event.amount() + " " + event.currency() + " effectué";
            default ->
                "Opération de " + event.amount() + " " + event.currency() + " — " + event.reference();
        };
    }
 
    private String buildTransactionBody(TransactionCreatedEvent event) {
        return String.format(
            """
            Bonjour,
 
            Une opération a été enregistrée sur votre compte :
 
              Référence   : %s
              Type        : %s
              Montant     : %s %s
              Bénéficiaire: %s
              Date        : %s
 
            Si vous n'êtes pas à l'origine de cette opération,
            contactez immédiatement votre conseiller.
 
            Cordialement,
            Votre banque
            """,
            event.reference(),
            event.type().getLabel(),
            event.amount(), event.currency(),
            event.counterpartName() != null ? event.counterpartName() : "N/A",
            event.occurredAt()
        );
    }
 
    private String buildTransactionSms(TransactionCreatedEvent event) {
        return String.format(
            "BANQUE : %s %s %s — Réf: %s. Pas vous ? Appelez le 09 XX XX XX XX.",
            event.type().getLabel(), event.amount(), event.currency(), event.reference()
        );
    }
 
    private String buildFraudAlertClientBody(FraudAlertEvent event) {
        return String.format(
            """
            Bonjour,
 
            Nous avons détecté une activité inhabituelle sur votre compte.
 
            Une opération suspecte a été bloquée à titre préventif.
            Votre compte peut être temporairement restreint.
 
            Si vous êtes à l'origine de cette opération, contactez
            votre conseiller ou notre service client au 09 XX XX XX XX.
 
            Dans le cas contraire, votre compte est protégé.
            Aucune action supplémentaire n'est requise de votre part.
 
            Cordialement,
            Le service sécurité de votre banque
 
            [Réf. interne : %s — %s]
            """,
            event.transactionReference(),
            event.occurredAt().toLocalDate()
        );
    }
 
    private String buildAccountBlockedBody(AccountBlockedEvent event) {
        String motif = event.isPotentiallyReversible()
            ? "Cette mesure est temporaire. Contactez votre conseiller pour la lever."
            : "Cette mesure fait suite à une procédure réglementaire.";
 
        return String.format(
            """
            Bonjour,
 
            Votre compte %s a été bloqué.
 
            Motif   : %s
            Date    : %s
 
            %s
 
            Pour toute question : 09 XX XX XX XX (lun-ven 8h-19h)
 
            Cordialement,
            Votre banque
            """,
            event.maskedIban(),
            event.reason().getLabel(),
            event.occurredAt(),
            motif
        );
    }

}
