package com.bank.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.service.api.AccountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {
	
	private final AccountRepository accountRepository;
	private final AuditLogRepository auditLogRepository;
	@Override
	public List<Account> getAllAccounts() {
		return accountRepository.findAll();
	}
	@Override
	public Optional<Account> getAccountById(UUID id) {
		return accountRepository.findById(id);
	}
		
	@Override
	@Transactional
	public Account saveAccount(Account account) {
		Account saved = accountRepository.save(account);
		
		log.info(
	            "[ACCOUNT] Account saved id={} iban={}",
	            saved.getId(),
	            saved.getIban()
	        );
		return saved;
	}
	@Override
	public Page<Account> getAllAccounts(Pageable page) {
		return accountRepository.findAll(page);
	}
	
	@Override
	@Transactional
	public void deleteAccount(UUID id) {
		accountRepository.deleteById(id);
		 log.warn(
		            "[ACCOUNT] Account deleted id={}",
		            id
		        );
	}
	
	@Override
	public Optional<Account> findByIban(String iban) {
		return accountRepository.findByIban(iban);
	}
	
	@Override
	public Optional<Account> findByAccountNumber(String accountNumber) {
		
		return accountRepository.findByAccountNumber(accountNumber);
	}
	@Override
	public boolean existsByIban(String iban) {
        return accountRepository.existsByIban(iban);
    }
	@Override
    public boolean existsByAccountNumber(String accountNumber) {
        return accountRepository.existsByAccountNumber(accountNumber);
    }
	@Override
	public List<Account> findByOwner(UUID ownerId) {
		return accountRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}
	@Override
	public Page<Account> findByOwner(UUID ownerId, Pageable pageable) {
		return accountRepository.findByOwnerId(ownerId, pageable);
	}
	@Override
	public List<Account> findByOwnerAndStatus(UUID ownerId, AccountStatus status) {
		return accountRepository.findByOwnerIdAndStatus(ownerId, status);
	}
	@Override
	public List<Account> findByOwnerAndType(UUID ownerId, AccountType type) {
		return accountRepository.findByOwnerIdAndType(ownerId, type);
	}
	// verifications métiers
	@Override
	public boolean userOwnsAccount(UUID accountId, UUID ownerId) {
		return accountRepository.existsByIdAndOwnerId(accountId, ownerId);
	}
	
	//chargements optimisés
	
	@Override
	public Optional<Account> findByIdWithOwner(UUID accountId) {
		return accountRepository.findByIdWithOwner(accountId);
	}
	@Override
	public Optional<Account> findByIdWithTransactions(UUID accountId) {
		return accountRepository.findByIdWithTransactions(accountId);
	}
	
	@Override
	@Transactional
	public Optional<Account> findByIdWithLock(UUID accountId) {
		 return accountRepository.findByIdWithLock(accountId);
	}
	@Override
	@Transactional
	public Optional<Account> findByIdWithReadLock(UUID accountId) {
		return accountRepository.findByIdWithReadLock(accountId);
	}
	@Override
	@Transactional
	public void updateBalance(UUID accountId, BigDecimal balance) {
		
		
		
		int updated = accountRepository.updateBalance(accountId, balance, LocalDateTime.now());
		
		if (updated == 0)
		{
			throw new IllegalArgumentException("Compte introuvable : " + accountId);
		}
		
		log.info("[ACCOUNT] Balance updated accountId={} balance={}",accountId,balance);

	}
	@Override
	@Transactional
    public void updateStatus(UUID accountId, AccountStatus status) {
		Account account = accountRepository.findById(accountId).orElseThrow(()->new IllegalArgumentException("Compte introuvable : " + accountId));
		
		if (!account.getStatus().canTransitionTo(status))
		{
			throw UnauthorizedOperationException.invalidTransition(
	                "Account", accountId,
	                account.getStatus().name(), status.name());
		}
        accountRepository.updateStatus(accountId,status,LocalDateTime.now());
        
        auditLogRepository.save(AuditLog.success(
                "Account Updated", "Account",
                accountId.toString(), null,
                "status=" + status.name()
            ));

       

        log.warn("[ACCOUNT] Status updated accountId={} status={}",accountId,status);
    }
	@Override
	@Transactional
	public void blockAccount(UUID accountId) {
		updateStatus(accountId, AccountStatus.BLOCKED);
		
	}
	@Override
	@Transactional
	public void closeAccount(UUID accountId) {
		updateStatus(accountId, AccountStatus.CLOSED);
	}
	@Override
	public BigDecimal sumBalanceByOwnerAndCurrency(UUID ownerId, CurrencyCode currency) {
		return accountRepository.sumBalanceByOwnerAndCurrency(ownerId, currency).orElse(BigDecimal.ZERO);
	}
	@Override
	public List<Object[]> countAccountsByStatus() {
		return accountRepository.countByStatus();
	}
	@Override
	public Page<Account> findOverdrawnAccounts(Pageable pageable) {
		return accountRepository.findOverdrawnAccounts(pageable);
	}
	@Override
	public Page<Account> findDormantAccounts(LocalDateTime since, Pageable pageable) {
		return accountRepository.findDormantAccounts(since, pageable);
	}
	@Override
	public List<Account> findAccountsWithExpiringCards(LocalDate from, LocalDate to) {
		return accountRepository.findAccountsWithExpiringCards(from, to);
	}

}
