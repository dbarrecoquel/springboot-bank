package com.bank.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.domain.entity.Transaction;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.api.TransactionService;

@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

	private final TransactionRepository transactionRepository;
	
	public TransactionServiceImpl(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}
	@Override
	public List<Transaction> getAllTransactions() {
		return transactionRepository.findAll();
	}
	@Override
	public Optional<Transaction> getTransactionById(UUID id) {
		return transactionRepository.findById(id);
	}
		
	@Override
	public Transaction saveTransaction(Transaction transaction) {
		return transactionRepository.save(transaction);
	}
	@Override
	public Page<Transaction> getAllAds(Pageable page) {
		return transactionRepository.findAll(page);
	}
	
	@Override
	public void deleteTransaction(UUID id) {
		transactionRepository.deleteById(id);
	}
	
}
