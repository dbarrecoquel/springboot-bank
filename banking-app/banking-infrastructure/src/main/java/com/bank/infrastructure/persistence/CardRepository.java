package com.bank.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.domain.entity.Card;
import com.bank.domain.enums.CardStatus;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

	List<Card> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
	List<Card> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
	List<Card> findByOwnerIdAndStatus(UUID ownerId, CardStatus status);
	List<Card> findByAccountIdAndStatus(UUID accountId, CardStatus status);
	boolean existsByIdAndOwnerId(UUID cardId, UUID ownerdId);
	
    /**
     * Carte active d'un compte — au plus une carte active par compte
     * dans la configuration standard.
     */

	Page<Card> findByAccountIdAndStatus(UUID accountId, CardStatus status, Pageable pageable);
	
    /**
     * Charge la carte avec son propriétaire et son compte en JOIN FETCH.
     */

	@Query("""
			SELECT c FROM Card c
			JOIN FETCH c.owner
			JOIN FETCH c.account
			WHERE c.id = :id
			""")
	Optional<Card> findByIdWithOwnerAndAccount(@Param("id") UUID id);
	
    /**
     * Cartes expirant entre deux dates — pour les notifications et le renouvellement.
     */
	@Query("""
			SELECT c FROM Card c
			where c.status = 'Active'
			AND c.expiryDate BETWEEN :from AND :to
			ORDER BY c.expiryDate ASC
			""")
	List<Card> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
	
	/**
     * Cartes déjà expirées mais toujours en statut ACTIVE.
     * Traitées par {@code CardExpiryScheduler} pour passage en EXPIRED.
     */
	@Query("""
			SELECT c FROM Card c
			where c.status = 'Active'
			AND c.expiryDate < :today
			""")
	List<Card> findExpiredNotYetMarked(@Param("today") LocalDate today);
	
    /**
     * Mise à jour du statut de la carte.
     */
    @Modifying
    @Query("""
        UPDATE Card c
        SET c.status    = :status,
            c.updatedAt = :now
        WHERE c.id = :id
        """)
    int updateStatus(@Param("id")     UUID id,
                     @Param("status") CardStatus status,
                     @Param("now")    LocalDateTime now);
    
    /**
     * Bloque toutes les cartes actives d'un compte.
     * Déclenché lors du blocage du compte ({@code AccountBlockedEvent}).
     */
    @Modifying
    @Query("""
        UPDATE Card c
        SET c.status    = 'BLOCKED',
            c.blockedAt = :now,
            c.updatedAt = :now
        WHERE c.account.id = :accountId
          AND c.status     = 'ACTIVE'
        """)
    int blockAllActiveByAccount(@Param("accountId") UUID accountId,
                                @Param("now")       LocalDateTime now);

    /**
     * Annule toutes les cartes d'un compte lors de la clôture.
     */
    @Modifying
    @Query("""
    		UPDATE Card c
    		SET c.status = 'CANCELLED',
    		c.updatedAt = :now
    		WHERE c.account.id = :accountId
    		AND c.status NOT IN ('CANCELLED', 'EXPIRED')
    		""")
    int cancelAllByAccount(@Param("accountId") UUID accountId, @Param("now") LocalDateTime now );
    
    /**
     * Incrémente le compteur de tentatives PIN erronées.
     */
    @Modifying
    @Query("""
        UPDATE Card c
        SET c.pinAttempts = c.pinAttempts + 1,
            c.updatedAt   = :now
        WHERE c.id = :id
        """)
    int incrementPinAttempts(@Param("id") UUID id, @Param("now") LocalDateTime now);
    
    /**
     * Bloque la carte après trop de tentatives PIN erronées.
     */
    
    @Modifying
    @Query("""
    		UPDATE Card c
    		SET c.pinBlocked = true,
    		c.status = 'BLOCKED',
    		c.blockedAt = :now ,
    		c.updatedAt = :now
    		WHERE c.id = :id
    		""")
    int blockByPinFailure(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Réinitialise le compteur PIN après déblocage.
     */
    @Modifying
    @Query("""
        UPDATE Card c
        SET c.pinAttempts = 0,
            c.pinBlocked  = false,
            c.updatedAt   = :now
        WHERE c.id = :id
        """)
    int resetPinAttempts(@Param("id") UUID id, @Param("now") LocalDateTime now);
    
   /**
    * Nombre de cartes par statut — dashboard opérationnel.
    */
   @Query("SELECT c.status, COUNT(c) FROM Card c GROUP BY c.status")
   List<Object[]> countByStatus();
   
   /**
    * Cartes bloquées pour PIN — file de traitement support client.
    */
   Page<Card> findByPinBlockedTrueOrderByUpdatedAtDesc(Pageable pageable);

}
