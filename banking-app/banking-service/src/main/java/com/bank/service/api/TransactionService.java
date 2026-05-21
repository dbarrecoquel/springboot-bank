package com.bank.service.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.bank.domain.entity.Transaction;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;

public interface TransactionService {

	public List<Transaction> getAllTransactions();
	public Optional<Transaction> getTransactionById(UUID id);
	public Transaction saveTransaction(Transaction transaction);
	public Page<Transaction> getAllTransactions(Pageable page);
	public void deleteTransaction(UUID id);
	
	public Optional<Transaction> findByReference(String reference);
    public boolean existsByReference(String reference);
    public Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    public Page<Transaction> findByAccountIdAndStatusOrderByCreatedAtDesc(UUID accountId, TransactionStatus status, Pageable pageable);
    public Page<Transaction> findByAccountIdAndTypeOrderByCreatedAtDesc(UUID accountId, TransactionType type, Pageable pageable);
    public Page<Transaction> findByAccountIdAndPeriod(UUID accountId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    public List<Transaction> findForStatement(UUID accountId, LocalDateTime from, LocalDateTime to);
    public List<Transaction> findStuckTransactions(LocalDateTime timeout);
    public Page<Transaction> findByStatusOrderByCreatedAtAsc(TransactionStatus status, Pageable pageable);
    public void updateStatus(UUID id, TransactionStatus status, LocalDateTime updatedAt);
	public void pendingTransaction(UUID id, LocalDateTime updatedAt);
	public void processingTransaction(UUID id, LocalDateTime updatedAt);
	public void approvedTransaction(UUID id, LocalDateTime updatedAt);
	public void refusedTransaction(UUID id, LocalDateTime updatedAt);
	public void cancelledTransaction(UUID id, LocalDateTime updatedAt);
	public void reversedTransaction(UUID id, LocalDateTime updatedAt);
	public void confirmedTransaction(UUID id, LocalDateTime updatedAt);
	public void blockedTransaction(UUID id, LocalDateTime updatedAt);
	public void settle(UUID id, LocalDateTime settledAt);
	public void flagFraud(UUID id, BigDecimal score, LocalDateTime updatedAt);
	public long countRecentByAccount(UUID accountId, LocalDateTime since);
	public BigDecimal sumDebitedAmountSince(UUID accountId, LocalDateTime since);
	public List<Transaction> findRecentByCounterpartIban(UUID accountId,String iban,LocalDateTime since);
	public BigDecimal sumSettledByTypeAndPeriod(UUID accountId, List<TransactionType> types,LocalDateTime from, LocalDateTime to);
	public List<Object[]> volumeByTypeAndPeriod(LocalDateTime from,LocalDateTime to);
	public Page<Transaction> findAmlCandidates(BigDecimal threshold,LocalDateTime from, LocalDateTime to, Pageable pageable);
}
