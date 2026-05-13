package com.bank.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.domain.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
	
    /**
     * Journal d'activité d'un utilisateur — visible par compliance et admin.
     */

	Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable page);

    /**
     * Journal d'activité d'un utilisateur sur une période.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.userId     = :userId
          AND a.createdAt  BETWEEN :from AND :to
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findByUserIdAndPeriod(@Param("userId") UUID userId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);
    
    /**
     * Dernières connexions d'un utilisateur — détection d'activité suspecte.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.userId = :userId
          AND a.action IN ('USER_LOGIN_SUCCESS', 'USER_LOGIN_FAILED')
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findRecentLoginsByUser(@Param("userId") UUID userId,
                                          Pageable pageable);
    
    /**
     * Historique complet d'une entité (compte, carte, transaction…).
     * Utilisé pour l'audit de cycle de vie d'un objet.
     */
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    /**
     * Dernières actions sur une entité — pour les vues de détail.
     */
    @Query("""
    		SELECT a FROM AuditLog a
    		where a.entityType = :entityType
    		and a.entityId = :entityId
    		ORDER BY a.createdAt DESC
    		""")
    List<AuditLog> findLatestByEntity(@Param("entityType") String entityType,@Param("entityId") String entityId, Pageable pageable);
    
    /**
     * Tous les logs d'une action donnée sur une période — monitoring et alertes.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action    = :action
          AND a.createdAt BETWEEN :from AND :to
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findByActionAndPeriod(@Param("action") String action,
                                         @Param("from")   LocalDateTime from,
                                         @Param("to")     LocalDateTime to,
                                         Pageable pageable);
    
    /**
     * Échecs sur une action donnée — détection des tentatives répétées.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action = :action
          AND a.result = 'FAILURE'
          AND a.createdAt > :since
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findFailuresByAction(@Param("action") String action,
                                        @Param("since")  LocalDateTime since,
                                        Pageable pageable);

    /**
     * Logs liés aux événements de fraude sur une période.
     * Utilisé pour les rapports TRACFIN et les dossiers compliance.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action    IN ('TRANSACTION_FRAUD_SUSPECT',
                              'TRANSACTION_BLOCKED',
                              'ACCOUNT_BLOCKED',
                              'FRAUD_ALERT_CREATED')
          AND a.createdAt BETWEEN :from AND :to
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findFraudEvents(@Param("from") LocalDateTime from,
                                   @Param("to")   LocalDateTime to,
                                   Pageable pageable);
 
    /**
     * Toutes les actions effectuées depuis une adresse IP donnée.
     * Utilisé lors des investigations sur un incident de sécurité.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.ipAddress  = :ip
          AND a.createdAt  BETWEEN :from AND :to
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findByIpAndPeriod(@Param("ip")   String ip,
                                     @Param("from") LocalDateTime from,
                                     @Param("to")   LocalDateTime to,
                                     Pageable pageable);
 
    /**
     * Nombre d'échecs de connexion depuis une IP sur une fenêtre glissante.
     * Utilisé par {@code RateLimitFilter} pour détecter les attaques par force brute.
     */
    @Query("""
        SELECT COUNT(a) FROM AuditLog a
        WHERE a.action    = 'USER_LOGIN_FAILED'
          AND a.ipAddress = :ip
          AND a.createdAt > :since
        """)
    long countLoginFailuresByIp(@Param("ip")    String ip,
                                @Param("since") LocalDateTime since);
 
    /**
     * Nombre d'échecs de connexion pour un utilisateur sur une fenêtre glissante.
     * Second niveau de protection contre la force brute (par compte, pas par IP).
     */
    @Query("""
        SELECT COUNT(a) FROM AuditLog a
        WHERE a.action = 'USER_LOGIN_FAILED'
          AND a.userId = :userId
          AND a.createdAt > :since
        """)
    long countLoginFailuresByUser(@Param("userId") UUID userId,
                                  @Param("since")  LocalDateTime since);

    /**
     * Volume d'actions par type sur une période — métriques de monitoring.
     * Retourne des Object[] : [action, count, successCount, failureCount].
     */
    @Query("""
        SELECT a.action,
               COUNT(a),
               SUM(CASE WHEN a.result = 'SUCCESS' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.result = 'FAILURE' THEN 1 ELSE 0 END)
        FROM AuditLog a
        WHERE a.createdAt BETWEEN :from AND :to
        GROUP BY a.action
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> actionVolumeByPeriod(@Param("from") LocalDateTime from,
                                        @Param("to")   LocalDateTime to);

}
