package com.bank.service.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.Account;

public interface AccountService {

	public List<Account> getAllAccounts();
	public Optional<Account> getAccountById(UUID id);
	public Account saveAccount(Account account);
	public Page<Account> getAllAds(Pageable page);
	public void deleteAccount(UUID id);
	public Optional<Account> findByIban(String iban);
	
}
