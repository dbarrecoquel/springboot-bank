package com.bank.service.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.Transaction;

public interface TransactionService {

	public List<Transaction> getAllTransactions();
	public Optional<Transaction> getTransactionById(UUID id);
	public Transaction saveTransaction(Transaction transaction);
	public Page<Transaction> getAllTransactions(Pageable page);
	public void deleteTransaction(UUID id);
	
	public Optional<Transaction> findByReference(String reference);
    public boolean existsByReference(String reference);
}
