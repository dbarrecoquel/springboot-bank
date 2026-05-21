package com.bank.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.domain.entity.Account;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.service.api.AccountService;


@Service
@Transactional
public class AccountServiceImpl implements AccountService {
	
	private final AccountRepository accountRepository;
	
	public AccountServiceImpl(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}
	
	@Override
	public List<Account> getAllAccounts() {
		return accountRepository.findAll();
	}
	@Override
	public Optional<Account> getAccountById(UUID id) {
		return accountRepository.findById(id);
	}
		
	@Override
	public Account saveAccount(Account account) {
		return accountRepository.save(account);
	}
	@Override
	public Page<Account> getAllAds(Pageable page) {
		return accountRepository.findAll(page);
	}
	
	@Override
	public void deleteAccount(UUID id) {
		accountRepository.deleteById(id);
	}
	
	@Override
	public Optional<Account> findByIban(String iban) {
		return accountRepository.findByIban(iban);
	}

}
