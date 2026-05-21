package com.bank.service.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.Card;

public interface CardService {

	public List<Card> getAllCards();
	public Optional<Card> getCardById(UUID id);
	public Card saveCard(Card card);
	public Page<Card> getAllAds(Pageable page);
	public void deleteCard(UUID id);

}
