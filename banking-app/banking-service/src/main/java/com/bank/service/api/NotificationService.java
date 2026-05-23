package com.bank.service.api;

import com.bank.domain.entity.Notification;
import com.bank.domain.entity.Notification.Channel;
import com.bank.domain.event.AccountBlockedEvent;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;

import java.util.List;
import java.util.UUID;

/**
 * Interface du service de notifications.
 *
 * <p>Orchestre la création et l'envoi des notifications multicanal
 * (email, SMS, push, in-app) à destination des clients et des opérateurs internes.</p>
 *
 * <p>Deux modes d'utilisation :</p>
 * <ul>
 *   <li><strong>Réactif</strong> — déclenché par les domain events Kafka
 *       ({@link TransactionCreatedEvent}, {@link FraudAlertEvent}, {@link AccountBlockedEvent})
 *       via {@code NotificationEventConsumer}.</li>
 *   <li><strong>Impératif</strong> — appelé directement par les services métier
 *       pour les notifications synchrones (ex : confirmation d'inscription,
 *       envoi d'OTP).</li>
 * </ul>
 */
public interface NotificationService {

    // ─────────────────────────────────────────────────────────
    //  Notifications déclenchées par les domain events
    // ─────────────────────────────────────────────────────────

    /**
     * Notifie le client d'une nouvelle transaction sur son compte.
     * Envoie sur les canaux configurés (email + SMS si montant significatif).
     *
     * @param event événement de création de transaction
     */
    void notifyTransactionCreated(TransactionCreatedEvent event);

    /**
     * Notifie le client et l'équipe compliance d'une alerte fraude.
     * Le message client est volontairement vague pour ne pas exposer les règles.
     *
     * @param event alerte fraude
     */
    void notifyFraudAlert(FraudAlertEvent event);

    /**
     * Notifie le client du blocage de son compte.
     * Le ton et le contenu varient selon la réversibilité du blocage.
     *
     * @param event événement de blocage de compte
     */
    void notifyAccountBlocked(AccountBlockedEvent event);

    // ─────────────────────────────────────────────────────────
    //  Notifications impératives — authentification
    // ─────────────────────────────────────────────────────────

    /**
     * Envoie un code OTP par SMS pour la double authentification (2FA).
     *
     * @param userId      identifiant de l'utilisateur
     * @param phoneNumber numéro E.164 du destinataire
     * @param otpCode     code à 6 chiffres
     */
    void sendOtpSms(UUID userId, String phoneNumber, String otpCode);

    /**
     * Envoie un email de vérification d'adresse email lors de l'inscription.
     *
     * @param userId            identifiant de l'utilisateur
     * @param email             adresse à vérifier
     * @param verificationToken token de vérification (lien cliquable)
     * @param fullName          nom complet pour la personnalisation
     */
    void sendEmailVerification(UUID userId, String email,
                                String verificationToken, String fullName);

    /**
     * Envoie un email de réinitialisation de mot de passe.
     *
     * @param userId      identifiant de l'utilisateur
     * @param email       adresse destinataire
     * @param resetToken  token de réinitialisation (validité 15 min)
     * @param fullName    nom complet
     */
    void sendPasswordReset(UUID userId, String email,
                            String resetToken, String fullName);

    /**
     * Notifie l'utilisateur d'une connexion depuis un nouvel appareil.
     *
     * @param userId    identifiant de l'utilisateur
     * @param email     adresse destinataire
     * @param ipAddress adresse IP de la connexion
     * @param userAgent navigateur / application
     * @param loginTime horodatage de la connexion
     */
    void sendNewDeviceLoginAlert(UUID userId, String email, String ipAddress,
                                  String userAgent, String loginTime);

    // ─────────────────────────────────────────────────────────
    //  Notifications impératives — opérations bancaires
    // ─────────────────────────────────────────────────────────

    /**
     * Notifie le client de l'activation de sa nouvelle carte bancaire.
     *
     * @param userId     identifiant du client
     * @param email      adresse destinataire
     * @param maskedPan  PAN masqué : **** **** **** 4242
     * @param cardType   type de carte (ex : "Visa Platinum")
     */
    void sendCardActivated(UUID userId, String email,
                            String maskedPan, String cardType);

    /**
     * Notifie le client du blocage de sa carte.
     *
     * @param userId    identifiant du client
     * @param email     adresse destinataire
     * @param maskedPan PAN masqué
     * @param reason    motif du blocage (lisible, sans détail sensible)
     */
    void sendCardBlocked(UUID userId, String email,
                          String maskedPan, String reason);

    /**
     * Notifie le client d'un dépassement de plafond journalier.
     *
     * @param userId    identifiant du client
     * @param email     adresse destinataire
     * @param phone     numéro de téléphone (nullable)
     * @param limitType type de plafond : "paiement" ou "retrait"
     * @param limit     montant du plafond
     * @param currency  devise
     */
    void sendDailyLimitReached(UUID userId, String email, String phone,
                                String limitType, String limit, String currency);

    /**
     * Notifie le client de l'expiration prochaine de sa carte.
     *
     * @param userId     identifiant du client
     * @param email      adresse destinataire
     * @param maskedPan  PAN masqué
     * @param expiryDate date d'expiration (format MM/YY)
     * @param daysLeft   nombre de jours restants
     */
    void sendCardExpiryReminder(UUID userId, String email,
                                 String maskedPan, String expiryDate, int daysLeft);

    // ─────────────────────────────────────────────────────────
    //  Envoi générique
    // ─────────────────────────────────────────────────────────

    /**
     * Envoie une notification sur un canal spécifique.
     * Méthode bas niveau — préférer les méthodes métier ci-dessus.
     *
     * @param notification notification à envoyer
     */
    void send(Notification notification);

    /**
     * Envoie une notification sur plusieurs canaux simultanément.
     *
     * @param userId    identifiant du destinataire
     * @param channels  canaux cibles
     * @param subject   sujet (email uniquement)
     * @param body      corps du message
     * @param sourceType type de l'entité source (ex : "Transaction")
     * @param sourceId   identifiant de l'entité source
     */
    void sendMultiChannel(UUID userId, List<Channel> channels,
                           String subject, String body,
                           String sourceType, String sourceId);

    // ─────────────────────────────────────────────────────────
    //  Retry
    // ─────────────────────────────────────────────────────────

    /**
     * Rejoue les notifications en échec éligibles au retry.
     * Appelé par un scheduler ({@code NotificationRetryScheduler}) toutes les 5 min.
     *
     * @return nombre de notifications relancées
     */
    int retryFailedNotifications();
}