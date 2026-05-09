package com.bank.infrastructure.persistence;

import com.bank.domain.entity.Transaction;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // ─────────────────────────────────────────────────────────
    //  Recherches simples
    // ─────────────────────────────────────────────────────────

    Optional<Transaction> findByReference(String reference);

    boolean existsByReference(String reference);

    // ─────────────────────────────────────────────────────────
    //  Relevé de compte paginé
    // ─────────────────────────────────────────────────────────

    /**
     * Historique complet d'un compte, trié par date décroissante.
     * Point d'entrée principal pour les relevés de compte.
     */
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * Historique filtré par statut.
     */
    Page<Transaction> findByAccountIdAndStatusOrderByCreatedAtDesc(
            UUID accountId, TransactionStatus status, Pageable pageable);

    /**
     * Historique filtré par type.
     */
    Page<Transaction> findByAccountIdAndTypeOrderByCreatedAtDesc(
            UUID accountId, TransactionType type, Pageable pageable);

    /**
     * Historique sur une période donnée — pour les relevés mensuels ou annuels.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByAccountIdAndPeriod(@Param("accountId") UUID accountId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to")   LocalDateTime to,
                                               Pageable pageable);

    /**
     * Transactions d'un compte sur une période, avec leur compte chargé en JOIN FETCH
     * (évite N+1 lors de la génération de relevés PDF).
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.account a
        WHERE a.id = :accountId
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
        """)
    List<Transaction> findForStatement(@Param("accountId") UUID accountId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to")   LocalDateTime to);

    // ─────────────────────────────────────────────────────────
    //  Gestion des statuts
    // ─────────────────────────────────────────────────────────

    /**
     * Transactions en attente depuis plus de {@code timeout} minutes.
     * Utilisé par le scheduler de timeout pour passer les transactions
     * bloquées en statut {@code FAILED}.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status IN ('PENDING', 'PROCESSING')
          AND t.createdAt < :timeout
        ORDER BY t.createdAt ASC
        """)
    List<Transaction> findStuckTransactions(@Param("timeout") LocalDateTime timeout);

    /**
     * Transactions suspectes en attente de décision compliance.
     */
    Page<Transaction> findByStatusOrderByCreatedAtAsc(TransactionStatus status, Pageable pageable);

    /**
     * Mise à jour ciblée du statut — sans charger l'entité complète.
     */
    @Modifying
    @Query("""
        UPDATE Transaction t
        SET t.status    = :status,
            t.updatedAt = :updatedAt
        WHERE t.id = :id
        """)
    int updateStatus(@Param("id")        UUID id,
                     @Param("status")    TransactionStatus status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Règlement d'une transaction : statut SETTLED + date de règlement.
     */
    @Modifying
    @Query("""
        UPDATE Transaction t
        SET t.status     = 'SETTLED',
            t.settledAt  = :settledAt,
            t.updatedAt  = :settledAt
        WHERE t.id = :id
          AND t.status   = 'APPROVED'
        """)
    int settle(@Param("id") UUID id, @Param("settledAt") LocalDateTime settledAt);

    /**
     * Enregistrement du score de fraude et passage en FRAUD_SUSPECT.
     */
    @Modifying
    @Query("""
        UPDATE Transaction t
        SET t.status     = 'FRAUD_SUSPECT',
            t.fraudScore = :score,
            t.updatedAt  = :updatedAt
        WHERE t.id = :id
        """)
    int flagFraud(@Param("id")        UUID id,
                  @Param("score")     BigDecimal score,
                  @Param("updatedAt") LocalDateTime updatedAt);

    // ─────────────────────────────────────────────────────────
    //  Anti-fraude — détection de patterns
    // ─────────────────────────────────────────────────────────

    /**
     * Nombre de transactions initiées depuis un compte sur une fenêtre glissante.
     * Règle velocity check : détecte les séquences de petits paiements rapides
     * typiques du card testing.
     */
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.createdAt  > :since
          AND t.status NOT IN ('CANCELLED', 'REFUSED', 'BLOCKED')
        """)
    long countRecentByAccount(@Param("accountId") UUID accountId,
                               @Param("since")     LocalDateTime since);

    /**
     * Somme des montants débités depuis un compte sur une fenêtre glissante.
     * Règle amount threshold : détecte les montants cumulés anormaux.
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.createdAt  > :since
          AND t.type IN ('SEPA_TRANSFER', 'INTERNATIONAL_TRANSFER',
                         'CARD_PAYMENT', 'CASH_WITHDRAWAL')
          AND t.status NOT IN ('CANCELLED', 'REFUSED', 'BLOCKED')
        """)
    BigDecimal sumDebitedAmountSince(@Param("accountId") UUID accountId,
                                     @Param("since")     LocalDateTime since);

    /**
     * Transactions vers un IBAN contrepartie donné sur une période.
     * Détecte les virements répétés vers le même bénéficiaire.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.id       = :accountId
          AND t.counterpartIban  = :iban
          AND t.createdAt        > :since
          AND t.status NOT IN ('CANCELLED', 'REFUSED')
        ORDER BY t.createdAt DESC
        """)
    List<Transaction> findRecentByCounterpartIban(@Param("accountId") UUID accountId,
                                                  @Param("iban")      String iban,
                                                  @Param("since")     LocalDateTime since);

    // ─────────────────────────────────────────────────────────
    //  Reporting — agrégations
    // ─────────────────────────────────────────────────────────

    /**
     * Somme des transactions réglées pour un compte sur une période.
     * Utilisé pour les relevés de compte et les calculs d'intérêts.
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.status     = 'SETTLED'
          AND t.type       IN :types
          AND t.createdAt  BETWEEN :from AND :to
        """)
    BigDecimal sumSettledByTypeAndPeriod(@Param("accountId") UUID accountId,
                                         @Param("types")     List<TransactionType> types,
                                         @Param("from")      LocalDateTime from,
                                         @Param("to")        LocalDateTime to);

    /**
     * Volume et nombre de transactions par type sur une période — dashboard.
     * Retourne des Object[] : [TransactionType, count, sum].
     */
    @Query("""
        SELECT t.type, COUNT(t), COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.createdAt BETWEEN :from AND :to
          AND t.status    = 'SETTLED'
        GROUP BY t.type
        ORDER BY COUNT(t) DESC
        """)
    List<Object[]> volumeByTypeAndPeriod(@Param("from") LocalDateTime from,
                                         @Param("to")   LocalDateTime to);

    /**
     * Transactions nécessitant une déclaration TRACFIN (montant ≥ 10 000 € non justifié).
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.amount   >= :threshold
          AND t.type     IN ('SEPA_TRANSFER', 'INTERNATIONAL_TRANSFER', 'CASH_DEPOSIT')
          AND t.status   = 'SETTLED'
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.amount DESC
        """)
    Page<Transaction> findAmlCandidates(@Param("threshold") BigDecimal threshold,
                                        @Param("from")      LocalDateTime from,
                                        @Param("to")        LocalDateTime to,
                                        Pageable pageable);
}