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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.bank.common.dto.CardDTO;
import com.bank.common.exception.BankingException;
import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.common.mapper.CardMapper;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Card;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.CardStatus;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.CardRepository;
import com.bank.service.impl.CardServiceImpl;

@ExtendWith(MockitoExtension.class)
public class CardServiceTest {

	@Mock
	private CardRepository cardRepository;
	
	@Mock
	private AuditLogRepository auditLogRepository;
	
	@Mock
	private AccountRepository accountRepository;
	
	@Mock
	private CardMapper cardMapper;
	
	@InjectMocks
	private CardServiceImpl cardService;
	
	private Card card;
	private UUID cardId;
	private UUID ownerId;
	private UUID accountId;
	private User user;
	private Account account;
	
	
	
	
	@BeforeEach
	void setUp() {
		
		cardId = UUID.randomUUID();
		ownerId = UUID.randomUUID();
		accountId = UUID.randomUUID();
		user = new User();
		
		card = new Card();
		account = new Account();
		
		card.setId(cardId);
		card.setStatus(CardStatus.ACTIVE);
		user.setId(ownerId);
		account.setId(accountId);
		account.setOwner(user);
		card.setOwner(user);
		card.setAccount(account);
		ReflectionTestUtils.setField(
		        cardService, 
		        "encryptionKeyBase64", 
		        "MDEyMzQ1Njc4OUFCQ0RFRmowMTIzNDU2Nzg5QUJDREU="
		    );
	}
	
