package com.bank.infrastructure.persistence;

import com.bank.domain.entity.Account;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface AccountRepository extends JpaRepository<Account,UUID>, JpaSpecificationExecutor<Account> {

	Optional<Account> findByIban(String iban);
	Optional<Account> findByAccountNumber(String accountNumber);
	boolean existsByIban(String iban);
	boolean existsByAccountNumber(String accountNumber);
	List<Account> findByOwnerIdOrderByCreatedAtDesc(UUID ownerdId);
	Page<Account> findByOwnerId(UUID ownerId, Pageable pageable);
	List<Account> findByOwnerIdAndStatus(UUID ownerId, AccountStatus status);
	List<Account> findByOwnerIdAndType(UUID ownerId, AccountType type);
    /**
     * Vérifie si un utilisateur est bien propriétaire d'un compte.
     * Utilisé pour les contrôles d'autorisation dans les services.
     */
    boolean existsByIdAndOwnerId(UUID accountId, UUID ownerId);
    /**
     * Charge le compte avec son propriétaire en une seule requête (JOIN FETCH)
     * pour éviter le problème N+1 lors des vérifications d'autorisation.
     */
    @Query("SELECT a FROM Account a JOIN FETCH a.owner WHERE a.id = :id")
    Optional<Account> findByIdWithOwner(@Param("id") UUID id);
    /**
     * Charge le compte avec ses transactions récentes (JOIN FETCH).
     */
    @Query("""
        SELECT DISTINCT a FROM Account a
        LEFT JOIN FETCH a.transactions t
        WHERE a.id = :id
        ORDER BY t.createdAt DESC
        """)
    Optional<Account> findByIdWithTransactions(@Param("id") UUID id);
    
    // ─────────────────────────────────────────────────────────
    //  Verrouillage pessimiste — opérations financières
    // ─────────────────────────────────────────────────────────
 
    /**
     * Charge un compte avec verrou exclusif en base de données.
     * À utiliser UNIQUEMENT dans une transaction (@Transactional) active.
     * Garantit qu'aucune autre transaction ne peut modifier le solde
     * simultanément (protection contre les race conditions).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);
    
    /**
     * Verrou partagé — pour les lectures cohérentes dans une transaction longue.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithReadLock(@Param("id") UUID id);

    /**
     * Met à jour le solde directement en base — plus performant que de charger
     * l'entité, modifier, puis sauvegarder pour les opérations à haut volume.
     * Nécessite {@code @Modifying} + {@code @Transactional} côté service.
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = :balance, a.updatedAt = :updatedAt WHERE a.id = :id")
    int updateBalance(@Param("id") UUID id,
                      @Param("balance") BigDecimal balance,
                      @Param("updatedAt") LocalDateTime updatedAt);
    /**
     * Met à jour le statut d'un compte (blocage, clôture).
     */
    @Modifying
    @Query("UPDATE Account a SET a.status = :status, a.updatedAt = :updatedAt WHERE a.id = :id")
    int updateStatus(@Param("id") UUID id,
                     @Param("status") AccountStatus status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Somme des soldes pour un utilisateur et une devise donnés.
     * Utilisé pour les rapports de patrimoine client.
     */
    @Query("""
        SELECT SUM(a.balance)
        FROM Account a
        WHERE a.owner.id = :ownerId
          AND a.currency = :currency
          AND a.status = 'ACTIVE'
        """)
    Optional<BigDecimal> sumBalanceByOwnerAndCurrency(@Param("ownerId") UUID ownerId,
                                                      @Param("currency") CurrencyCode currency);

    

    /**
     * Nombre de comptes par statut — dashboard opérationnel.
     */
    @Query("SELECT a.status, COUNT(a) FROM Account a GROUP BY a.status")
    List<Object[]> countByStatus();
 
    /**
     * Comptes avec un solde débiteur (en situation de découvert).
     * Utilisé par le service de relance.
     */
    @Query("SELECT a FROM Account a WHERE a.balance < 0 AND a.status = 'ACTIVE'")
    Page<Account> findOverdrawnAccounts(Pageable pageable);
 
    /**
     * Comptes actifs sans activité depuis une date donnée.
     * Utilisé pour les processus de clôture dormance (réglementation française).
     */
    @Query("""
        SELECT a FROM Account a
        WHERE a.status = 'ACTIVE'
          AND a.updatedAt < :since
          AND NOT EXISTS (
              SELECT 1 FROM Transaction t
              WHERE t.account.id = a.id
                AND t.createdAt > :since
          )
        """)
    Page<Account> findDormantAccounts(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Comptes dont les cartes expirent bientôt — pour les notifications de renouvellement.
     */
    @Query("""
        SELECT DISTINCT a FROM Account a
        JOIN a.cards c
        WHERE c.status = 'ACTIVE'
          AND c.expiryDate BETWEEN :from AND :to
        """)
    List<Account> findAccountsWithExpiringCards(@Param("from") java.time.LocalDate from,
                                                @Param("to")   java.time.LocalDate to);
}


