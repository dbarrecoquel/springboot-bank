package com.bank.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CardStatus;
import com.bank.domain.enums.CurrencyCode;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.service.impl.AccountServiceImpl;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

	@Mock
	private AccountRepository accountRepository;
	
	@Mock
	private AuditLogRepository auditLogRepository;
	
	@InjectMocks
	private AccountServiceImpl accountService;
	
	private Account account;
	private UUID accountId;
	private UUID ownerId;
	
	@BeforeEach
	void setup(){
		
		accountId = UUID.randomUUID();
		ownerId = UUID.randomUUID();
		
		account = new Account();
		account.setId(accountId);
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
}
