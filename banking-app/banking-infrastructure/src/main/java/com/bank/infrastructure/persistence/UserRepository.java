package com.bank.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;

@Repository
public interface UserRepository
        extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
	
	Optional<User> findByEmail(String email);
	
    /**
     * Charge l'utilisateur avec ses rôles en une seule requête.
     * Évite le lazy loading sur {@code roles} lors de la construction du JWT.
     */

	@Query("SELECT u from User u JOIN FETCH u.roles where u.email = :email")
	Optional<User> findByEmailWithRoles(@Param("email") String email);
	
	boolean existsByEmail(String email);
	
	boolean existsByPhoneNumber(String phoneNumber);
	
	Optional<User> findByPhoneNumber(String phoneNumber);
	
	
	@Query("SELECT u from User u where email = :value or phoneNumber = :value")
	Optional<User> findByEmailOrPhone(@Param("value") String value);
	
    /**
     * Recherche textuelle sur nom, prénom ou email
     */

	@Query("""
			SELECT u from User u
			WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query , '%'))
			OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query , '%'))
			OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query , '%'))
			ORDER BT u.lastName ASC, u.firstName ASC
			""")
	Page<User> searchByNameOrEmail(@Param("query") String query, Pageable pageable);
	
    /**
     * Utilisateurs filtrés par rôle — gestion des habilitations.
     */

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role ORDER BY u.lastName ASC")
    Page<User> findByRole(@Param("role") UserRole role, Pageable pageable);
 
    /**
     * Utilisateurs dont le KYC n'est pas encore validé — file de traitement compliance.
     */
    Page<User> findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(Pageable pageable);
    
    /**
     * Incrémente le compteur de tentatives échouées.
     * Mis à jour directement en base sans charger l'entité complète.
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.failedLoginAttempts = u.failedLoginAttempts + 1,
            u.updatedAt           = :now
        WHERE u.id = :id
        """)
    int incrementFailedLoginAttempts(@Param("id") UUID id,
                                     @Param("now") LocalDateTime now);
    
    /**
     * Réinitialise le compteur après une connexion réussie.
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.failedLoginAttempts = 0,
            u.lockedUntil         = null,
            u.lastLoginAt         = :now,
            u.updatedAt           = :now
        WHERE u.id = :id
        """)
    int resetLoginAttempts(@Param("id") UUID id,
                           @Param("now") LocalDateTime now);

    /**
     * Verrouille le compte jusqu'à une date donnée.
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.lockedUntil = :until,
            u.updatedAt   = :now
        WHERE u.id = :id
        """)
    int lockUntil(@Param("id")    UUID id,
                  @Param("until") LocalDateTime until,
                  @Param("now")   LocalDateTime now);
 
    /**
     * Active la vérification email.
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.emailVerified = true,
            u.updatedAt     = :now
        WHERE u.id = :id
        """)
    int verifyEmail(@Param("id") UUID id, @Param("now") LocalDateTime now);
 
    /**
     * Valide le KYC d'un utilisateur.
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.kycVerified   = true,
            u.kycVerifiedAt = :now,
            u.updatedAt     = :now
        WHERE u.id = :id
        """)
    int validateKyc(@Param("id") UUID id, @Param("now") LocalDateTime now);
 
    // ─────────────────────────────────────────────────────────
    //  Reporting
    // ─────────────────────────────────────────────────────────
 
    /**
     * Nombre de nouveaux utilisateurs enregistrés sur une période.
     */
    @Query("""
        SELECT COUNT(u) FROM User u
        WHERE u.createdAt BETWEEN :from AND :to
        """)
    long countRegisteredBetween(@Param("from") LocalDateTime from,
                                @Param("to")   LocalDateTime to);
 
    /**
     * Utilisateurs inactifs depuis une date donnée.
     * Utilisé pour les processus de purge RGPD.
     */
    @Query("""
        SELECT u FROM User u
        WHERE (u.lastLoginAt IS NULL OR u.lastLoginAt < :since)
          AND u.enabled = true
          AND u.createdAt < :since
        ORDER BY u.lastLoginAt ASC NULLS FIRST
        """)
    Page<User> findInactiveUsers(@Param("since") LocalDateTime since, Pageable pageable);
 
    /**
     * Comptes désactivés — liste pour audit compliance.
     */
    Page<User> findByEnabledFalseOrderByUpdatedAtDesc(Pageable pageable);
    
    /**
     * Vérifie l'unicité email + téléphone en une seule requête lors de l'inscription.
     */
    @Query("""
        SELECT COUNT(u) > 0 FROM User u
        WHERE u.email = :email OR u.phoneNumber = :phone
        """)
    boolean existsByEmailOrPhone(@Param("email") String email,
                                 @Param("phone") String phone);
 
    /**
     * Vérifie si un utilisateur (autre que lui-même) utilise déjà cet email.
     * Utilisé lors d'une mise à jour de profil.
     */
    @Query("""
        SELECT COUNT(u) > 0 FROM User u
        WHERE u.email = :email AND u.id <> :excludeId
        """)
    boolean existsByEmailAndIdNot(@Param("email")     String email,
                                  @Param("excludeId") UUID excludeId);
 
    /**
     * Charge un utilisateur avec ses comptes — pour le dashboard client.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.accounts WHERE u.id = :id")
    Optional<User> findByIdWithAccounts(@Param("id") UUID id);


}
