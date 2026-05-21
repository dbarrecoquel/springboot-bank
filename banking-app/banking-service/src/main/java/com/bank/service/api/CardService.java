package com.bank.service.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.Card;
import com.bank.domain.enums.CardStatus;

public interface CardService {

	public List<Card> getAllCards();
	public Optional<Card> getCardById(UUID id);
	public Card saveCard(Card card);
	public Page<Card> getAllCards(Pageable page);
	public void deleteCard(UUID id);
	
	public List<Card> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
	public List<Card> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
	public List<Card> findByOwnerIdAndStatus(UUID ownerId, CardStatus status);
	public List<Card> findByAccountIdAndStatus(UUID accountId, CardStatus status);
	public boolean existsByIdAndOwnerId(UUID cardId, UUID ownerdId);
	public Page<Card> findByAccountIdAndStatus(UUID accountId, CardStatus status, Pageable pageable);
	public Optional<Card> findByIdWithOwnerAndAccount(UUID id);
	public List<Card> findExpiringBetween(LocalDate from, LocalDate to);
	public List<Card> findExpiredNotYetMarked();
	public void updateStatus(UUID id, CardStatus status);
	public void blockCard(UUID id);
	public void expiredCard(UUID id);
	public void cancelledCard(UUID id);
	public void activeCard(UUID id);
	public void disableCard(UUID id);
	public void blockAllActiveByAccount(UUID accountId);
	public void cancelAllByAccount(UUID accountId);
	public void incrementPinAttempts(UUID id);
	public void blockByPinFailure(UUID id);
	public void resetPinAttempts(UUID id);
	public List<Object[]> countByStatus();
	public Page<Card> findByPinBlockedTrueOrderByUpdatedAtDesc(Pageable pageable);
}
