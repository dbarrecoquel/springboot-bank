package com.bank.infrastructure.notification;

import com.bank.domain.entity.Notification;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class SmsAdapter {

    private static final int MAX_SMS_LENGTH = 160;
    
    @Value("${banking.twilio.account-sid}")
    private String accountSid;
 
    @Value("${banking.twilio.auth-token}")
    private String authToken;
 
    @Value("${banking.twilio.from-number}")
    private String fromNumber;
 
    @Value("${banking.sms.enabled:true}")
    private boolean enabled;
    
    @PostConstruct
    public void init() {
        if (enabled) {
            Twilio.init(accountSid, authToken);
            log.info("[SMS] Twilio SDK initialisé — from={}", fromNumber);
        } else {
            log.info("[SMS] Twilio désactivé (profil dev/test)");
        }
    }

    /**
     * Envoie un SMS à partir d'une {@link Notification}.
     *
     * @param notification notification à envoyer (canal SMS)
     * @throws IllegalArgumentException si le canal n'est pas SMS
     */
    @Async("notificationExecutor")
    public void send(Notification notification) {
        validateChannel(notification);
 
        if (!enabled) {
            log.info("[SMS] Envoi désactivé — to={} body={}",
                     mask(notification.getRecipient()),
                     truncate(notification.getBody(), 40));
            notification.markSent();
            return;
        }
 
        String body = prepareBody(notification.getBody());
 
        try {
            Message message = Message.creator(
                    new PhoneNumber(notification.getRecipient()),
                    new PhoneNumber(fromNumber),
                    body
                ).create();
 
            notification.markSent();
 
            log.info("[SMS] Envoyé — to={} sid={} status={} notifId={}",
                     mask(notification.getRecipient()),
                     message.getSid(),
                     message.getStatus(),
                     notification.getId());
 
        } catch (ApiException ex) {
            notification.markFailed(
                "Twilio error " + ex.getCode() + ": " + ex.getMessage());
 
            log.error("[SMS] Échec Twilio — to={} code={} message={} notifId={}",
                      mask(notification.getRecipient()),
                      ex.getCode(),
                      ex.getMessage(),
                      notification.getId(), ex);
 
            // Erreurs non-retryables : numéro invalide, non enregistré, blacklisté
            if (isNonRetryable(ex.getCode())) {
                log.warn("[SMS] Erreur non-retryable (code={}) — abandon retry pour notifId={}",
                         ex.getCode(), notification.getId());
                return;
            }
 
            throw new SmsException("Échec envoi SMS à " + mask(notification.getRecipient()), ex);
 
        } catch (Exception ex) {
            notification.markFailed(ex.getMessage());
            log.error("[SMS] Erreur inattendue — to={} notifId={} error={}",
                      mask(notification.getRecipient()),
                      notification.getId(),
                      ex.getMessage(), ex);
            throw new SmsException("Erreur inattendue envoi SMS", ex);
        }
    }
 
    /**
     * Envoie un SMS brut (sans objet Notification).
     * Réservé aux envois internes urgents (OTP, alerte sécurité critique).
     *
     * @param toNumber  numéro E.164
     * @param body      corps du message
     * @return SID Twilio du message envoyé
     */
    public String sendRaw(String toNumber, String body) {
        if (!enabled) {
            log.info("[SMS] Envoi brut désactivé — to={}", mask(toNumber));
            return "MOCK-SID";
        }
 
        try {
            Message message = Message.creator(
                    new PhoneNumber(toNumber),
                    new PhoneNumber(fromNumber),
                    prepareBody(body)
                ).create();
 
            log.info("[SMS] Envoi brut — to={} sid={}", mask(toNumber), message.getSid());
            return message.getSid();
 
        } catch (ApiException ex) {
            log.error("[SMS] Échec envoi brut — to={} code={} error={}",
                      mask(toNumber), ex.getCode(), ex.getMessage(), ex);
            throw new SmsException("Échec envoi SMS brut", ex);
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Helpers privés
    // ─────────────────────────────────────────────────────────
 
    /**
     * Prépare le corps du SMS : tronque à 160 chars et nettoie les caractères spéciaux.
     */
    private String prepareBody(String body) {
        if (body == null) return "";
        // Remplacer les caractères non-GSM basiques pour éviter le multi-part SMS
        String cleaned = body
            .replace('\u2019', '\'')   // apostrophe typographique → apostrophe simple
            .replace('\u2013', '-')    // tiret demi-cadratin
            .replace('\u2014', '-')    // tiret cadratin
            .replace('\u00e9', 'e')    // é → e (pour encodage GSM-7 strict si nécessaire)
            .replace('\u00e0', 'a')
            .replace('\u00e8', 'e')
            .replace('\u00ea', 'e');
 
        if (cleaned.length() > MAX_SMS_LENGTH) {
            log.debug("[SMS] Corps tronqué de {} à {} caractères", cleaned.length(), MAX_SMS_LENGTH);
            return cleaned.substring(0, MAX_SMS_LENGTH - 3) + "...";
        }
        return cleaned;
    }
 
    /**
     * Masque un numéro de téléphone pour les logs.
     * Ex : +33612345678 → +336*****678
     */
    private String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) return "****";
        int visible = 3;
        return phoneNumber.substring(0, phoneNumber.length() - 6)
            + "*****"
            + phoneNumber.substring(phoneNumber.length() - visible);
    }
 
    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
 
    /**
     * Codes d'erreur Twilio non-retryables.
     * Voir : https://www.twilio.com/docs/api/errors
     */
    private boolean isNonRetryable(int code) {
        return switch (code) {
            case 21211 -> true;   // numéro destinataire invalide
            case 21214 -> true;   // numéro destinataire ne peut pas recevoir de SMS
            case 21612 -> true;   // numéro non supporté dans ce pays
            case 21408 -> true;   // permission internationale non activée
            case 21610 -> true;   // destinataire a désactivé les SMS de ce numéro
            case 30003 -> true;   // numéro désactivé/injoignable
            case 30006 -> true;   // numéro de téléphone fixe (non SMS)
            default    -> false;
        };
    }
 
    private void validateChannel(Notification notification) {
        if (notification.getChannel() != Notification.Channel.SMS) {
            throw new IllegalArgumentException(
                "SmsAdapter ne gère que le canal SMS — reçu : "
                    + notification.getChannel());
        }
        if (notification.getRecipient() == null
                || notification.getRecipient().isBlank()) {
            throw new IllegalArgumentException(
                "Numéro de téléphone destinataire manquant — notifId="
                    + notification.getId());
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Exception interne
    // ─────────────────────────────────────────────────────────
 
    public static class SmsException extends RuntimeException {
        public SmsException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
