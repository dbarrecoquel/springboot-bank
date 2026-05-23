package com.bank.service.impl;

import com.bank.common.exception.BankingException;
import com.bank.domain.entity.Notification;
import com.bank.domain.entity.Notification.Channel;
import com.bank.domain.entity.User;
import com.bank.domain.event.AccountBlockedEvent;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;
import com.bank.infrastructure.notification.EmailAdapter;
import com.bank.infrastructure.notification.PushAdapter;
import com.bank.infrastructure.notification.SmsAdapter;
import com.bank.infrastructure.persistence.NotificationRepository;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.api.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du service de notifications.
 *
 * <p>Responsabilités :</p>
 * <ol>
 *   <li>Construire les objets {@link Notification} avec le bon canal et contenu.</li>
 *   <li>Persister la notification avant envoi (permet le retry en cas d'échec).</li>
 *   <li>Déléguer l'envoi physique aux adapters ({@link EmailAdapter},
 *       {@link SmsAdapter}, {@link PushAdapter}).</li>
 *   <li>Mettre à jour le statut après envoi.</li>
 * </ol>
 *
 * <p>Tous les envois sont <strong>non-bloquants</strong> — les adapters s'exécutent
 * dans un thread pool dédié ({@code @Async("notificationExecutor")}).</p>
 *
 * <p>Les méthodes déclenchées par des domain events ne propagent jamais d'exception —
 * un échec de notification ne doit pas annuler la transaction bancaire parente.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final EmailAdapter           emailAdapter;
    private final SmsAdapter             smsAdapter;
    private final PushAdapter            pushAdapter;
    private final UserRepository         userRepository;
    private final NotificationRepository notificationRepository;

    @Value("${banking.app.url:https://app.bank.com}")
    private String appUrl;

    @Value("${banking.notification.sms-amount-threshold:100}")
    private BigDecimal smsAmountThreshold;

    // ─────────────────────────────────────────────────────────
    //  Notifications — domain events
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void notifyTransactionCreated(TransactionCreatedEvent event) {
        userRepository.findById(event.userId()).ifPresentOrElse(
            user -> {
                // Email — systématique si email vérifié
                if (user.isEmailVerified()) {
                    Notification email = buildTransactionEmail(event, user);
                    persist(email);
                    emailAdapter.send(email);
                }

                // SMS — si téléphone vérifié et montant > seuil
                if (user.isPhoneVerified()
                        && event.amount().compareTo(smsAmountThreshold) > 0) {
                    Notification sms = buildTransactionSms(event, user);
                    persist(sms);
                    smsAdapter.send(sms);
                }

                log.info("[NOTIF] Transaction notifiée — ref={} userId={} email={} sms={}",
                         event.reference(), event.userId(),
                         user.isEmailVerified(), user.isPhoneVerified());
            },
            () -> log.warn("[NOTIF] Utilisateur introuvable — userId={}", event.userId())
        );
    }

    @Override
    @Transactional
    public void notifyFraudAlert(FraudAlertEvent event) {
        userRepository.findById(event.userId()).ifPresentOrElse(
            user -> {
                // Email client — message volontairement vague
                if (user.isEmailVerified()) {
                    Notification email = Notification.email(
                        user.getId(),
                        user.getEmail(),
                        "Alerte sécurité — activité inhabituelle détectée",
                        buildFraudAlertClientBody(event),
                        "fraud.alert.client"
                    );
                    email.setSourceType("Transaction");
                    email.setSourceId(event.transactionId().toString());
                    persist(email);
                    emailAdapter.send(email);
                }

                // SMS — systématique pour les alertes fraude
                if (user.isPhoneVerified()) {
                    Notification sms = Notification.sms(
                        user.getId(),
                        user.getPhoneNumber(),
                        "BANQUE ALERTE : activité suspecte sur votre compte. " +
                        "Pas vous ? Appelez le 09 XX XX XX XX immédiatement."
                    );
                    persist(sms);
                    smsAdapter.send(sms);
                }

                log.warn("[NOTIF] Fraude notifiée — txRef={} severity={} userId={}",
                         event.transactionReference(), event.severity(), event.userId());
            },
            () -> log.warn("[NOTIF] Utilisateur introuvable pour alerte fraude — userId={}",
                           event.userId())
        );
    }

    @Override
    @Transactional
    public void notifyAccountBlocked(AccountBlockedEvent event) {
        userRepository.findById(event.userId()).ifPresentOrElse(
            user -> {
                String subject = "Votre compte a été bloqué";
                String body    = buildAccountBlockedBody(event);

                if (user.isEmailVerified()) {
                    Notification email = Notification.email(
                        user.getId(), user.getEmail(), subject, body, "account.blocked"
                    );
                    email.setSourceType("Account");
                    email.setSourceId(event.accountId().toString());
                    persist(email);
                    emailAdapter.send(email);
                }

                if (user.isPhoneVerified()) {
                    Notification sms = Notification.sms(
                        user.getId(),
                        user.getPhoneNumber(),
                        "BANQUE : Votre compte " + event.maskedIban() +
                        " a été bloqué. Contactez le 09 XX XX XX XX."
                    );
                    persist(sms);
                    smsAdapter.send(sms);
                }

                log.info("[NOTIF] Blocage compte notifié — accountId={} reason={}",
                         event.accountId(), event.reason());
            },
            () -> log.warn("[NOTIF] Utilisateur introuvable pour blocage — userId={}",
                           event.userId())
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Notifications — authentification
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void sendOtpSms(UUID userId, String phoneNumber, String otpCode) {
        String body = "BANQUE : Votre code de vérification est " + otpCode +
                      ". Valable 10 minutes. Ne le communiquez jamais.";

        Notification sms = Notification.sms(userId, phoneNumber, body);
        persist(sms);
        smsAdapter.send(sms);

        log.info("[NOTIF] OTP SMS envoyé — userId={} phone={}",
                 userId, maskPhone(phoneNumber));
    }

    @Override
    @Transactional
    public void sendEmailVerification(UUID userId, String email,
                                       String verificationToken, String fullName) {
        String verifyUrl = appUrl + "/auth/verify-email?token=" + verificationToken;
        String body = "Bonjour " + fullName + ",\n\n" +
                      "Cliquez sur ce lien pour vérifier votre adresse email :\n" +
                      verifyUrl + "\n\nCe lien expire dans 24 heures.";

        Notification notif = Notification.email(
            userId, email,
            "Vérifiez votre adresse email",
            body, "auth.email.verify"
        );
        persist(notif);
        emailAdapter.send(notif);

        log.info("[NOTIF] Email de vérification envoyé — userId={} email={}",
                 userId, maskEmail(email));
    }

    @Override
    @Transactional
    public void sendPasswordReset(UUID userId, String email,
                                   String resetToken, String fullName) {
        String resetUrl = appUrl + "/auth/reset-password?token=" + resetToken;
        String body = "Bonjour " + fullName + ",\n\n" +
                      "Vous avez demandé la réinitialisation de votre mot de passe.\n" +
                      "Cliquez ici : " + resetUrl + "\n\n" +
                      "Ce lien expire dans 15 minutes.\n" +
                      "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.";

        Notification notif = Notification.email(
            userId, email,
            "Réinitialisation de votre mot de passe",
            body, "auth.password.reset"
        );
        persist(notif);
        emailAdapter.send(notif);

        log.info("[NOTIF] Email reset mot de passe envoyé — userId={}", userId);
    }

    @Override
    @Transactional
    public void sendNewDeviceLoginAlert(UUID userId, String email, String ipAddress,
                                         String userAgent, String loginTime) {
        String body = "Une connexion depuis un nouvel appareil a été détectée :\n\n" +
                      "IP       : " + ipAddress + "\n" +
                      "Appareil : " + userAgent + "\n" +
                      "Date     : " + loginTime + "\n\n" +
                      "Si ce n'était pas vous, changez votre mot de passe immédiatement\n" +
                      "et contactez le 09 XX XX XX XX.";

        Notification notif = Notification.email(
            userId, email,
            "Connexion depuis un nouvel appareil",
            body, "auth.new.device"
        );
        persist(notif);
        emailAdapter.send(notif);

        log.warn("[NOTIF] Alerte nouveau device — userId={} ip={}", userId, ipAddress);
    }

    // ─────────────────────────────────────────────────────────
    //  Notifications — opérations bancaires
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void sendCardActivated(UUID userId, String email,
                                   String maskedPan, String cardType) {
        String body = "Votre carte " + cardType + " (" + maskedPan + ") " +
                      "a été activée avec succès.\n\n" +
                      "Vous pouvez l'utiliser immédiatement pour vos paiements.";

        Notification notif = Notification.email(
            userId, email,
            "Votre carte est activée",
            body, "card.activated"
        );
        persist(notif);
        emailAdapter.send(notif);

        log.info("[NOTIF] Activation carte notifiée — userId={} pan={}", userId, maskedPan);
    }

    @Override
    @Transactional
    public void sendCardBlocked(UUID userId, String email,
                                 String maskedPan, String reason) {
        String body = "Votre carte (" + maskedPan + ") a été bloquée.\n" +
                      "Motif : " + reason + "\n\n" +
                      "Contactez votre conseiller pour la débloquer : 09 XX XX XX XX.";

        Notification notif = Notification.email(
            userId, email,
            "Votre carte a été bloquée",
            body, "card.blocked"
        );
        persist(notif);
        emailAdapter.send(notif);

        log.info("[NOTIF] Blocage carte notifié — userId={} pan={}", userId, maskedPan);
    }

    @Override
    @Transactional
    public void sendDailyLimitReached(UUID userId, String email, String phone,
                                       String limitType, String limit, String currency) {
        String subject = "Plafond journalier de " + limitType + " atteint";
        String body    = "Vous avez atteint votre plafond journalier de " + limitType +
                         " : " + limit + " " + currency + ".\n\n" +
                         "Aucune opération de " + limitType +
                         " supplémentaire ne sera autorisée aujourd'hui.\n" +
                         "Pour modifier vos plafonds : " + appUrl + "/settings/limits";

        if (email != null) {
            Notification emailNotif = Notification.email(userId, email, subject, body,
                                                         "card.limit.reached");
            persist(emailNotif);
            emailAdapter.send(emailNotif);
        }

        if (phone != null) {
            Notification sms = Notification.sms(userId, phone,
                "BANQUE : Plafond " + limitType + " journalier atteint (" +
                limit + " " + currency + "). Modifiez-le dans l'appli.");
            persist(sms);
            smsAdapter.send(sms);
        }

        log.info("[NOTIF] Plafond atteint notifié — userId={} type={} limit={}{}",
                 userId, limitType, limit, currency);
    }

    @Override
    @Transactional
    public void sendCardExpiryReminder(UUID userId, String email,
                                        String maskedPan, String expiryDate, int daysLeft) {
        String body = "Votre carte (" + maskedPan + ") expire le " + expiryDate +
                      " (dans " + daysLeft + " jours).\n\n" +
                      "Une nouvelle carte vous sera envoyée automatiquement.\n" +
                      "Si vous n'avez rien reçu dans 5 jours, contactez le 09 XX XX XX XX.";

        Notification notif = Notification.email(
            userId, email,
            "Votre carte expire bientôt",
            body, "card.expiry.reminder"
        );
        persist(notif);
        emailAdapter.send(notif);

        log.info("[NOTIF] Rappel expiration carte — userId={} pan={} daysLeft={}",
                 userId, maskedPan, daysLeft);
    }

    // ─────────────────────────────────────────────────────────
    //  Envoi générique
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void send(Notification notification) {
        persist(notification);
        dispatch(notification);
    }

    @Override
    @Transactional
    public void sendMultiChannel(UUID userId, List<Channel> channels,
                                  String subject, String body,
                                  String sourceType, String sourceId) {
        userRepository.findById(userId).ifPresent(user -> {
            for (Channel channel : channels) {
                Notification notif = buildForChannel(
                    channel, user, subject, body, sourceType, sourceId);
                if (notif != null) {
                    persist(notif);
                    dispatch(notif);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    //  Retry des notifications en échec
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public int retryFailedNotifications() {
        List<Notification> failed = notificationRepository
            .findRetryableNotifications(LocalDateTime.now());

        int retried = 0;
        for (Notification notif : failed) {
            if (!notif.canRetry()) continue;
            try {
                dispatch(notif);
                retried++;
                log.info("[NOTIF] Retry réussi — notifId={} canal={} attempt={}",
                         notif.getId(), notif.getChannel(), notif.getRetryCount());
            } catch (Exception ex) {
                log.warn("[NOTIF] Retry échoué — notifId={} attempt={} error={}",
                         notif.getId(), notif.getRetryCount(), ex.getMessage());
            }
        }

        if (retried > 0) {
            log.info("[NOTIF] Retry terminé — {}/{} notifications relancées",
                     retried, failed.size());
        }

        return retried;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers privés — construction des notifications
    // ─────────────────────────────────────────────────────────

    private Notification buildTransactionEmail(TransactionCreatedEvent event, User user) {
        String subject = buildTransactionSubject(event);
        String body    = "Opération enregistrée sur votre compte.\n" +
                         "Référence : " + event.reference() + "\n" +
                         "Montant   : " + event.amount() + " " + event.currency() + "\n" +
                         (event.counterpartName() != null
                             ? "Bénéficiaire : " + event.counterpartName() + "\n" : "") +
                         "Date      : " + event.occurredAt();

        Notification notif = Notification.email(
            user.getId(), user.getEmail(), subject, body, "transaction.created");
        notif.setSourceType("Transaction");
        notif.setSourceId(event.transactionId().toString());
        return notif;
    }

    private Notification buildTransactionSms(TransactionCreatedEvent event, User user) {
        String body = "BANQUE : " + event.type().getLabel() +
                      " " + event.amount() + " " + event.currency() +
                      " — Réf: " + event.reference() +
                      ". Pas vous ? Appelez le 09 XX XX XX XX.";
        Notification sms = Notification.sms(user.getId(), user.getPhoneNumber(), body);
        sms.setSourceType("Transaction");
        sms.setSourceId(event.transactionId().toString());
        return sms;
    }

    private String buildTransactionSubject(TransactionCreatedEvent event) {
        return switch (event.type()) {
            case SEPA_TRANSFER, INTERNAL_TRANSFER, INTERNATIONAL_TRANSFER ->
                "Virement de " + event.amount() + " " + event.currency();
            case CARD_PAYMENT ->
                "Paiement de " + event.amount() + " " + event.currency();
            case CASH_DEPOSIT ->
                "Dépôt de " + event.amount() + " " + event.currency();
            case CASH_WITHDRAWAL ->
                "Retrait de " + event.amount() + " " + event.currency();
            default ->
                "Opération " + event.reference() + " — " + event.amount() + " " + event.currency();
        };
    }

    private String buildFraudAlertClientBody(FraudAlertEvent event) {
        return "Une activité inhabituelle a été détectée sur votre compte.\n\n" +
               "Une opération a été bloquée à titre préventif.\n" +
               "Référence : " + event.transactionReference() + "\n" +
               "Date      : " + event.occurredAt().toLocalDate() + "\n\n" +
               "Si vous êtes à l'origine de cette opération, contactez le 09 XX XX XX XX.\n" +
               "Dans le cas contraire, vos fonds sont protégés.";
    }

    private String buildAccountBlockedBody(AccountBlockedEvent event) {
        String motif = event.isPotentiallyReversible()
            ? "Cette mesure est temporaire. Contactez votre conseiller."
            : "Cette mesure fait suite à une procédure réglementaire.";

        return "Votre compte " + event.maskedIban() + " a été bloqué.\n\n" +
               "Motif  : " + event.reason().getLabel() + "\n" +
               "Date   : " + event.occurredAt() + "\n\n" +
               motif + "\n\n" +
               "Service client : 09 XX XX XX XX (lun-ven 8h-19h)";
    }

    private Notification buildForChannel(Channel channel, User user,
                                          String subject, String body,
                                          String sourceType, String sourceId) {
        Notification notif = switch (channel) {
            case EMAIL -> user.isEmailVerified()
                ? Notification.email(user.getId(), user.getEmail(), subject, body, null)
                : null;
            case SMS -> user.isPhoneVerified()
                ? Notification.sms(user.getId(), user.getPhoneNumber(), body)
                : null;
            case PUSH -> null; // token FCM géré par DeviceTokenService
            case IN_APP -> null; // géré par WebSocket
        };

        if (notif != null && sourceType != null) {
            notif.setSourceType(sourceType);
            notif.setSourceId(sourceId);
        }
        return notif;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers privés — persistance et dispatch
    // ─────────────────────────────────────────────────────────

    private void persist(Notification notification) {
        try {
            notificationRepository.save(notification);
        } catch (Exception ex) {
            log.error("[NOTIF] Échec persistance notification — canal={} userId={} error={}",
                      notification.getChannel(), notification.getUserId(), ex.getMessage());
        }
    }

    private void dispatch(Notification notification) {
        try {
            switch (notification.getChannel()) {
                case EMAIL -> emailAdapter.send(notification);
                case SMS   -> smsAdapter.send(notification);
                case PUSH  -> pushAdapter.send(notification);
                default    -> log.warn("[NOTIF] Canal non géré : {}", notification.getChannel());
            }
        } catch (Exception ex) {
            log.error("[NOTIF] Échec dispatch — canal={} notifId={} error={}",
                      notification.getChannel(), notification.getId(), ex.getMessage());
            notification.markFailed(ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Masquage pour les logs
    // ─────────────────────────────────────────────────────────

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        String[] parts = email.split("@");
        String   local = parts[0];
        return (local.length() > 2
            ? local.substring(0, 2) + "***"
            : "***") + "@" + parts[1];
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "****";
        return phone.substring(0, phone.length() - 4) + "****";
    }
}