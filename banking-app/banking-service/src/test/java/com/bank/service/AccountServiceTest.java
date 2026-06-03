package com.bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.TransactionDTO;
import com.bank.common.dto.TransactionDTO.Summary;
import com.bank.common.exception.BankingException;
import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.common.mapper.AccountMapper;
import com.bank.common.mapper.TransactionMapper;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Transaction;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CardStatus;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.domain.enums.UserRole;
import com.bank.domain.event.AccountBlockedEvent;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.impl.AccountServiceImpl;

import net.bytebuddy.NamingStrategy.Suffixing.BaseNameResolver.ForGivenType;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

	@Mock
	private AccountRepository accountRepository;
	
	@Mock
	private AuditLogRepository auditLogRepository;
	
	@Mock
	private AccountMapper accountMapper;
	
	@Mock
	private UserRepository userRepository;
	@Mock
	private TransactionEventProducer eventProducer;
	
	@Mock
	private TransactionRepository transactionRepository;
	
	@Mock
	private TransactionMapper transactionMapper;
	
	@InjectMocks
	private AccountServiceImpl accountService;
	
	private Account account;
	private User alice;
	private UUID accountId;
	private UUID ownerId;
	
	@BeforeEach
	void setup(){
		
		accountId = UUID.randomUUID();
		ownerId = UUID.randomUUID();
		
		alice = User.create("Alice", "Alice", LocalDate.of(1985, 10, 1), "alice@bank.fr", "password");
	    alice.setId(ownerId);

	    // 2. Il faut impérativement passer 'alice' comme dernier paramètre ici !
	    account = Account.create("FR7630006000011234567890189", "ACC-001", AccountType.CURRENT, CurrencyCode.EUR, alice);
	    account.setId(accountId);
	    account.setBalance(BigDecimal.valueOf(2000));
		account.setIban("FR761234567890");
		account.setAccountNumber("ACC123456");
	}
	
	@Test
	void shouldGetAllAccounts() {
		
		//on init une list
		List<Account> accounts = List.of(account);
		
		// on rempli findAll avec cette liste
		when(accountRepository.findAll()).thenReturn(accounts);
		
		//on appel le service qui utilise accountRepository
		List<Account> result = accountService.getAllAccounts();
		
		assertNotNull(result);
		assertEquals(1,result.size());
		
		//on verifie que le service appel findAll 1 fois
		verify(accountRepository).findAll();
	}
	
	@Test
	void shouldGetAccountById() {
		
		when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
		
		Optional<Account> result = accountService.getAccountById(accountId);
		
		assertTrue(result.isPresent());
		assertEquals(accountId, result.get().getId());
		
		verify(accountRepository).findById(accountId);
	}
	
	@Test
	void shouldSaveAccount() {
		
		when(accountRepository.save(account)).thenReturn(account);
		
		Account result = accountService.saveAccount(account);
		
		assertNotNull(result);
		assertEquals(account.getIban(), result.getIban());
		
		verify(accountRepository).save(account);
		
	}
	
	@Test
	void shouldGetAllAccountsWithPageable() {
		
		Pageable pageable = PageRequest.of(0, 10);
		Page<Account> page = new PageImpl<Account>(List.of(account));
		
		when(accountRepository.findAll(pageable)).thenReturn(page);
		
		Page<Account> result = accountService.getAllAccounts(pageable);
		
		assertNotNull(result);
		assertEquals(result.getContent().size(),1);
		
		verify(accountRepository).findAll(pageable);
	}
	
	@Test
	void shouldFindByIban() {
	
		when(accountRepository.findByIban("FR761234567890")).thenReturn(Optional.of(account));
		
		Optional<Account> result = accountService.findByIban("FR761234567890");
		
		assertTrue(result.isPresent());
		
		verify(accountRepository).findByIban("FR761234567890");
	}
	
	@Test
	void shouldFindByAccNumber() {
		
		when(accountRepository.findByAccountNumber("ACC123456")).thenReturn(Optional.of(account));
		
		Optional<Account> result = accountService.findByAccountNumber("ACC123456");
		
		assertTrue(result.isPresent());
		
		verify(accountRepository).findByAccountNumber("ACC123456");
	}
	
	@Test
	void shouldCheckExistsByIban() {
		
		when(accountRepository.existsByIban("FR761234567890")).thenReturn(true);

		boolean result = accountService.existsByIban("FR761234567890");
		
		assertTrue(result);
		
		verify(accountRepository).existsByIban("FR761234567890");
	}
	
	@Test
	void shouldCheckExistsByAccountNumber() {
		
		when(accountRepository.existsByAccountNumber("ACC123456")).thenReturn(true);
		
		boolean result = accountService.existsByAccountNumber("ACC123456");
		
		assertTrue(result);
		
		verify(accountRepository).existsByAccountNumber("ACC123456");
	}
	
	@Test
	void shouldFindByOwner() {
		
		List<Account> accounts = List.of(account);
		
		when(accountRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(accounts);
		
		List<Account> result = accountService.findByOwner(ownerId);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(accountRepository).findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}
	
	@Test
	void shouldFindByOwnerWithPageable() {
		Pageable pageable = PageRequest.of(0, 10);
		Page<Account> page = new PageImpl<Account>(List.of(account));
		
		when(accountRepository.findByOwnerId(ownerId, pageable)).thenReturn(page);
		
		Page<Account> result = accountService.findByOwner(ownerId, pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(accountRepository).findByOwnerId(ownerId, pageable);
	}
	
	@Test
	void shouldFindByOwnerWithStatus() {
		
		List<Account> accounts = List.of(account);
		
		when(accountRepository.findByOwnerIdAndStatus(ownerId, AccountStatus.ACTIVE)).thenReturn(accounts);
		
		List<Account> result = accountService.findByOwnerAndStatus(ownerId,AccountStatus.ACTIVE);
		
		assertEquals(1, result.size());
		
		verify(accountRepository).findByOwnerIdAndStatus(ownerId,AccountStatus.ACTIVE);
	}
	
	@Test
	void shouldFindByOwnerIdAndType() {
		
		List<Account> accounts = List.of(account);
		
		when(accountRepository.findByOwnerIdAndType(ownerId, AccountType.CURRENT)).thenReturn(accounts);
		
		List<Account> result = accountService.findByOwnerAndType(ownerId, AccountType.CURRENT);
		
		assertEquals(1, result.size());
		
		verify(accountRepository).findByOwnerIdAndType(ownerId, AccountType.CURRENT);
	}
	
	@Test
	void shouldCheckUserOwnsAccount() {
		
		when(accountRepository.existsByIdAndOwnerId(accountId, ownerId)).thenReturn(true);
		
		boolean result = accountService.userOwnsAccount(accountId, ownerId);
		
		assertTrue(result);
		
		verify(accountRepository).existsByIdAndOwnerId(accountId, ownerId);
	}
	
	@Test
	void shouldFindByIdWithOwner() {
		
		when(accountRepository.findByIdWithOwner(accountId)).thenReturn(Optional.of(account));
		
		Optional<Account> result = accountService.findByIdWithOwner(accountId);
		
		assertTrue(result.isPresent());
		
		verify(accountRepository).findByIdWithOwner(accountId);
	}
	
	@Test
	void shouldFindByIdWithTransactions() {
		
		when(accountRepository.findByIdWithTransactions(accountId)).thenReturn(Optional.of(account));
		
		Optional<Account> result = accountService.findByIdWithTransactions(accountId);
		
		assertTrue(result.isPresent());
		
		verify(accountRepository).findByIdWithTransactions(accountId);
	}
	
	@Test
	void shouldFindByIdWithLock() {
		
		when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
		
		Optional<Account> result = accountService.findByIdWithLock(accountId);
		
		assertTrue(result.isPresent());
		
		verify(accountRepository).findByIdWithLock(accountId);
	}
	
	@Test
    void shouldFindByIdWithReadLock() {
        when(accountRepository.findByIdWithReadLock(accountId)).thenReturn(Optional.of(account));

        Optional<Account> result =
                accountService.findByIdWithReadLock(accountId);

        assertTrue(result.isPresent());

        verify(accountRepository).findByIdWithReadLock(accountId);
    }
	
	@Test
	void shouldUpdateBalance() {
		when(accountRepository.updateBalance(eq(accountId), eq(BigDecimal.valueOf(1000)), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> accountService.updateBalance(accountId,BigDecimal.valueOf(1000)));
		
		verify(accountRepository).updateBalance(eq(accountId), eq(BigDecimal.valueOf(1000)),any(LocalDateTime.class));
	}
	
	@Test
	void shouldThrowExceptionWhenUpdateBalance() {
		when(accountRepository.updateBalance(eq(accountId), any(BigDecimal.class), any(LocalDateTime.class))).thenReturn(0);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> accountService.updateBalance(accountId,BigDecimal.TEN));
		
		assertTrue(exception.getMessage().contains("Compte introuvable"));
		
		verify(accountRepository).updateBalance(eq(accountId), any(BigDecimal.class),any(LocalDateTime.class));
		
	}
	
	@Test
	void shouldUpdateStatus() {
		account.setStatus(AccountStatus.ACTIVE);

	    when(accountRepository.findById(accountId))
	            .thenReturn(Optional.of(account));
		when(accountRepository.updateStatus(eq(accountId), eq(AccountStatus.BLOCKED) , any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> accountService.updateStatus(accountId, AccountStatus.BLOCKED));
		
		verify(accountRepository).findById(accountId);
		
		verify(accountRepository).updateStatus(eq(accountId), eq(AccountStatus.BLOCKED) , any(LocalDateTime.class));

		verify(auditLogRepository)
        .save(any(AuditLog.class));
		
	}
	
	@Test
    void shouldThrowExceptionWhenUpdateStatusFails() {
		
		 when(accountRepository.findById(accountId))
         .thenReturn(Optional.empty());
		 

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateStatus(accountId, AccountStatus.CLOSED)
        );
        assertTrue(exception.getMessage().contains("Compte introuvable"));
        verify(accountRepository).findById(accountId);
        verify(accountRepository, never())
        .updateStatus(any(), any(), any());
    }
	@Test
    void shouldThrowExceptionWhenTransitionToPending() {
		
		account.setStatus(AccountStatus.BLOCKED);
		
		 when(accountRepository.findById(accountId))
         .thenReturn(Optional.of(account));
		 

		 UnauthorizedOperationException exception = assertThrows(
				 UnauthorizedOperationException.class,
                () -> accountService.updateStatus(accountId, AccountStatus.PENDING_VALIDATION)
        );
        assertTrue(exception.getMessage().contains("Transition interdite"));
        verify(accountRepository).findById(accountId);
        verify(accountRepository, never())
        .updateStatus(any(), any(), any());
    }
	@Test
    void shouldBlockAccount() {
		
		account.setStatus(AccountStatus.ACTIVE);

	    when(accountRepository.findById(accountId))
	            .thenReturn(Optional.of(account));
	    
        when(accountRepository.updateStatus(
                eq(accountId),
                eq(AccountStatus.BLOCKED),
                any(LocalDateTime.class)))
                .thenReturn(1);

        accountService.blockAccount(accountId);
        
        verify(accountRepository).findById(accountId);
        verify(accountRepository)
                .updateStatus(eq(accountId),
                        eq(AccountStatus.BLOCKED),
                        any(LocalDateTime.class));
        verify(auditLogRepository)
        .save(any(AuditLog.class));
    }
	
	@Test
    void shouldCloseAccount() {
		account.setStatus(AccountStatus.ACTIVE);
		
		when(accountRepository.findById(accountId))
        .thenReturn(Optional.of(account));
		
        when(accountRepository.updateStatus(
                eq(accountId),
                eq(AccountStatus.CLOSED),
                any(LocalDateTime.class)))
                .thenReturn(1);

        accountService.closeAccount(accountId);
        
        verify(accountRepository).findById(accountId);
        verify(accountRepository)
                .updateStatus(eq(accountId),
                        eq(AccountStatus.CLOSED),
                        any(LocalDateTime.class));
        verify(auditLogRepository)
        .save(any(AuditLog.class));
    }
	@Test
    void shouldSumBalanceByOwnerAndCurrency() {
        when(accountRepository.sumBalanceByOwnerAndCurrency(
                ownerId,
                CurrencyCode.EUR))
                .thenReturn(Optional.of(BigDecimal.valueOf(5000)));

        BigDecimal result =
                accountService.sumBalanceByOwnerAndCurrency(ownerId, CurrencyCode.EUR);

        assertEquals(BigDecimal.valueOf(5000), result);

        verify(accountRepository)
                .sumBalanceByOwnerAndCurrency(ownerId, CurrencyCode.EUR);
    }

    @Test
    void shouldReturnZeroWhenNoBalanceFound() {
        when(accountRepository.sumBalanceByOwnerAndCurrency(
                ownerId,
                CurrencyCode.EUR))
                .thenReturn(Optional.empty());

        BigDecimal result =
                accountService.sumBalanceByOwnerAndCurrency(ownerId, CurrencyCode.EUR);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void shouldCountAccountsByStatus() {
    	Object[] activeStats = new Object[] { AccountStatus.ACTIVE, 5L };
    	List<Object[]> stats = List.<Object[]>of(activeStats);
       

        when(accountRepository.countByStatus()).thenReturn(stats);

        List<Object[]> result = accountService.countAccountsByStatus();

        assertEquals(1, result.size());

        verify(accountRepository).countByStatus();
    }

    @Test
    void shouldFindOverdrawnAccounts() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Account> page = new PageImpl<>(List.of(account));

        when(accountRepository.findOverdrawnAccounts(pageable))
                .thenReturn(page);

        Page<Account> result =
                accountService.findOverdrawnAccounts(pageable);

        assertEquals(1, result.getContent().size());

        verify(accountRepository).findOverdrawnAccounts(pageable);
    }

    @Test
    void shouldFindDormantAccounts() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime since = LocalDateTime.now().minusMonths(6);

        Page<Account> page = new PageImpl<>(List.of(account));

        when(accountRepository.findDormantAccounts(since, pageable))
                .thenReturn(page);

        Page<Account> result =
                accountService.findDormantAccounts(since, pageable);

        assertEquals(1, result.getContent().size());

        verify(accountRepository).findDormantAccounts(since, pageable);
    }

    @Test
    void shouldFindAccountsWithExpiringCards() {
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusMonths(1);

        List<Account> accounts = List.of(account);

        when(accountRepository.findAccountsWithExpiringCards(from, to))
                .thenReturn(accounts);

        List<Account> result =
                accountService.findAccountsWithExpiringCards(from, to);

        assertEquals(1, result.size());

        verify(accountRepository)
                .findAccountsWithExpiringCards(from, to);
    }
    
    @Test
    void shouldFindByOwnerIdDTO_asOwner() {
    	
    	UUID accountId = account.getId();
    	UUID requesterId = ownerId;
    	
    	Set<UserRole> roles = Set.of(UserRole.CUSTOMER);
    	
    	AccountDTO expectedDTO = buildAccountDTO(accountId, requesterId);
    	
    	given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.of(account));
    	given(accountMapper.toDto(account)).willReturn(expectedDTO);
    	
    	AccountDTO result = accountService.findById(accountId, requesterId, roles);
    	
    	assertThat(result).isNotNull();
    	assertThat(result).isEqualTo(expectedDTO);
    	
    	then(accountRepository).should(times(1)).findByIdWithOwner(accountId);
    	then(accountMapper).should(times(1)).toDto(account);
    	
    	
    }
    @Test
    void shouldFindByOwnerIdDTO_asEmployerOrAdmin() {
    	
    	UUID strangerId = UUID.randomUUID();
    	Set<UserRole> roles = Set.of(UserRole.CUSTOMER, UserRole.TELLER);
    	
    	AccountDTO expectedDTO = buildAccountDTO(accountId, strangerId);
    	
    	given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.of(account));
    	given(accountMapper.toDto(account)).willReturn(expectedDTO);
    	
    	AccountDTO result = accountService.findById(accountId, strangerId, roles);
    	assertThat(result).isNotNull();
    	then(accountRepository).should(times(1)).findByIdWithOwner(accountId);
    	
    }
    @Test
    void findById_accountNotFound(){
    	
    	UUID unknownId = UUID.randomUUID();
		given(accountRepository.findByIdWithOwner(unknownId)).willReturn(Optional.empty());
		assertThatThrownBy(() -> 
			accountService.findById(unknownId, alice.getId(), Set.of(UserRole.CUSTOMER))
		).isInstanceOf(BankingException.class)
		 .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Compte introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
		 });
		
		then(accountMapper).should(never()).toDto(any());
    }
    @Test
    void findById_access_denied() {
    	
    	UUID unknowId = UUID.randomUUID();
    	given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.of(account));
    	
    	assertThatThrownBy(() -> 
		accountService.findById(accountId, unknowId, Set.of(UserRole.CUSTOMER))
		).isInstanceOf(BankingException.class)
		 .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Accès refusé à ce compte");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCESS_DENIED");
		 });
	
    	then(accountMapper).should(never()).toDto(any());
    }
    @Test
    void findByOwnerId_success() {
    	
    	AccountDTO.Summary summary1 = mock(AccountDTO.Summary.class);
    	given(accountRepository.findByOwnerIdOrderByCreatedAtDesc(alice.getId())).willReturn(List.of(account));
    	given(accountMapper.toSummary(account)).willReturn(summary1);
    	
    	List<AccountDTO.Summary> result = accountService.findByOwnerId(alice.getId());
    	
    	assertThat(result).isNotNull();
    	assertThat(result).hasSize(1);
    	assertThat(result).containsExactly(summary1);
    	then(accountRepository).should(times(1)).findByOwnerIdOrderByCreatedAtDesc(ownerId);
		then(accountMapper).should(times(1)).toSummary(account);
    	
    }
    @Test
    void findByOwnerId_emptyList(){
    	
    	UUID unknowId = UUID.randomUUID();
    	given(accountRepository.findByOwnerIdOrderByCreatedAtDesc(unknowId)).willReturn(List.of());
    	
    	List<AccountDTO.Summary> result = accountService.findByOwnerId(unknowId);
    	
    	assertThat(result).isNotNull();
    	assertThat(result).isEmpty();
    	

    	then(accountRepository).should(times(1)).findByOwnerIdOrderByCreatedAtDesc(unknowId);
		then(accountMapper).should(never()).toSummary(account);
    	
    }
    @Test
    void updateLabel_success() {
    	
    	Set<UserRole> roles = Set.of(UserRole.CUSTOMER);
    	AccountDTO expectedDto = buildAccountDTO(accountId, alice.getId());
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	given(accountRepository.save(account)).willReturn(account);
    	given(accountMapper.toDto(account)).willReturn(expectedDto);
    	
    	AccountDTO result = accountService.updateLabel(accountId,"label", alice.getId(), roles);
    	
    	assertThat(result).isNotNull();
    	
    	then(accountRepository).should(times(1)).findById(accountId);
    	then(accountRepository).should(times(1)).save(account);
    	then(accountMapper).should(times(1)).toDto(account);
    }
    @Test
    void updateLabel_asEmployeOrAdmin() {
    	Set<UserRole> roles = Set.of(UserRole.TELLER);
    	AccountDTO expectedDto = buildAccountDTO(accountId, alice.getId());
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	given(accountRepository.save(account)).willReturn(account);
    	given(accountMapper.toDto(account)).willReturn(expectedDto);
    	
    	AccountDTO result = accountService.updateLabel(accountId,"label", alice.getId(), roles);
    	
    	assertThat(result).isNotNull();
    	
    	then(accountRepository).should(times(1)).findById(accountId);
    	then(accountRepository).should(times(1)).save(account);
    	then(accountMapper).should(times(1)).toDto(account);
    }
    @Test
    void updateLabel_access_denied() {
    	
    	UUID unknowId = UUID.randomUUID();
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	
    	assertThatThrownBy(() -> 
		accountService.updateLabel(accountId, "label",unknowId, Set.of(UserRole.CUSTOMER))
		).isInstanceOf(BankingException.class)
		 .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Accès refusé à ce compte");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCESS_DENIED");
		 });
	
    	then(accountMapper).should(never()).toDto(any());
    }
    @Test
    void updateLabel_statusClosed() {
    	
    	account.setStatus(AccountStatus.CLOSED);
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	assertThatThrownBy(() -> 
		accountService.updateLabel(accountId, "label",alice.getId(), Set.of(UserRole.CUSTOMER))
		).isInstanceOf(BankingException.class)
		 .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Accès refusé à ce compte");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_CLOSED");
		 });
	
    	then(accountMapper).should(never()).toDto(any());
    }
    @Test
    void blockAccountSuccess() {

    	AccountDTO expectedDto = buildAccountDTO(accountId, alice.getId(), AccountStatus.BLOCKED);
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	given(accountRepository.updateStatus(eq(accountId), eq(AccountStatus.BLOCKED), any(LocalDateTime.class))).willReturn(1);
    	given(accountMapper.toDto(account)).willReturn(expectedDto);
    	
    	 AccountDTO accountDto =  accountService.blockAccount(accountId,"", UUID.randomUUID());
    	 
    	 assertThat(accountDto).isNotNull();
    	 assertThat(accountDto.status()).isEqualTo(AccountStatus.BLOCKED);
    	 then(accountRepository).should(times(1)).findById(accountId);
    	 then(accountRepository).should(times(1)).updateStatus(eq(accountId), eq(AccountStatus.BLOCKED), any(LocalDateTime.class));
    	 then(eventProducer).should(times(1)).publishAccountBlocked(any(AccountBlockedEvent.class));
    	 then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
    	 then(accountMapper).should(times(1)).toDto(account);
    	
    }
    @Test
    void blockAccount_failedWithClosed() {
    	
    	account.setStatus(AccountStatus.CLOSED);
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	
    	assertThatThrownBy(() -> 
			accountService.blockAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(UnauthorizedOperationException.class);
    	

   	    then(accountRepository).should(times(1)).findById(accountId);
	   	then(eventProducer).should(never()).publishAccountBlocked(any(AccountBlockedEvent.class));
	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.BLOCKED), any(LocalDateTime.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(accountMapper).should(never()).toDto(account);
    }
    @Test
    void blockAccount_notFoundAccount() {
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.empty());
    	
    	assertThatThrownBy(() -> 
			accountService.blockAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Compte introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
		 });;
    	

   	    then(accountRepository).should(times(1)).findById(accountId);
	   	then(eventProducer).should(never()).publishAccountBlocked(any(AccountBlockedEvent.class));
	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.BLOCKED), any(LocalDateTime.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(accountMapper).should(never()).toDto(account);
    	
    }
    @Test
    void unblockAccount_success() {
    	AccountDTO expectedDto = buildAccountDTO(accountId, alice.getId(), AccountStatus.ACTIVE);
    	account.setStatus(AccountStatus.BLOCKED);
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	given(accountRepository.updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class))).willReturn(1);
    	given(accountMapper.toDto(account)).willReturn(expectedDto);
    	
    	 AccountDTO accountDto =  accountService.unblockAccount(accountId,"", UUID.randomUUID());
    	 
    	 assertThat(accountDto).isNotNull();
    	 assertThat(accountDto.status()).isEqualTo(AccountStatus.ACTIVE);
    	 then(accountRepository).should(times(1)).findById(accountId);
    	 then(accountRepository).should(times(1)).updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class));
    	 then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
    	 then(accountMapper).should(times(1)).toDto(account);
    }
    @Test
    void unblockAccount_failedWithClosed() {
    	
    	account.setStatus(AccountStatus.CLOSED);
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	
    	assertThatThrownBy(() -> 
			accountService.unblockAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(UnauthorizedOperationException.class);
    	

   	    then(accountRepository).should(times(1)).findById(accountId);
	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(accountMapper).should(never()).toDto(account);
    }
    @Test
    void unblockAccount_notFoundAccount() {
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.empty());
    	
    	assertThatThrownBy(() -> 
			accountService.unblockAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Compte introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
		 });;
    	

   	    then(accountRepository).should(times(1)).findById(accountId);
	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(accountMapper).should(never()).toDto(account);
    	
    }
    @Test
    void closeAccount_success() {
    	
    	account.setBalance(BigDecimal.ZERO);
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	given(accountRepository.updateStatus(eq(accountId), eq(AccountStatus.CLOSED), any(LocalDateTime.class))).willReturn(1);
    	
    	accountService.closeAccount(accountId, "", UUID.randomUUID());
    	
    	then(accountRepository).should(times(1)).findById(accountId);
    	then(accountRepository).should(times(1)).updateStatus(eq(accountId), eq(AccountStatus.CLOSED), any(LocalDateTime.class));
    	then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
    	
    }
    @Test
    void closeAccountNotFound() {
    	
    	given(accountRepository.findById(accountId)).willReturn(Optional.empty());
    	
    	assertThatThrownBy(() -> 
			accountService.closeAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Compte introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
		 });;
    	

   	    then(accountRepository).should(times(1)).findById(accountId);
	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(accountMapper).should(never()).toDto(account);
    	
    }
    @Test
    void closeAccountFailled_balance() {
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	
    	assertThatThrownBy(() -> 
		accountService.closeAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Le solde");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_BALANCE_NOT_ZERO");
		 });
    	then(accountRepository).should(times(1)).findById(accountId);
 	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class));
 		then(auditLogRepository).should(never()).save(any(AuditLog.class));
 		then(accountMapper).should(never()).toDto(account);
    }
    @Test
    void closedAccount_failedWithClosed() {
    	account.setBalance(BigDecimal.ZERO);
    	account.setStatus(AccountStatus.CLOSED);
    	given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
    	
    	assertThatThrownBy(() -> 
			accountService.closeAccount(accountId, "label", UUID.randomUUID())
		).isInstanceOf(UnauthorizedOperationException.class);
    	

   	    then(accountRepository).should(times(1)).findById(accountId);
	   	then(accountRepository).should(never()).updateStatus(eq(accountId), eq(AccountStatus.ACTIVE), any(LocalDateTime.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(accountMapper).should(never()).toDto(account);
    }
    @Test
    void getTransactionsSuccess_withoutDate() {

    	Transaction tx = buildTransaction(account, TransactionType.SEPA_TRANSFER, TransactionStatus.APPROVED, BigDecimal.valueOf(100));
        TransactionDTO.Summary summary = buildTransactionDTOSummary(tx.getId());
        Pageable pageable = PageRequest.of(0, 10);
        
        Page<Transaction> txPage = new PageImpl<>(List.of(tx), pageable, 1);
        Set<UserRole> roles = Set.of(UserRole.CUSTOMER);

        given(accountRepository.findByIdWithOwner(account.getId())).willReturn(Optional.of(account));
        given(transactionRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable)).willReturn(txPage);
        given(transactionMapper.toSummary(tx)).willReturn(summary);

        Page<TransactionDTO.Summary> result = accountService.getTransactions(
            account.getId(), alice.getId(), roles, null, null, pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(summary);

        then(accountRepository).should(times(1)).findByIdWithOwner(account.getId());
        then(transactionRepository).should(times(1)).findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable);
        then(transactionRepository).should(never()).findByAccountIdAndPeriod(any(), any(), any(), any());
        then(transactionMapper).should(times(1)).toSummary(tx);
    	
    }
    @Test
    void getTransactionsSuccess_withDatePeriod() {
        // Given
        Transaction tx = buildTransaction(account, TransactionType.SEPA_TRANSFER, TransactionStatus.APPROVED, BigDecimal.valueOf(100));
        TransactionDTO.Summary summary = buildTransactionDTOSummary(tx.getId());
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> txPage = new PageImpl<>(List.of(tx), pageable, 1);
        Set<UserRole> roles = Set.of(UserRole.CUSTOMER);

        String fromStr = "2026-01-01T00:00:00";
        String toStr = "2026-01-31T23:59:59";

        given(accountRepository.findByIdWithOwner(account.getId())).willReturn(Optional.of(account));
        
        given(transactionRepository.findByAccountIdAndPeriod(eq(account.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
            .willReturn(txPage);
        given(transactionMapper.toSummary(tx)).willReturn(summary);

        // When
        Page<TransactionDTO.Summary> result = accountService.getTransactions(
            account.getId(), alice.getId(), roles, fromStr, toStr, pageable
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        then(accountRepository).should(times(1)).findByIdWithOwner(account.getId());
        then(transactionRepository).should(times(1)).findByAccountIdAndPeriod(eq(account.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable));
        then(transactionRepository).should(never()).findByAccountIdOrderByCreatedAtDesc(any(), any());
        then(transactionMapper).should(times(1)).toSummary(tx);
    }
  
	private AccountDTO buildAccountDTO(UUID accountId, UUID ownerId, AccountStatus status) {
        return new AccountDTO(
            accountId,
            "FR7630006000011234567890189",
            "ACC-001",
            AccountType.CURRENT,
            "Compte Courant",
            status,
            CurrencyCode.EUR,
            BigDecimal.valueOf(2000),
            BigDecimal.valueOf(2000),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "Compte principal",
            ownerId,
            "Alice Alice",
            java.time.LocalDateTime.now(),
            java.time.LocalDateTime.now(),
            null
        );
    }
    private AccountDTO buildAccountDTO(UUID accountId, UUID ownerId) {
    	return buildAccountDTO(accountId, ownerId, AccountStatus.ACTIVE);
    }
    private Transaction buildTransaction(Account account, TransactionType type, TransactionStatus status, BigDecimal amount) {
		Transaction tx = Transaction.create(
			"TXN-TEST-" + UUID.randomUUID().toString().substring(0, 6),
			type, amount, CurrencyCode.EUR, account, "Test"
		);
		tx.setStatus(status);
		tx.setId(UUID.randomUUID());
		return tx;
    }
    private TransactionDTO.Summary buildTransactionDTOSummary(UUID transactionId) {
        return new TransactionDTO.Summary(
            transactionId,
            "TXN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
            TransactionType.SEPA_TRANSFER,
            TransactionStatus.PENDING,
            BigDecimal.valueOf(150.00),
            CurrencyCode.EUR,
            "Bob Bob",
            "Remboursement dîner",
            java.time.LocalDateTime.now()
        );
    }
}
