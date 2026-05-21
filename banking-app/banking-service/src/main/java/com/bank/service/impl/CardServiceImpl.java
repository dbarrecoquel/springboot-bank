package com.bank.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.domain.entity.Card;
import com.bank.domain.enums.CardStatus;
import com.bank.infrastructure.persistence.CardRepository;
import com.bank.service.api.CardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardServiceImpl implements CardService{
	
	private final CardRepository cardRepository;
	
	@Override
	public List<Card> getAllCards() {
		return cardRepository.findAll();
	}
	@Override
	public Optional<Card> getCardById(UUID id) {
		return cardRepository.findById(id);
	}
		
	@Override
	@Transactional
	public Card saveCard(Card card) {
		return cardRepository.save(card);
	}
	@Override
	public Page<Card> getAllCards(Pageable page) {
		return cardRepository.findAll(page);
	}
	
	@Override
	@Transactional
	public void deleteCard(UUID id) {
		cardRepository.deleteById(id);
	}
	@Override
	public List<Card> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId) {
		return cardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}
	@Override
	public List<Card> findByAccountIdOrderByCreatedAtDesc(UUID accountId) {
		return cardRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
	}
	@Override
	public List<Card> findByOwnerIdAndStatus(UUID ownerId, CardStatus status) {
		return cardRepository.findByOwnerIdAndStatus(ownerId, status);
	}
	@Override
	public List<Card> findByAccountIdAndStatus(UUID accountId, CardStatus status) {
		return cardRepository.findByAccountIdAndStatus(accountId, status);
	}
	@Override
	public boolean existsByIdAndOwnerId(UUID cardId, UUID ownerdId) {
		return cardRepository.existsByIdAndOwnerId(cardId, ownerdId);
	}
	@Override
	public Page<Card> findByAccountIdAndStatus(UUID accountId, CardStatus status, Pageable pageable) {
		return cardRepository.findByAccountIdAndStatus(accountId, status, pageable);
	}
	@Override
	public Optional<Card> findByIdWithOwnerAndAccount(UUID id) {
		return cardRepository.findByIdWithOwnerAndAccount(id);
	}
	@Override
	public List<Card> findExpiringBetween(LocalDate from, LocalDate to) {
		return cardRepository.findExpiringBetween(from, to);
	}
	@Override
	public List<Card> findExpiredNotYetMarked() {
		return cardRepository.findExpiredNotYetMarked(LocalDate.now());
	}
	@Override
	@Transactional
	public void updateStatus(UUID id, CardStatus status) {
		
		int updated = cardRepository.updateStatus(id, status, LocalDateTime.now());
		
		if (updated == 0)
		{
			 throw new IllegalArgumentException("Carte introuvable : " + id);
		}
		
		 log.warn("[CARD] Status updated id={} status={}",id,status);
		
	}
	@Override
	@Transactional
	public void blockCard(UUID id) {
		
		updateStatus(id, CardStatus.BLOCKED);

	}
	@Override
	@Transactional
	public void expiredCard(UUID id) {
		updateStatus(id, CardStatus.EXPIRED);
	}
	@Override
	@Transactional
	public void cancelledCard(UUID id) {
		updateStatus(id, CardStatus.CANCELLED);
	}
	@Override
	@Transactional
	public void activeCard(UUID id) {
		updateStatus(id, CardStatus.ACTIVE);
	}
	@Override
	@Transactional
	public void disableCard(UUID id) {
		updateStatus(id, CardStatus.INACTIVE);
	}
	@Override
	@Transactional
	public void blockAllActiveByAccount(UUID accountId) {
		
		int updated = cardRepository.blockAllActiveByAccount(accountId, LocalDateTime.now());
		
		if (updated == 0)
		{
			 throw new IllegalArgumentException("Account introuvable : " + accountId);
		}
		
		log.warn("[CARD] blockAllActiveByAccount updated id={}", accountId);
		
	}
	@Override
	@Transactional
	public void cancelAllByAccount(UUID accountId) {
		int updated = cardRepository.cancelAllByAccount(accountId, LocalDateTime.now());
		
		if (updated == 0)
		{
			 throw new IllegalArgumentException("Account introuvable : " + accountId);
		}
		
		log.warn("[CARD] cancelAllByAccount updated id={}", accountId);
	}
	@Override
	@Transactional
	public void incrementPinAttempts(UUID id) {
		int updated = cardRepository.incrementPinAttempts(id, LocalDateTime.now());
		
		if (updated == 0)
		{
			 throw new IllegalArgumentException("Card introuvable : " + id);
		}
		
		log.warn("[CARD] incrementPinAttempts updated id={}", id);
	}
	@Override
	@Transactional
	public void blockByPinFailure(UUID id) {
		int updated = cardRepository.blockByPinFailure(id, LocalDateTime.now());
		
		if (updated == 0)
		{
			 throw new IllegalArgumentException("Card introuvable : " + id);
		}
		
		log.warn("[CARD] blockByPinFailure updated id={}", id);
	}
	@Override
	@Transactional
	public void resetPinAttempts(UUID id) {
		int updated = cardRepository.resetPinAttempts(id, LocalDateTime.now());
		
		if (updated == 0)
		{
			 throw new IllegalArgumentException("Card introuvable : " + id);
		}
		
		log.warn("[CARD] resetPinAttempts updated id={}", id);
	}
	@Override
	public List<Object[]> countByStatus() {
		return cardRepository.countByStatus();
	}
	@Override
	public Page<Card> findByPinBlockedTrueOrderByUpdatedAtDesc(Pageable pageable) {
		return cardRepository.findByPinBlockedTrueOrderByUpdatedAtDesc(pageable);
	}
	
}
