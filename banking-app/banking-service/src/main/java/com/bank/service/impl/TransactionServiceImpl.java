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
	
}
