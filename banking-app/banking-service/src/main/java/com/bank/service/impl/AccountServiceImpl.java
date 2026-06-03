package com.bank.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.AccountDTO.Summary;
import com.bank.common.dto.TransactionDTO;
import com.bank.common.exception.BankingException;
import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.common.mapper.AccountMapper;
import com.bank.common.mapper.TransactionMapper;
import com.bank.common.mapper.UserMapper;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.UserRole;
import com.bank.domain.event.AccountBlockedEvent;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.infrastructure.persistence.UserRepository;
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
	private final AccountMapper accountMapper;
	private final UserRepository userRepository;
	private final TransactionEventProducer eventProducer;
	private final TransactionRepository transactionRepository;
	private final TransactionMapper transactionMapper;
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
	@Override
	public Page<Summary> findAll(AccountStatus status, AccountType type, Pageable pageable) {
		Specification<Account> spec = Specification.where((Specification<Account>)null);
		
        if (status != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("type"), type));
        }
        
        Page<Account> page = accountRepository.findAll(spec, pageable);
        
        List<AccountDTO.Summary> summaries = page.getContent().stream().map(accountMapper::toSummary).collect(Collectors.toList());
        
        return new PageImpl<AccountDTO.Summary>(summaries, pageable, page.getTotalElements());
		
	}
	@Override
	public List<AccountDTO.Summary> findByOwnerId(UUID ownerId){
		
		return accountRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(accountMapper::toSummary).collect(Collectors.toList());
	}
	@Override
	public AccountDTO findById(UUID accountId, UUID requesterId, Set<UserRole> roles ) {
		
		Account account = accountRepository.findByIdWithOwner(accountId).orElseThrow(() -> new BankingException(
                "Compte introuvable : " + accountId,
                "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND));
		
        if (roles.contains(UserRole.CUSTOMER) && !roles.contains(UserRole.TELLER)
                && !roles.contains(UserRole.MANAGER) && !roles.contains(UserRole.ADMIN)) {
            if (!account.getOwner().getId().equals(requesterId)) {
                throw new BankingException(
                    "Accès refusé à ce compte", "ACCESS_DENIED", HttpStatus.FORBIDDEN);
            }
        }
        
        return accountMapper.toDto(account);

	}
	@Override
	@Transactional
	public AccountDTO openAccount(UUID ownerId, AccountType type, CurrencyCode currency, String label, BigDecimal overdraftLimit) {
		
		var owner = userRepository.findById(ownerId).orElseThrow(() -> new BankingException(
                "Utilisateur introuvable : " + ownerId,
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND));
		
		if (!owner.isKycVerified())
		{
            throw new BankingException(
                    "Vérification KYC requise avant l'ouverture d'un compte.",
                    "KYC_REQUIRED", HttpStatus.FORBIDDEN);

		}
		
		String iban = generateIban(ownerId);
		String accountNumber = generateAccountNumber();
		
		Account account = Account.create(iban, accountNumber, type, currency, owner);
		account.setLabel(label);
		account.setOverdraftLimit(overdraftLimit);
		// Les comptes épargne ne supportent pas le découvert
        if (type == AccountType.SAVINGS) {
            account.setOverdraftLimit(BigDecimal.ZERO);
        }
        
        Account saved = accountRepository.save(account);
        auditLogRepository.save(AuditLog.success(
                "ACCOUNT_OPENED", "Account",
                saved.getId().toString(), ownerId,
                "iban=" + saved.getIban() + " type=" + type + " currency=" + currency
            ));
     
        log.info("[ACCOUNT] Ouverture — id={} iban={} ownerId={} type={}",
                     saved.getId(), saved.getIban(), ownerId, type);

        return accountMapper.toDto(saved);
		
	}
	@Override
	@Transactional
	public AccountDTO updateLabel(UUID accountId,String label, UUID requesterId,  Set<UserRole> roles) {
		
		Account account = loadAccount(accountId);
		assertNotClosed(account,"UPDATE_LABEL");
		assertOwnerOrOperator(account, requesterId, roles, "UPDATE_LABEL");
		
		account.setLabel(label);
		Account saved = accountRepository.save(account);
		
        log.debug("[ACCOUNT] Libellé mis à jour — id={} label={}", accountId, label);
        
        return accountMapper.toDto(saved);
		
	}
	@Override
	@Transactional
	public AccountDTO blockAccount(UUID accountId, String reason, UUID operatorId) {
		
		Account account = loadAccount(accountId);
		
		if (!account.getStatus().canTransitionTo(AccountStatus.BLOCKED))
		{
            throw UnauthorizedOperationException.invalidTransition(
                    "Account", accountId,
                    account.getStatus().name(), AccountStatus.BLOCKED.name());

		}
		
		account.block();
		accountRepository.updateStatus(accountId, AccountStatus.BLOCKED, LocalDateTime.now());
        AccountBlockedEvent event = AccountBlockedEvent.manual(
                accountId, account.getIban(), account.getOwner().getId(),
                account.getBalance(), account.getCurrency(),
                AccountBlockedEvent.BlockReason.COMPLIANCE_DECISION,
                reason, operatorId
            );
            eventProducer.publishAccountBlocked(event);
     
            auditLogRepository.save(AuditLog.success(
                "ACCOUNT_BLOCKED", "Account",
                accountId.toString(), operatorId,
                "reason=" + reason
            ));
         
          return accountMapper.toDto(account);
		
		
	}
    @Override
    @Transactional
    public AccountDTO unblockAccount(UUID accountId, String reason, UUID operatorId) {
        Account account = loadAccount(accountId);
 
        if (!account.getStatus().canTransitionTo(AccountStatus.ACTIVE)) {
            throw UnauthorizedOperationException.invalidTransition(
                "Account", accountId,
                account.getStatus().name(), AccountStatus.ACTIVE.name());
        }
 
        accountRepository.updateStatus(accountId, AccountStatus.ACTIVE, LocalDateTime.now());
        account.setStatus(AccountStatus.ACTIVE);
 
        auditLogRepository.save(AuditLog.success(
            "ACCOUNT_UNBLOCKED", "Account",
            accountId.toString(), operatorId,
            "reason=" + reason
        ));
 
        log.info("[ACCOUNT] Débloqué — id={} operator={}", accountId, operatorId);
        return accountMapper.toDto(account);
    }
    @Override
    @Transactional
    public void closeAccount(UUID accountId, String reason, UUID operatorId) {
    	
    	Account account = loadAccount(accountId);
    	
    	if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BankingException(
                    "Le solde doit être nul avant clôture — solde actuel : "
                    + account.getBalance() + " " + account.getCurrency(),
                    "ACCOUNT_BALANCE_NOT_ZERO", HttpStatus.UNPROCESSABLE_ENTITY);

    	}
        if (!account.getStatus().canTransitionTo(AccountStatus.CLOSED)) {
            throw UnauthorizedOperationException.invalidTransition(
                "Account", accountId,
                account.getStatus().name(), AccountStatus.CLOSED.name());
        }
        account.close();
        accountRepository.updateStatus(accountId, AccountStatus.CLOSED, LocalDateTime.now());
        auditLogRepository.save(AuditLog.success(
                "ACCOUNT_CLOSED", "Account",
                accountId.toString(), operatorId,
                "reason=" + reason
        ));

    }
    @Override
    @Transactional(readOnly = true)
    public Page<TransactionDTO.Summary> getTransactions(UUID accountId,
                                                         UUID requesterId,
                                                         Set<UserRole> roles,
                                                         String from, String to,
                                                         Pageable pageable) {
        // Vérifier l'accès au compte
        findById(accountId, requesterId, roles);
 
        // Filtrage par période si fournie
        if (from != null && to != null) {
            LocalDateTime dtFrom = parseDateTime(from, "from");
            LocalDateTime dtTo   = parseDateTime(to,   "to");
 
            return transactionRepository
                .findByAccountIdAndPeriod(accountId, dtFrom, dtTo, pageable)
                .map(transactionMapper::toSummary);
        }
 
        return transactionRepository
            .findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map(transactionMapper::toSummary);
    }

    private String generateIban(UUID ownerId) {
        String bban = "30006" + String.format("%016d",
            Math.abs(ownerId.getMostSignificantBits() % 1_000_000_000_000_000L));
        return "FR76" + bban.substring(0, 23);
    }
 
    private String generateAccountNumber() {
        return "ACC-" + UUID.randomUUID().toString()
            .replace("-", "").substring(0, 10).toUpperCase();
    }
    private Account loadAccount(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new BankingException(
                "Compte introuvable : " + accountId,
                "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND));
    }
 
    private void assertNotClosed(Account account, String operation) {
        if (account.getStatus() == AccountStatus.CLOSED) {
        	throw new BankingException(
                    "Accès refusé à ce compte", "ACCOUNT_CLOSED", HttpStatus.FORBIDDEN);
        }
    }
 
    private void assertOwnerOrOperator(Account account, UUID requesterId,
                                        Set<UserRole> roles, String operation) {
        boolean isOperator = roles.contains(UserRole.TELLER)
            || roles.contains(UserRole.MANAGER)
            || roles.contains(UserRole.ADMIN);
 
        if (!isOperator && !account.getOwner().getId().equals(requesterId)) {
            throw new BankingException(
                    "Accès refusé à ce compte", "ACCESS_DENIED", HttpStatus.FORBIDDEN);
        }
    }
 
    private LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BankingException(
                "Format de date invalide pour '" + fieldName + "' — attendu ISO 8601 (yyyy-MM-ddTHH:mm:ss)",
                "INVALID_DATE_FORMAT", HttpStatus.BAD_REQUEST);
        }
    }


}
