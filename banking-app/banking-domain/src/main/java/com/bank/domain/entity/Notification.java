package com.bank.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_user_id",  columnList = "user_id"),
        @Index(name = "idx_notif_status",   columnList = "status"),
        @Index(name = "idx_notif_channel",  columnList = "channel"),
        @Index(name = "idx_notif_sent_at",  columnList = "sent_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Notification {
    // ─────────────────────────────────────────────────────────
    //  Canal d'envoi
    // ─────────────────────────────────────────────────────────
 
    public enum Channel {
        EMAIL, SMS, PUSH, IN_APP
    }
 
    public enum Status {
        PENDING,  // en attente d'envoi
        SENT,     // envoyé avec succès
        FAILED,   // échec d'envoi
        READ      // lu par l'utilisateur (IN_APP uniquement)
    }
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    
    // ─────────────────────────────────────────────────────────
    //  Destinataire
    // ─────────────────────────────────────────────────────────
 
    @Column(name = "user_id", nullable = false)
    @NotNull
    private UUID userId;
 
    /**
     * Adresse de destination résolue au moment de l'envoi :
     * adresse email, numéro E.164, device token FCM.
     */
    @Column(name = "recipient", length = 255)
    private String recipient;
    
 // ─────────────────────────────────────────────────────────
    //  Contenu
    // ─────────────────────────────────────────────────────────
 
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10, nullable = false)
    @NotNull
    private Channel channel;
 
    @Column(name = "subject", length = 200)
    @Size(max = 200)
    private String subject;
 
    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Le contenu de la notification est obligatoire")
    private String body;
    
    /**
     * Clé du template utilisé (ex : {@code "transaction.credit.confirmed"}).
     * Permet de retrouver le template Thymeleaf/Freemarker.
     */
    @Column(name = "template_key", length = 100)
    private String templateKey;
 
    // ─────────────────────────────────────────────────────────
    //  Statut & envoi
    // ─────────────────────────────────────────────────────────
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    @NotNull
    private Status status = Status.PENDING;
 
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
 
    @Column(name = "read_at")
    private LocalDateTime readAt;
 
    /**
     * Nombre de tentatives d'envoi (pour les politiques de retry).
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;
 
    @Column(name = "last_error", length = 500)
    private String lastError;
 
    // ─────────────────────────────────────────────────────────
    //  Référence métier
    // ─────────────────────────────────────────────────────────
 
    /**
     * Type de l'entité qui a déclenché la notification
     * (ex : {@code "Transaction"}, {@code "Account"}).
     */
    @Column(name = "source_type", length = 50)
    private String sourceType;
 
    /**
     * Identifiant de l'entité source.
     */
    @Column(name = "source_id", length = 36)
    private String sourceId;
 
    // ─────────────────────────────────────────────────────────
    //  Audit
    // ─────────────────────────────────────────────────────────
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
 
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
 
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // ─────────────────────────────────────────────────────────
    //  Factory methods
    // ─────────────────────────────────────────────────────────
 
    public static Notification email(UUID userId, String recipient,
                                     String subject, String body, String templateKey) {
        Notification n = new Notification();
        n.userId      = userId;
        n.recipient   = recipient;
        n.channel     = Channel.EMAIL;
        n.subject     = subject;
        n.body        = body;
        n.templateKey = templateKey;
        return n;
    }
    public static Notification sms(UUID userId, String phoneNumber, String body) {
        Notification n = new Notification();
        n.userId    = userId;
        n.recipient = phoneNumber;
        n.channel   = Channel.SMS;
        n.body      = body;
        return n;
    }
    public static Notification push(UUID userId, String deviceToken,
            String subject, String body) {
		Notification n = new Notification();
		n.userId    = userId;
		n.recipient = deviceToken;
		n.channel   = Channel.PUSH;
		n.subject   = subject;
		n.body      = body;
		return n;
	}
    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = LocalDateTime.now();
    }
 
    public void markFailed(String error) {
        this.status    = Status.FAILED;
        this.lastError = error;
        this.retryCount++;
    }
 
    public void markRead() {
        if (this.channel != Channel.IN_APP) {
            throw new IllegalStateException("Seules les notifications IN_APP peuvent être marquées comme lues");
        }
        this.status = Status.READ;
        this.readAt = LocalDateTime.now();
    }
 
    public boolean canRetry() {
        return this.status == Status.FAILED && this.retryCount < 3;
    }
}
