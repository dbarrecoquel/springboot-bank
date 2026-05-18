package com.bank.infrastructure.notification;


import com.bank.domain.entity.Notification;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class PushAdapter {
	
    @Value("${banking.firebase.credentials-path:firebase/service-account.json}")
    private String credentialsPath;
 
    @Value("${banking.firebase.project-id:banking-app}")
    private String projectId;
 
    @Value("${banking.push.enabled:true}")
    private boolean enabled;
    
    @PostConstruct
    public void init() {
    	
        if (!enabled) {
            log.info("[PUSH] Firebase FCM désactivé (profil dev/test)");
            return;
        }
        try {
        	InputStream credentialsStream = new ClassPathResource(credentialsPath).getInputStream();
        	FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(credentialsStream))
    															.setProjectId(projectId).build();
        	FirebaseApp.initializeApp(options);
            log.info("[PUSH] Firebase FCM initialisé — projectId={}", projectId);
            
        } catch (IOException ex) {
            log.error("[PUSH] Échec initialisation Firebase — path={} error={}",
                      credentialsPath, ex.getMessage(), ex);
            throw new PushException("Impossible d'initialiser Firebase FCM", ex);
        }
    }
	    
    // ─────────────────────────────────────────────────────────
    //  Envoi unitaire — depuis une Notification
    // ─────────────────────────────────────────────────────────
 
    /**
     * Envoie une notification push à un seul device.
     *
     * @param notification notification (canal PUSH, recipient = device token FCM)
     */
    @Async("notificationExecutor")
    public void send(Notification notification) {
        validateChannel(notification);
 
        if (!enabled) {
            log.info("[PUSH] Envoi désactivé — token={} subject={}",
                     maskToken(notification.getRecipient()), notification.getSubject());
            notification.markSent();
            return;
        }
 
        try {
            Message message = buildMessage(
                notification.getRecipient(),
                notification.getSubject(),
                notification.getBody(),
                Map.of(
                    "notifId",    notification.getId() != null
                                      ? notification.getId().toString() : "",
                    "sourceType", notification.getSourceType() != null
                                      ? notification.getSourceType() : "",
                    "sourceId",   notification.getSourceId() != null
                                      ? notification.getSourceId() : ""
                )
            );
 
            String messageId = FirebaseMessaging.getInstance().send(message);
            notification.markSent();
 
            log.info("[PUSH] Envoyé — token={} messageId={} notifId={}",
                     maskToken(notification.getRecipient()),
                     messageId,
                     notification.getId());
 
        } catch (FirebaseMessagingException ex) {
            notification.markFailed(ex.getMessagingErrorCode() + ": " + ex.getMessage());
            log.error("[PUSH] Échec FCM — token={} errorCode={} message={} notifId={}",
                      maskToken(notification.getRecipient()),
                      ex.getMessagingErrorCode(),
                      ex.getMessage(),
                      notification.getId(), ex);
 
            if (isInvalidToken(ex)) {
                log.warn("[PUSH] Token FCM invalide/expiré — à supprimer : token={}",
                         maskToken(notification.getRecipient()));
                // En production : appeler DeviceTokenService.invalidate(token)
            }
 
            throw new PushException("Échec envoi push FCM", ex);
        }
    }
    
    // ─────────────────────────────────────────────────────────
    //  Envoi multicast — plusieurs devices simultanément
    // ─────────────────────────────────────────────────────────
 
    /**
     * Envoie une notification push à plusieurs devices en une seule requête FCM.
     * Max 500 tokens par appel (limite FCM).
     *
     * @param deviceTokens liste de tokens FCM (max 500)
     * @param title        titre de la notification
     * @param body         corps de la notification
     * @param data         données supplémentaires (payload JSON)
     * @return nombre de messages envoyés avec succès
     */
    @Async("notificationExecutor")
    public int sendMulticast(List<String> deviceTokens, String title,
                              String body, Map<String, String> data) {
        if (!enabled) {
            log.info("[PUSH] Multicast désactivé — {} tokens", deviceTokens.size());
            return deviceTokens.size();
        }
 
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            log.warn("[PUSH] Multicast appelé avec liste vide");
            return 0;
        }
 
        if (deviceTokens.size() > 500) {
            throw new IllegalArgumentException(
                "FCM multicast limité à 500 tokens — reçu : " + deviceTokens.size());
        }
 
        try {
            MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(deviceTokens)
                .setAndroidConfig(buildAndroidConfig(title, body))
                .setApnsConfig(buildApnsConfig(title, body))
                .setWebpushConfig(buildWebpushConfig(title, body));
 
            if (data != null) {
                builder.putAllData(data);
            }
 
            BatchResponse response = FirebaseMessaging.getInstance()
                .sendEachForMulticast(builder.build());
 
            int successCount = response.getSuccessCount();
            int failureCount = response.getFailureCount();
 
            log.info("[PUSH] Multicast terminé — total={} success={} failure={}",
                     deviceTokens.size(), successCount, failureCount);
 
            // Logger les tokens en échec pour nettoyage
            if (failureCount > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse sr = responses.get(i);
                    if (!sr.isSuccessful()) {
                        log.warn("[PUSH] Token en échec — token={} errorCode={}",
                                 maskToken(deviceTokens.get(i)),
                                 sr.getException() != null
                                     ? sr.getException().getMessagingErrorCode()
                                     : "UNKNOWN");
                    }
                }
            }
 
            return successCount;
 
        } catch (FirebaseMessagingException ex) {
            log.error("[PUSH] Échec multicast FCM — tokens={} error={}",
                      deviceTokens.size(), ex.getMessage(), ex);
            throw new PushException("Échec envoi push multicast", ex);
        }
    }
    
    /**
     * Envoie une notification à un topic FCM (tous les abonnés).
     * Ex : topic "fraud-alerts" pour notifier toute l'équipe compliance.
     *
     * @param topic   nom du topic FCM
     * @param title   titre
     * @param body    corps
     * @param data    données additionnelles
     */
    public void sendToTopic(String topic, String title, String body,
                             Map<String, String> data) {
        if (!enabled) {
            log.info("[PUSH] Topic désactivé — topic={}", topic);
            return;
        }
 
        try {
            Message.Builder builder = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(buildAndroidConfig(title, body))
                .setApnsConfig(buildApnsConfig(title, body));
 
            if (data != null) {
                builder.putAllData(data);
            }
 
            String messageId = FirebaseMessaging.getInstance().send(builder.build());
            log.info("[PUSH] Topic envoyé — topic={} messageId={}", topic, messageId);
 
        } catch (FirebaseMessagingException ex) {
            log.error("[PUSH] Échec envoi topic — topic={} error={}", topic, ex.getMessage(), ex);
            throw new PushException("Échec envoi push topic " + topic, ex);
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Construction des messages FCM
    // ─────────────────────────────────────────────────────────
 
    private Message buildMessage(String token, String title, String body,
                                  Map<String, String> data) {
        Message.Builder builder = Message.builder()
            .setToken(token)
            .setAndroidConfig(buildAndroidConfig(title, body))
            .setApnsConfig(buildApnsConfig(title, body))
            .setWebpushConfig(buildWebpushConfig(title, body));
 
        if (data != null) {
            builder.putAllData(data);
        }
 
        return builder.build();
    }
 
    /**
     * Configuration Android — priorité HIGH pour les notifications bancaires urgentes.
     */
    private AndroidConfig buildAndroidConfig(String title, String body) {
        return AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .setNotification(AndroidNotification.builder()
                .setTitle(title)
                .setBody(body)
                .setSound("default")
                .setIcon("ic_notification_banking")
                .setColor("#1A3C8F")   // couleur de la banque
                .setChannelId("banking_alerts")
                .build())
            .build();
    }
 
    /**
     * Configuration APNs (iOS) — badge incrémental + son d'alerte.
     */
    private ApnsConfig buildApnsConfig(String title, String body) {
        return ApnsConfig.builder()
            .setAps(Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
                .setSound("default")
                .setBadge(1)
                .setContentAvailable(true)
                .build())
            .build();
    }
 
    /**
     * Configuration Web Push.
     */
    private WebpushConfig buildWebpushConfig(String title, String body) {
        return WebpushConfig.builder()
            .setNotification(WebpushNotification.builder()
                .setTitle(title)
                .setBody(body)
                .setIcon("/icons/banking-logo-192.png")
                .setBadge("/icons/badge-72.png")
                .build())
            .build();
    }
    /**
     * Indique si l'erreur FCM signifie que le token est invalide/révoqué.
     * Dans ce cas le token doit être supprimé de la base.
     */
    private boolean isInvalidToken(FirebaseMessagingException ex) {
        MessagingErrorCode code = ex.getMessagingErrorCode();
        return code == MessagingErrorCode.UNAVAILABLE
            || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
 
    /**
     * Masque un token FCM pour les logs (tokens très longs ~150 chars).
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 12) return "****";
        return token.substring(0, 6) + "…" + token.substring(token.length() - 6);
    }
 
    private void validateChannel(Notification notification) {
        if (notification.getChannel() != Notification.Channel.PUSH) {
            throw new IllegalArgumentException(
                "PushAdapter ne gère que le canal PUSH — reçu : "
                    + notification.getChannel());
        }
        if (notification.getRecipient() == null
                || notification.getRecipient().isBlank()) {
            throw new IllegalArgumentException(
                "Token FCM manquant — notifId=" + notification.getId());
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Exception interne
    // ─────────────────────────────────────────────────────────
 
    public static class PushException extends RuntimeException {
        public PushException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
