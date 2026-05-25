package com.bank.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Card;
import com.bank.domain.enums.CardStatus;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.CardRepository;
import com.bank.service.impl.CardServiceImpl;

@ExtendWith(MockitoExtension.class)
public class CardServiceTest {

	@Mock
	private CardRepository cardRepository;
	
	@Mock
	private AuditLogRepository auditLogRepository;
	
	@InjectMocks
	private CardServiceImpl cardService;
	
	private Card card;
	private UUID cardId;
	private UUID ownerId;
	private UUID accountId;
	
	
	
	
	
	@BeforeEach
	void setUp() {
		
		cardId = UUID.randomUUID();
		ownerId = UUID.randomUUID();
		accountId = UUID.randomUUID();
		
		card = new Card();
		card.setId(cardId);
		card.setStatus(CardStatus.ACTIVE);
	
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
	
}