	@Test
	void shouldGetAllCards() {
		
		List<Card> cards = List.of(card);
		
		when(cardRepository.findAll()).thenReturn(cards);
		
		List<Card> result = cardService.getAllCards();
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findAll();
	}
	@Test
	void shouldGetAllCardsPageable() {
		
		Pageable pageable = PageRequest.of(0, 10);
		Page<Card> page = new PageImpl<Card>(List.of(card));
 		
		
		when(cardRepository.findAll(pageable)).thenReturn(page);
		
		Page<Card> result = cardService.getAllCards(pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(cardRepository).findAll(pageable);
	}
	@Test
	void shouldGetCardById() {
		
		when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
		
		Optional<Card> result = cardService.getCardById(cardId);
		
		assertTrue(result.isPresent());
		
		verify(cardRepository).findById(cardId);
	}
	@Test
	void shouldSaveCard() {
		
		when(cardRepository.save(card)).thenReturn(card);
		
		Card result = cardService.saveCard(card);
		
		assertNotNull(result);
		assertEquals(result.getStatus(),CardStatus.ACTIVE);
		
		verify(cardRepository).save(card);
	}
	@Test
	void shouldFindByOwnerIdOrderByCreatedAtDesc() {
		
		List<Card> cards = List.of(card);
		
		when(cardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(cards);
		
		List<Card> result = cardService.findByOwnerIdOrderByCreatedAtDesc(ownerId);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}
	@Test
	void shouldFindByAccountIdOrderByCreatedAtDesc() {
		
		List<Card> cards = List.of(card);
		
		when(cardRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).thenReturn(cards);
		
		List<Card> result = cardService.findByAccountIdOrderByCreatedAtDesc(accountId);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findByAccountIdOrderByCreatedAtDesc(accountId);
	}
	
	@Test
	void shouldFindByOwnerIdAndStatus() {
		
		List<Card> cards = List.of(card);
		
		when(cardRepository.findByOwnerIdAndStatus(ownerId,CardStatus.ACTIVE)).thenReturn(cards);
		
		List<Card> result = cardService.findByOwnerIdAndStatus(ownerId, CardStatus.ACTIVE);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findByOwnerIdAndStatus(ownerId, CardStatus.ACTIVE);
	}
	@Test
	void shouldFindByAccountIdAndStatus() {
		
		List<Card> cards = List.of(card);
		
		when(cardRepository.findByAccountIdAndStatus(accountId, CardStatus.ACTIVE)).thenReturn(cards);
		
		List<Card> result = cardService.findByAccountIdAndStatus(accountId, CardStatus.ACTIVE);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findByAccountIdAndStatus(accountId,CardStatus.ACTIVE);
	}
	
	@Test
	void shouldCheckExistsByIdAndOwnerId() {
		
		when(cardRepository.existsByIdAndOwnerId(cardId, ownerId)).thenReturn(true);
		
		boolean result = cardService.existsByIdAndOwnerId(cardId, ownerId);
		
		assertTrue(result);
		
		verify(cardRepository).existsByIdAndOwnerId(cardId, ownerId);
	}
	@Test
	void shouldFindByAccountIdAndStatusPageable() {
		
		Pageable pageable = PageRequest.of(0, 10);
		Page<Card> page = new PageImpl<Card>(List.of(card));
		
		when(cardRepository.findByAccountIdAndStatus(accountId, CardStatus.ACTIVE, pageable)).thenReturn(page);
		
		Page<Card> result = cardService.findByAccountIdAndStatus(accountId, CardStatus.ACTIVE, pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(cardRepository).findByAccountIdAndStatus(accountId,CardStatus.ACTIVE, pageable);
	}
	@Test
	void shouldFindByIdWithOwnerAndAccount() {
		
		when(cardRepository.findByIdWithOwnerAndAccount(cardId)).thenReturn(Optional.of(card));
		
		Optional<Card> result = cardService.findByIdWithOwnerAndAccount(cardId);
		
		assertTrue(result.isPresent());
		
		verify(cardRepository).findByIdWithOwnerAndAccount(cardId);
	}
	@Test
	void shouldFindExpiringBetween() {
		
		LocalDate from = LocalDate.now().minusYears(2);
		LocalDate to = LocalDate.now();
		List<Card> cards = List.of(card);
		
		when(cardRepository.findExpiringBetween(from, to)).thenReturn(cards);
		
		List<Card> result = cardService.findExpiringBetween(from, to);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findExpiringBetween(from, to);
	}
	@Test
	void shouldFindExpiredNotYetMarked() {
		
		List<Card> cards = List.of(card);
		
		when(cardRepository.findExpiredNotYetMarked(any(LocalDate.class))).thenReturn(cards);
		
		List<Card> result = cardService.findExpiredNotYetMarked();
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(cardRepository).findExpiredNotYetMarked(any(LocalDate.class));
		
	}
	
	@Test
	void shouldUpdateStatus() {

	    card.setStatus(CardStatus.ACTIVE);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    when(cardRepository.updateStatus(
	            eq(cardId),
	            eq(CardStatus.BLOCKED),
	            any(LocalDateTime.class)))
	            .thenReturn(1);

	    assertDoesNotThrow(() ->
	            cardService.updateStatus(cardId, CardStatus.BLOCKED));

	    verify(cardRepository)
	            .findById(cardId);

	    verify(cardRepository)
	            .updateStatus(
	                    eq(cardId),
	                    eq(CardStatus.BLOCKED),
	                    any(LocalDateTime.class));

	    verify(auditLogRepository)
	            .save(any(AuditLog.class));
	}

	@Test
	void shouldThrowExceptionWhenCardNotFound() {

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.empty());

	    IllegalArgumentException exception = assertThrows(
	            IllegalArgumentException.class,
	            () -> cardService.updateStatus(cardId, CardStatus.BLOCKED)
	    );

	    assertTrue(exception.getMessage().contains("Card introuvable"));

	    verify(cardRepository).findById(cardId);

	    verify(cardRepository, never())
	            .updateStatus(any(), any(), any());
	}

	@Test
	void shouldThrowUnauthorizedOperationExceptionWhenTransitionInvalid() {

	    card.setStatus(CardStatus.EXPIRED);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    assertThrows(
	            UnauthorizedOperationException.class,
	            () -> cardService.updateStatus(cardId, CardStatus.ACTIVE)
	    );

	    verify(cardRepository).findById(cardId);

	    verify(cardRepository, never())
	            .updateStatus(any(), any(), any());

	    verify(auditLogRepository, never())
	            .save(any(AuditLog.class));
	}

	@Test
	void shouldBlockCard() {

	    card.setStatus(CardStatus.ACTIVE);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    when(cardRepository.updateStatus(
	            eq(cardId),
	            eq(CardStatus.BLOCKED),
	            any(LocalDateTime.class)))
	            .thenReturn(1);

	    assertDoesNotThrow(() -> cardService.blockCard(cardId));

	    verify(cardRepository)
	            .updateStatus(
	                    eq(cardId),
	                    eq(CardStatus.BLOCKED),
	                    any(LocalDateTime.class));
	}

	@Test
	void shouldExpireCard() {

	    card.setStatus(CardStatus.ACTIVE);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    when(cardRepository.updateStatus(
	            eq(cardId),
	            eq(CardStatus.EXPIRED),
	            any(LocalDateTime.class)))
	            .thenReturn(1);

	    assertDoesNotThrow(() -> cardService.expiredCard(cardId));

	    verify(cardRepository)
	            .updateStatus(
	                    eq(cardId),
	                    eq(CardStatus.EXPIRED),
	                    any(LocalDateTime.class));
	}

	@Test
	void shouldCancelCardFromBlockedStatus() {

	    card.setStatus(CardStatus.BLOCKED);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    when(cardRepository.updateStatus(
	            eq(cardId),
	            eq(CardStatus.CANCELLED),
	            any(LocalDateTime.class)))
	            .thenReturn(1);

	    assertDoesNotThrow(() -> cardService.cancelledCard(cardId));

	    verify(cardRepository)
	            .updateStatus(
	                    eq(cardId),
	                    eq(CardStatus.CANCELLED),
	                    any(LocalDateTime.class));
	}

	@Test
	void shouldActivateCardFromInactiveStatus() {

	    card.setStatus(CardStatus.INACTIVE);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    when(cardRepository.updateStatus(
	            eq(cardId),
	            eq(CardStatus.ACTIVE),
	            any(LocalDateTime.class)))
	            .thenReturn(1);

	    assertDoesNotThrow(() -> cardService.activeCard(cardId));

	    verify(cardRepository)
	            .updateStatus(
	                    eq(cardId),
	                    eq(CardStatus.ACTIVE),
	                    any(LocalDateTime.class));
	}

	@Test
	void shouldReactivateBlockedCard() {

	    card.setStatus(CardStatus.BLOCKED);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    when(cardRepository.updateStatus(
	            eq(cardId),
	            eq(CardStatus.ACTIVE),
	            any(LocalDateTime.class)))
	            .thenReturn(1);

	    assertDoesNotThrow(() -> cardService.activeCard(cardId));

	    verify(cardRepository)
	            .updateStatus(
	                    eq(cardId),
	                    eq(CardStatus.ACTIVE),
	                    any(LocalDateTime.class));
	}

	@Test
	void shouldThrowExceptionWhenDisableCardTransitionInvalid() {

	    card.setStatus(CardStatus.ACTIVE);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    assertThrows(
	            UnauthorizedOperationException.class,
	            () -> cardService.disableCard(cardId)
	    );

	    verify(cardRepository).findById(cardId);

	    verify(cardRepository, never())
	            .updateStatus(any(), any(), any());
	}

	@Test
	void shouldThrowExceptionWhenTryingToReactivateCancelledCard() {

	    card.setStatus(CardStatus.CANCELLED);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    assertThrows(
	            UnauthorizedOperationException.class,
	            () -> cardService.activeCard(cardId)
	    );

	    verify(cardRepository).findById(cardId);

	    verify(cardRepository, never())
	            .updateStatus(any(), any(), any());
	}

	@Test
	void shouldThrowExceptionWhenTryingSameStatusTransition() {

	    card.setStatus(CardStatus.ACTIVE);

	    when(cardRepository.findById(cardId))
	            .thenReturn(Optional.of(card));

	    assertThrows(
	            UnauthorizedOperationException.class,
	            () -> cardService.updateStatus(cardId, CardStatus.ACTIVE)
	    );

	    verify(cardRepository).findById(cardId);

	    verify(cardRepository, never())
	            .updateStatus(any(), any(), any());
	}
	@Test
	void shouldBlockAllActiveByAccount() {
		
		when(cardRepository.blockAllActiveByAccount(eq(accountId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> cardService.blockAllActiveByAccount(accountId));
		
		verify(cardRepository).blockAllActiveByAccount(eq(accountId), any(LocalDateTime.class));
	}
	@Test
    void shouldThrowExceptionWhenBlockAllActiveByAccountFails() {
		
		when(cardRepository.blockAllActiveByAccount(eq(accountId), any(LocalDateTime.class))).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cardService.blockAllActiveByAccount(accountId)
        );
        
        assertTrue(exception.getMessage().contains("Account introuvable"));
        
        verify(cardRepository)
                .blockAllActiveByAccount(eq(accountId),
                        any(LocalDateTime.class));
    }
	@Test
	void shouldCancelAllByAccount() {
		
		when(cardRepository.cancelAllByAccount(eq(accountId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> cardService.cancelAllByAccount(accountId));
		
		verify(cardRepository).cancelAllByAccount(eq(accountId), any(LocalDateTime.class));
	}
	@Test
    void shouldThrowExceptionWhenCancelAllByAccountFails() {
		
		when(cardRepository.cancelAllByAccount(eq(accountId), any(LocalDateTime.class))).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cardService.cancelAllByAccount(accountId)
        );
        
        assertTrue(exception.getMessage().contains("Account introuvable"));
        
        verify(cardRepository)
                .cancelAllByAccount(eq(accountId),
                        any(LocalDateTime.class));
    }
	@Test
	void shouldIncrementPinAttempts() {
		
		when(cardRepository.incrementPinAttempts(eq(cardId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> cardService.incrementPinAttempts(cardId));
		
		verify(cardRepository).incrementPinAttempts(eq(cardId), any(LocalDateTime.class));
	}
	@Test
    void shouldThrowExceptionWhenIncrementPinAttemptsFails() {
		
		when(cardRepository.incrementPinAttempts(eq(cardId), any(LocalDateTime.class))).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cardService.incrementPinAttempts(cardId)
        );
        
        assertTrue(exception.getMessage().contains("Card introuvable"));
        
        verify(cardRepository)
                .incrementPinAttempts(eq(cardId),
                        any(LocalDateTime.class));
    }
	@Test
	void shouldBlockByPinFailure() {
		
		when(cardRepository.blockByPinFailure(eq(cardId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> cardService.blockByPinFailure(cardId));
		
		verify(cardRepository).blockByPinFailure(eq(cardId), any(LocalDateTime.class));
	}
	@Test
    void shouldThrowExceptionWhenBlockByPinFailureFails() {
		
		when(cardRepository.blockByPinFailure(eq(cardId), any(LocalDateTime.class))).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cardService.blockByPinFailure(cardId)
        );
        
        assertTrue(exception.getMessage().contains("Card introuvable"));
        
        verify(cardRepository)
                .blockByPinFailure(eq(cardId),
                        any(LocalDateTime.class));
    }
	@Test
	void shouldResetPinAttempts() {
		
		when(cardRepository.resetPinAttempts(eq(cardId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> cardService.resetPinAttempts(cardId));
		
		verify(cardRepository).resetPinAttempts(eq(cardId), any(LocalDateTime.class));
	}
	@Test
    void shouldThrowExceptionWhenResetPinAttemptsFails() {
		
		when(cardRepository.resetPinAttempts(eq(cardId), any(LocalDateTime.class))).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cardService.resetPinAttempts(cardId)
        );
        
        assertTrue(exception.getMessage().contains("Card introuvable"));
        
        verify(cardRepository)
                .resetPinAttempts(eq(cardId),
                        any(LocalDateTime.class));
    }
	@Test
	void shouldCountByStatus() {
		
		Object[] activeStats = new Object[] { CardStatus.ACTIVE, 5L };
    	List<Object[]> stats = List.<Object[]>of(activeStats);
    	
    	when(cardRepository.countByStatus()).thenReturn(stats);
    	
    	List<Object[]> result = cardService.countByStatus();
    	
    	assertEquals(1, result.size());
    	
    	Object[] firstRow = result.get(0);
        assertEquals(CardStatus.ACTIVE, firstRow[0]);
        assertEquals(5L, firstRow[1]);
        
        verify(cardRepository).countByStatus();
	}
	
	@Test
	void shouldFindByPinBlockedTrueOrderByUpdatedAtDesc(){
		
		Pageable pageable = PageRequest.of(0, 10);
		Page<Card> page = new PageImpl<Card>(List.of(card));
		
		when(cardRepository.findByPinBlockedTrueOrderByUpdatedAtDesc(pageable)).thenReturn(page);
		
		Page<Card> result = cardService.findByPinBlockedTrueOrderByUpdatedAtDesc(pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(cardRepository).findByPinBlockedTrueOrderByUpdatedAtDesc(pageable);
		
	}
	@Test
	void shouldFindByOWner_success() {
		
		CardDTO expectedDTO = buildCardDTO(cardId, accountId);
		
		given(cardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).willReturn(List.of(card));
		given(cardMapper.toDto(card)).willReturn(expectedDTO);
		
		List<CardDTO> result = cardService.findByOwner(ownerId);
		
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo(expectedDTO);
		
		then(cardRepository).should(times(1)).findByOwnerIdOrderByCreatedAtDesc(ownerId);
		then(cardMapper).should(times(1)).toDto(card);
		
	}
	@Test
	void shouldFindByOWner_empty() {
		
		
		given(cardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).willReturn(List.of());
		
		List<CardDTO> result = cardService.findByOwner(ownerId);
		
		assertThat(result).hasSize(0);
		
		then(cardRepository).should(times(1)).findByOwnerIdOrderByCreatedAtDesc(ownerId);
		then(cardMapper).should(never()).toDto(card);
		
	}
	@Test
	void shouldFindById_success() {
		
		CardDTO expectedDTO = buildCardDTO(cardId, accountId);
		Set<UserRole> roles = Set.of(UserRole.ADMIN);
		given(cardRepository.findByIdWithOwnerAndAccount(cardId)).willReturn(Optional.of(card));
		given(cardMapper.toDto(card)).willReturn(expectedDTO);
		
		CardDTO result = cardService.findById(cardId, UUID.randomUUID(), roles);
		
		assertThat(result).isNotNull();
		
		then(cardRepository).should(times(1)).findByIdWithOwnerAndAccount(cardId);
		then(cardMapper).should(times(1)).toDto(card);
		
	}
	@Test
	void shouldFindById_notFound() {
		
		Set<UserRole> roles = Set.of(UserRole.ADMIN);
		given(cardRepository.findByIdWithOwnerAndAccount(cardId)).willReturn(Optional.empty());
		
		
		assertThatThrownBy(() -> 
		 cardService.findById(cardId, UUID.randomUUID(), roles)
		).isInstanceOf(BankingException.class)
		 .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Carte introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("CARD_NOT_FOUND");
		 });
	
		
		then(cardRepository).should(times(1)).findByIdWithOwnerAndAccount(cardId);
		then(cardMapper).should(never()).toDto(card);
		
	}
	@Test
	void shouldFindById_notOperator() {
		
		given(cardRepository.findByIdWithOwnerAndAccount(cardId)).willReturn(Optional.of(card));
		
		
		assertThatThrownBy(() -> 
		 cardService.findById(cardId, UUID.randomUUID(), null)
		).isInstanceOf(UnauthorizedOperationException.class);
		 
	
		
		then(cardRepository).should(times(1)).findByIdWithOwnerAndAccount(cardId);
		then(cardMapper).should(never()).toDto(card);
		
	}
	@Test
	void shouldIssueThat_success() {

		account.setStatus(AccountStatus.ACTIVE);
		CardDTO expectedDTO = buildCardDTO(cardId, accountId);
		given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.of(account));
		given(cardMapper.toDto(any(Card.class))).willReturn(expectedDTO);
		given(cardRepository.save(any(Card.class))).willAnswer(invocation -> invocation.getArgument(0));
		
		CardDTO result = cardService.issueCard(accountId, ownerId,"test", false, CurrencyCode.EUR);
		
		assertThat(result).isNotNull();
		assertThat(result.id()).isEqualTo(cardId);
		then(accountRepository).should(times(1)).findByIdWithOwner(accountId);
		then(cardRepository).should(times(1)).save(any(Card.class));
		then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
		then(cardMapper).should(times(1)).toDto(any(Card.class));
	}
	@Test
	void shouldIssueThat_statusInactive() {

		account.setStatus(AccountStatus.BLOCKED);
		given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.of(account));
		
		assertThatThrownBy(() ->  cardService.issueCard(accountId, ownerId,"test", false, CurrencyCode.EUR))
		.isInstanceOf(BankingException.class).satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("compte non actif");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
		 });
		
		then(accountRepository).should(times(1)).findByIdWithOwner(accountId);
		then(cardRepository).should(never()).save(any(Card.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(cardMapper).should(never()).toDto(any(Card.class));
	}
	@Test
	void shouldIssueThat_unknowUser() {

		given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.empty());
		
		assertThatThrownBy(() ->  cardService.issueCard(accountId, ownerId,"test", false, CurrencyCode.EUR))
		.isInstanceOf(BankingException.class).satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Compte introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
		 });
		
		then(accountRepository).should(times(1)).findByIdWithOwner(accountId);
		then(cardRepository).should(never()).save(any(Card.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(cardMapper).should(never()).toDto(any(Card.class));
	}
	@Test
	void shouldIssueThat_invalidRequester() {

		given(accountRepository.findByIdWithOwner(accountId)).willReturn(Optional.of(account));
		
		assertThatThrownBy(() ->  cardService.issueCard(accountId, UUID.randomUUID(),"test", false, CurrencyCode.EUR))
		.isInstanceOf(UnauthorizedOperationException.class);
		then(accountRepository).should(times(1)).findByIdWithOwner(accountId);
		then(cardRepository).should(never()).save(any(Card.class));
		then(auditLogRepository).should(never()).save(any(AuditLog.class));
		then(cardMapper).should(never()).toDto(any(Card.class));
	}
	private CardDTO buildCardDTO(UUID cardId, UUID accountId) {
	    return new CardDTO(
	        cardId,
	        "4532XXXXXXXX1234",                       // panMasked
	        "ALEXIS DUPONT",                          // cardholderName
	        "12/29",                                  // expiryDate (MM/YY)
	        "ACTIVE",                                 // status
	        "Carte active",                           // statusLabel
	        false,                                    // virtual (carte physique)
	        true,                                     // contactlessEnabled
	        true,                                     // onlinePaymentsEnabled
	        false,                                    // internationalPaymentsEnabled
	        false,                                    // pinBlocked
	        new java.math.BigDecimal("1500.00"),      // dailyPaymentLimit
	        new java.math.BigDecimal("500.00"),       // dailyWithdrawalLimit
	        CurrencyCode.EUR,                         // currency (Enum à adapter selon ton projet)
	        accountId,                                // accountId
	        "FR7630006000012345678901234",            // accountIban
	        java.time.LocalDateTime.now().minusMonths(3), // activatedAt
	        java.time.LocalDateTime.now().minusMonths(3)  // createdAt
	    );
	}
	
}
