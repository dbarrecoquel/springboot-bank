package com.bank.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.Card;
import com.bank.infrastructure.persistence.CardRepository;
import com.bank.service.api.CardService;

public class CardServiceImpl implements CardService{
	
	private final CardRepository cardRepository;
	
	public CardServiceImpl(CardRepository cardRepository) {
		this.cardRepository = cardRepository;
	}
	
	@Override
	public List<Card> getAllCards() {
		return cardRepository.findAll();
	}
	@Override
	public Optional<Card> getCardById(UUID id) {
		return cardRepository.findById(id);
	}
		
	@Override
	public Card saveCard(Card card) {
		return cardRepository.save(card);
	}
	@Override
	public Page<Card> getAllAds(Pageable page) {
		return cardRepository.findAll(page);
	}
	
	@Override
	public void deleteCard(UUID id) {
		cardRepository.deleteById(id);
	}
}
