package com.bank.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.AccountDTO.Summary;
import com.bank.common.dto.TransactionDTO;
import com.bank.domain.entity.Account;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.UserRole;

public interface AccountService {

	public List<Account> getAllAccounts();
	public Optional<Account> getAccountById(UUID id);
	public Account saveAccount(Account account);
	public Page<Account> getAllAccounts(Pageable page);
    Page<AccountDTO.Summary> findAll(AccountStatus status,
            AccountType type,
            Pageable pageable);

	public void deleteAccount(UUID id);
	public Optional<Account> findByIban(String iban);
	public Optional<Account> findByAccountNumber(String accountNumber);
	public boolean existsByIban(String iban);
	public boolean existsByAccountNumber(String accountNumber);
	public List<Account> findByOwner(UUID ownerId);
	public Page<Account> findByOwner(UUID ownerId, Pageable pageable);
	public List<Account> findByOwnerAndStatus(UUID ownerId, AccountStatus status);
	public List<Account> findByOwnerAndType(UUID ownerId, AccountType type);
	public boolean userOwnsAccount(UUID accountId, UUID ownerId);
	public Optional<Account> findByIdWithOwner(UUID accountId);
	public Optional<Account> findByIdWithTransactions(UUID accountId);
	public Optional<Account> findByIdWithLock(UUID accountId);
	public Optional<Account> findByIdWithReadLock(UUID accountId);
	public void updateBalance(UUID accountId, BigDecimal balance);
	public void updateStatus(UUID accountId, AccountStatus status);
	public void blockAccount(UUID accountId);
	public void closeAccount(UUID accountId);
	public BigDecimal sumBalanceByOwnerAndCurrency(UUID ownerId, CurrencyCode currency);
	public List<Object[]> countAccountsByStatus();
	public Page<Account> findOverdrawnAccounts(Pageable pageable);
	public Page<Account> findDormantAccounts(LocalDateTime since,Pageable pageable);
	public List<Account> findAccountsWithExpiringCards(LocalDate from, LocalDate to);
	public List<Summary> findByOwnerId(UUID ownerId);
	public AccountDTO findById(UUID accountId, UUID requesterId, Set<UserRole> roles);
	public AccountDTO openAccount(UUID ownerId, AccountType type, CurrencyCode currency, String label,
			BigDecimal overdraftLimit);
	public AccountDTO updateLabel(UUID accountId, String label, UUID requesterId, Set<UserRole> roles);
	public AccountDTO blockAccount(UUID accountId, String reason, UUID operatorId);
	public AccountDTO unblockAccount(UUID accountId, String reason, UUID operatorId);
	public void closeAccount(UUID accountId, String reason, UUID operatorId);
	public Page<TransactionDTO.Summary> getTransactions(UUID accountId, UUID requesterId,
			Set<UserRole> roles, String from, String to, Pageable pageable);
}
