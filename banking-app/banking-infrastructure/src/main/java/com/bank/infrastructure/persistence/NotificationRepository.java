package com.bank.infrastructure.persistence;

import com.bank.domain.entity.Notification;
import com.bank.domain.entity.Notification.Channel;
import com.bank.domain.entity.Notification.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'entité {@link Notification}.
 *
 * <p>Responsabilités principales :</p>
 * <ul>
 *   <li>Lecture des notifications en attente d'envoi ({@code PENDING}).</li>
 *   <li>Lecture des notifications en échec éligibles au retry ({@code FAILED}).</li>
 *   <li>Historique des notifications par utilisateur pour l'IHM.</li>
 *   <li>Mise à jour des statuts après envoi.</li>
 * </ul>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // ─────────────────────────────────────────────────────────
    //  Recherches par utilisateur — IHM
    // ─────────────────────────────────────────────────────────

    /**
     * Historique complet des notifications d'un utilisateur, trié par date décroissante.
     */
    Page<Notification> findByUserIdOrderBySentAtDesc(UUID userId, Pageable pageable);

    /**
     * Notifications non lues (statut SENT) d'un utilisateur sur le canal IN_APP.
     * Utilisé pour le badge de compteur dans l'interface.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.userId  = :userId
          AND n.channel = 'IN_APP'
          AND n.status  = 'SENT'
        ORDER BY n.createdAt DESC
        """)
    List<Notification> findUnreadInApp(@Param("userId") UUID userId);

    /**
     * Nombre de notifications IN_APP non lues — pour le badge compteur.
     */
    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE n.userId  = :userId
          AND n.channel = 'IN_APP'
          AND n.status  = 'SENT'
        """)
    long countUnreadInApp(@Param("userId") UUID userId);

    /**
     * Notifications filtrées par canal et statut.
     */
    Page<Notification> findByUserIdAndChannelAndStatusOrderByCreatedAtDesc(
        UUID userId, Channel channel, Status status, Pageable pageable);

    // ─────────────────────────────────────────────────────────
    //  File d'envoi — PENDING
    // ─────────────────────────────────────────────────────────

    /**
     * Notifications en attente d'envoi par canal.
     * Consommées par les adapters lors du traitement de la file.
     *
     * @param channel canal cible
     * @param limit   nombre maximum de notifications à traiter par batch
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status  = 'PENDING'
          AND n.channel = :channel
        ORDER BY n.createdAt ASC
        """)
    List<Notification> findPendingByChannel(@Param("channel") Channel channel,
                                             Pageable limit);

    // ─────────────────────────────────────────────────────────
    //  Retry — notifications en échec
    // ─────────────────────────────────────────────────────────

    /**
     * Notifications en échec éligibles au retry.
     *
     * <p>Critères :</p>
     * <ul>
     *   <li>Statut {@code FAILED}</li>
     *   <li>Moins de 3 tentatives ({@code retryCount < 3})</li>
     *   <li>Dernière tentative il y a plus de 5 minutes (évite le retry en boucle rapide)</li>
     * </ul>
     *
     * @param before seuil de date — seules les notifications échouées avant cette date
     *               sont éligibles (ex : {@code LocalDateTime.now().minusMinutes(5)})
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status     = 'FAILED'
          AND n.retryCount < 3
          AND n.updatedAt  < :before
        ORDER BY n.updatedAt ASC
        """)
    List<Notification> findRetryableNotifications(@Param("before") LocalDateTime before);

    /**
     * Notifications définitivement en échec (3 tentatives épuisées).
     * Utilisé pour les rapports de monitoring et les alertes opérationnelles.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status     = 'FAILED'
          AND n.retryCount >= 3
          AND n.updatedAt  BETWEEN :from AND :to
        ORDER BY n.updatedAt DESC
        """)
    Page<Notification> findExhaustedNotifications(@Param("from") LocalDateTime from,
                                                   @Param("to")   LocalDateTime to,
                                                   Pageable pageable);

    // ─────────────────────────────────────────────────────────
    //  Mises à jour ciblées
    // ─────────────────────────────────────────────────────────

    /**
     * Marque une notification comme envoyée.
     */
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.status    = 'SENT',
            n.sentAt    = :sentAt,
            n.updatedAt = :sentAt
        WHERE n.id = :id
        """)
    int markSent(@Param("id") UUID id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * Marque une notification comme lue (canal IN_APP uniquement).
     */
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.status    = 'READ',
            n.readAt    = :readAt,
            n.updatedAt = :readAt
        WHERE n.id      = :id
          AND n.channel = 'IN_APP'
        """)
    int markRead(@Param("id") UUID id, @Param("readAt") LocalDateTime readAt);

    /**
     * Marque toutes les notifications IN_APP d'un utilisateur comme lues.
     * Déclenché quand l'utilisateur ouvre le centre de notifications.
     */
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.status    = 'READ',
            n.readAt    = :readAt,
            n.updatedAt = :readAt
        WHERE n.userId  = :userId
          AND n.channel = 'IN_APP'
          AND n.status  = 'SENT'
        """)
    int markAllInAppRead(@Param("userId") UUID userId,
                         @Param("readAt") LocalDateTime readAt);

    /**
     * Incrémente le compteur de retry et enregistre le dernier message d'erreur.
     */
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.retryCount = n.retryCount + 1,
            n.lastError  = :error,
            n.updatedAt  = :now
        WHERE n.id = :id
        """)
    int incrementRetryCount(@Param("id")    UUID id,
                             @Param("error") String error,
                             @Param("now")   LocalDateTime now);

    // ─────────────────────────────────────────────────────────
    //  Reporting — statistiques d'envoi
    // ─────────────────────────────────────────────────────────

    /**
     * Volume de notifications par canal et statut sur une période.
     * Retourne des Object[] : [channel, status, count].
     */
    @Query("""
        SELECT n.channel, n.status, COUNT(n)
        FROM Notification n
        WHERE n.createdAt BETWEEN :from AND :to
        GROUP BY n.channel, n.status
        ORDER BY n.channel, COUNT(n) DESC
        """)
    List<Object[]> volumeByChannelAndStatus(@Param("from") LocalDateTime from,
                                             @Param("to")   LocalDateTime to);

    /**
     * Taux d'échec par canal sur une période.
     * Retourne des Object[] : [channel, total, failed].
     */
    @Query("""
        SELECT n.channel,
               COUNT(n),
               SUM(CASE WHEN n.status = 'FAILED' THEN 1 ELSE 0 END)
        FROM Notification n
        WHERE n.createdAt BETWEEN :from AND :to
        GROUP BY n.channel
        """)
    List<Object[]> failureRateByChannel(@Param("from") LocalDateTime from,
                                         @Param("to")   LocalDateTime to);

    // ─────────────────────────────────────────────────────────
    //  Purge RGPD
    // ─────────────────────────────────────────────────────────

    /**
     * Supprime les notifications anciennes d'un utilisateur.
     * Appelé lors d'une demande de suppression de compte (droit à l'oubli RGPD).
     *
     * @param userId    identifiant de l'utilisateur
     * @param before    date limite (ex : toutes les notifs avant cette date)
     * @return          nombre de notifications supprimées
     */
    @Modifying
    @Query("""
        DELETE FROM Notification n
        WHERE n.userId    = :userId
          AND n.createdAt < :before
        """)
    int deleteByUserIdBefore(@Param("userId") UUID userId,
                              @Param("before") LocalDateTime before);
}