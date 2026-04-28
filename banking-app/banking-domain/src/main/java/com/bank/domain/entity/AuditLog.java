package com.bank.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité de traçabilité réglementaire.
 *
 * <p>Chaque action sensible (ouverture de compte, virement, blocage de carte,
 * connexion, modification de profil…) génère une entrée immuable dans cette table.</p>
 *
 * <p>Durée de conservation : 10 ans (obligation LCB-FT / RGPD art. 17 §3).</p>
 *
 * <p><strong>Important :</strong> les entrées ne sont jamais modifiées ni supprimées
 * — {@code @PreUpdate} lève une exception pour garantir cette immuabilité.</p>
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_user_id",    columnList = "user_id"),
        @Index(name = "idx_audit_entity",     columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_action",     columnList = "action"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    // ─────────────────────────────────────────────────────────
    //  Identité
    // ─────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ─────────────────────────────────────────────────────────
    //  Qui
    // ─────────────────────────────────────────────────────────

    /**
     * Identifiant de l'utilisateur ayant déclenché l'action.
     * Peut être {@code null} pour les actions système automatiques.
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Adresse IP de l'appelant (IPv4 ou IPv6).
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User-Agent de la requête HTTP (navigateur, app mobile, service interne).
     */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    // ─────────────────────────────────────────────────────────
    //  Quoi
    // ─────────────────────────────────────────────────────────

    /**
     * Code de l'action effectuée.
     * Convention : ENTITE_VERBE en majuscules, ex : {@code ACCOUNT_CREATED},
     * {@code TRANSACTION_APPROVED}, {@code CARD_BLOCKED}, {@code USER_LOGIN_FAILED}.
     */
    @Column(name = "action", length = 80, nullable = false)
    @NotBlank
    private String action;

    /**
     * Type de l'entité concernée (ex : {@code "Account"}, {@code "Transaction"}).
     */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /**
     * Identifiant de l'entité concernée.
     */
    @Column(name = "entity_id", length = 36)
    private String entityId;

    // ─────────────────────────────────────────────────────────
    //  Détail
    // ─────────────────────────────────────────────────────────

    /**
     * Résultat de l'opération : {@code SUCCESS} ou {@code FAILURE}.
     */
    @Column(name = "result", length = 10, nullable = false)
    @NotBlank
    private String result;

    /**
     * Message ou description complémentaire (raison d'un échec, détail métier).
     * Données sensibles (PAN, mot de passe) ne doivent jamais apparaître ici.
     */
    @Column(name = "detail", length = 1000)
    private String detail;

    /**
     * Snapshot JSON de l'état de l'entité avant modification (pour audit complet).
     * Optionnel — activé uniquement pour les opérations à fort impact.
     */
    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    /**
     * Snapshot JSON de l'état de l'entité après modification.
     */
    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    // ─────────────────────────────────────────────────────────
    //  Quand
    // ─────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ─────────────────────────────────────────────────────────
    //  Callbacks JPA
    // ─────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Les entrées d'audit sont immuables");
    }

    // ─────────────────────────────────────────────────────────
    //  Factory methods
    // ─────────────────────────────────────────────────────────

    public static AuditLog success(String action, String entityType,
                                   String entityId, UUID userId, String detail) {
        return build(action, entityType, entityId, userId, "SUCCESS", detail, null, null, null);
    }

    public static AuditLog failure(String action, String entityType,
                                   String entityId, UUID userId, String detail) {
        return build(action, entityType, entityId, userId, "FAILURE", detail, null, null, null);
    }

    public static AuditLog withStates(String action, String entityType, String entityId,
                                      UUID userId, String beforeState, String afterState) {
        return build(action, entityType, entityId, userId, "SUCCESS", null, null, beforeState, afterState);
    }

    private static AuditLog build(String action, String entityType, String entityId,
                                  UUID userId, String result, String detail,
                                  String ipAddress, String beforeState, String afterState) {
        AuditLog log = new AuditLog();
        log.action      = action;
        log.entityType  = entityType;
        log.entityId    = entityId;
        log.userId      = userId;
        log.result      = result;
        log.detail      = detail;
        log.ipAddress   = ipAddress;
        log.beforeState = beforeState;
        log.afterState  = afterState;
        return log;
    }

    // ─────────────────────────────────────────────────────────
    //  Builder fluent pour les cas complexes
    // ─────────────────────────────────────────────────────────

    public AuditLog withIpAddress(String ip) {
        this.ipAddress = ip;
        return this;
    }

    public AuditLog withUserAgent(String ua) {
        this.userAgent = ua;
        return this;
    }
}