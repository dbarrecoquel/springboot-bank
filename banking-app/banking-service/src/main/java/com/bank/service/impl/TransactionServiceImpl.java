package com.bank.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.domain.entity.Transaction;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.api.TransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

	private final TransactionRepository transactionRepository;
	
	
	@Override
	public List<Transaction> getAllTransactions() {
		return transactionRepository.findAll();
	}
	@Override
	public Optional<Transaction> getTransactionById(UUID id) {
		return transactionRepository.findById(id);
	}
		
	@Override
	@Transactional
	public Transaction saveTransaction(Transaction transaction) {
		return transactionRepository.save(transaction);
	}
	@Override
	public Page<Transaction> getAllTransactions(Pageable page) {
		return transactionRepository.findAll(page);
	}
	
	@Override
	@Transactional
	public void deleteTransaction(UUID id) {
		transactionRepository.deleteById(id);
	}
	@Override
	public Optional<Transaction> findByReference(String reference) {
		return transactionRepository.findByReference(reference);
	}
	@Override
	public boolean existsByReference(String reference) {
		return transactionRepository.existsByReference(reference);
	}
	@Override
	public Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable) {
		return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
	}
	@Override
	public Page<Transaction> findByAccountIdAndStatusOrderByCreatedAtDesc(UUID accountId, TransactionStatus status,
			Pageable pageable) {
		return transactionRepository.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status, pageable);
	}
	@Override
	public Page<Transaction> findByAccountIdAndTypeOrderByCreatedAtDesc(UUID accountId, TransactionType type,
			Pageable pageable) {
		
		return transactionRepository.findByAccountIdAndTypeOrderByCreatedAtDesc(accountId, type, pageable);
	}
	@Override
	public Page<Transaction> findByAccountIdAndPeriod(UUID accountId, LocalDateTime from, LocalDateTime to,
			Pageable pageable) {
		return transactionRepository.findByAccountIdAndPeriod(accountId, from, to, pageable);
	}
	@Override
	public List<Transaction> findForStatement(UUID accountId, LocalDateTime from, LocalDateTime to) {
		return transactionRepository.findForStatement(accountId, from, to);
	}
	@Override
	public List<Transaction> findStuckTransactions(LocalDateTime timeout) {
		return transactionRepository.findStuckTransactions(timeout);
	}
	@Override
	public Page<Transaction> findByStatusOrderByCreatedAtAsc(TransactionStatus status, Pageable pageable) {
		return transactionRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
	}
	@Override
	@Transactional
	public void updateStatus(UUID id, TransactionStatus status, LocalDateTime updatedAt) {
		
		int updated = transactionRepository.updateStatus(id, status, updatedAt);
		
		if (updated == 0)
		{
			throw new IllegalArgumentException("transaction id not found :" + id);
		}
		log.warn("[TRANSACTION] Status updated id={} status={}",id,status);
		
	}
	@Override  
	@Transactional
	public void pendingTransaction(UUID id, LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.PENDING, updatedAt);
	}
	@Override
	@Transactional
	public void processingTransaction(UUID id, LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.PROCESSING, updatedAt);
	}
	@Override
	@Transactional
	public void approvedTransaction(UUID id, LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.APPROVED, updatedAt);
	}
	
	@Override
	@Transactional
	public void refusedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.REFUSED, updatedAt);
	}
	@Override
	@Transactional
	public void cancelledTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.CANCELLED, updatedAt);
	}
	@Override
	@Transactional
	public void reversedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.REVERSED, updatedAt);
	}
	
	@Override
	@Transactional
	public void confirmedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.CONFIRMED, updatedAt);
	}
	@Override
	@Transactional
	public void blockedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.BLOCKED, updatedAt);
	}
	@Override
	@Transactional
	public void settle(UUID id, LocalDateTime settledAt) {
		
		int updated = transactionRepository.settle(id, settledAt);
		
		if (updated == 0)
		{
			throw new IllegalArgumentException("transaction id not found :" + id);
		}
		log.warn("[TRANSACTION] settle updated id={}",id);

	}
	@Override
	@Transactional
	public void flagFraud(UUID id, BigDecimal score, LocalDateTime updatedAt) {
		
		int updated = transactionRepository.flagFraud(id, score, updatedAt);
		
		if (updated == 0)
		{
			throw new IllegalArgumentException("transaction id not found :" + id);
		}
		log.warn("[TRANSACTION] flagFraud updated id={} score={}",id,score);
	}
	@Override
	public long countRecentByAccount(UUID accountId, LocalDateTime since) {
		return transactionRepository.countRecentByAccount(accountId, since);
	}
	@Override
	public BigDecimal sumDebitedAmountSince(UUID accountId, LocalDateTime since) {
		return transactionRepository.sumDebitedAmountSince(accountId, since);
	}
	@Override
	public List<Transaction> findRecentByCounterpartIban(UUID accountId, String iban, LocalDateTime since) {
		return transactionRepository.findRecentByCounterpartIban(accountId, iban, since);
	}
	@Override
	public BigDecimal sumSettledByTypeAndPeriod(UUID accountId, List<TransactionType> types, LocalDateTime from,
			LocalDateTime to) {
		return transactionRepository.sumSettledByTypeAndPeriod(accountId, types, from, to);
	}
	@Override
	public List<Object[]> volumeByTypeAndPeriod(LocalDateTime from, LocalDateTime to) {
		return transactionRepository.volumeByTypeAndPeriod(from, to);
	}
	@Override
	public Page<Transaction> findAmlCandidates(BigDecimal threshold, LocalDateTime from, LocalDateTime to,
			Pageable pageable) {
		return transactionRepository.findAmlCandidates(threshold, from, to, pageable);
	}
	
	
}
