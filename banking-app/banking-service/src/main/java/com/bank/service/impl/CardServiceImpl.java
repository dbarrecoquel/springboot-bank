package com.bank.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.common.dto.CardDTO;
import com.bank.common.exception.BankingException;
import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.common.mapper.CardMapper;
import com.bank.common.util.EncryptionUtil;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Card;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.CardStatus;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.CardRepository;
import com.bank.service.api.CardService;
import com.bank.service.api.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardServiceImpl implements CardService{
	
	private final CardRepository cardRepository;
	private final AuditLogRepository auditLogRepository;
	private final CardMapper cardMapper;
	private final AccountRepository accountRepository; 
	private final NotificationService notificationService;
	private final SessionCacheService sessionCacheService;
    @Value("${banking.encryption.key:MDEyMzQ1Njc4OUFCQ0RFRmowMTIzNDU2Nzg5QUJDREU=}")
    private String encryptionKeyBase64;
 
    // Plafonds maximaux autorisés pour un client (un manager peut dépasser)
    private static final BigDecimal MAX_CUSTOMER_PAYMENT_LIMIT    = new BigDecimal("3000.00");
    private static final BigDecimal MAX_CUSTOMER_WITHDRAWAL_LIMIT = new BigDecimal("1000.00");

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
		
		Card card = cardRepository.findById(id).orElseThrow(() -> new IllegalArgumentException(
	                "Card introuvable : " + id));
		
		
		if (!card.getStatus().canTransitionTo(status))
			 throw UnauthorizedOperationException.invalidTransition(
		                "Card", id,
		                card.getStatus().name(), status.name());
		
		cardRepository.updateStatus(id, status, LocalDateTime.now());
		
		auditLogRepository.save(AuditLog.success(
                "Card Updated", "Card",
                id.toString(), null,
                "status=" + status.name()
            ));

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
	@Override
	public List<CardDTO> findByOwner(UUID ownerID) {
		return cardRepository.findByOwnerIdOrderByCreatedAtDesc(ownerID).stream().map(cardMapper::toDto).collect(Collectors.toList());
	}
	@Override
    public CardDTO findById(UUID cardId, UUID requesterId, Set<UserRole> roles) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        assertOwnerOrOperator(card, requesterId, roles, "VIEW_CARD");
        return cardMapper.toDto(card);
    }
	@Override
	@Transactional
	public CardDTO issueCard(UUID accountId, UUID requesterId, String cardholderName, boolean virtual, CurrencyCode currency) {
		
		Account account = accountRepository.findByIdWithOwner(accountId).orElseThrow(() -> new BankingException(
                "Compte introuvable : " + accountId,
                "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND));
		
        boolean isOperator = isOperator(null, requesterId);
        if (!isOperator && !account.getOwner().getId().equals(requesterId)) {
            throw UnauthorizedOperationException.accessDenied(
                "ISSUE_CARD", "Account", accountId, requesterId);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankingException(
                    "Impossible d'émettre une carte sur un compte non actif.",
                    "ACCOUNT_NOT_ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);

        }
        
        User owner = account.getOwner();
        
        String pan = generatePan();
        String maskedPan = EncryptionUtil.maskPan(pan);
        
        SecretKey key = EncryptionUtil.decodeKey(encryptionKeyBase64);
        String panEncrypted =  EncryptionUtil.encrypt(pan, key);
        
        String cvvHash = new BCryptPasswordEncoder(12).encode(generateCvv());
        
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setPanEncrypted(panEncrypted);
        card.setPanMasked(maskedPan);
        card.setCvvHash(cvvHash);
        card.setCardholderName(cardholderName);
        card.setExpiryDate(LocalDate.now().plusYears(4).withDayOfMonth(1));
        card.setStatus(CardStatus.INACTIVE);
        card.setVirtual(virtual);
        card.setCurrency(currency);
        card.setOwner(owner);
        card.setAccount(account);
        card.setContactlessEnabled(true);
        card.setOnlinePaymentsEnabled(true);
        card.setInternationalPaymentsEnabled(false);
        card.setDailyPaymentLimit(new BigDecimal("1000.00"));
        card.setDailyWithdrawalLimit(new BigDecimal("500.00"));
 
        Card saved = cardRepository.save(card);
        if (owner.isEmailVerified()) {
            notificationService.sendEmailVerification(
                owner.getId(), owner.getEmail(),
                "CARD_ISSUED", owner.getFullName()
            );
        }
 
        auditLogRepository.save(AuditLog.success(
            "CARD_ISSUED", "Card",
            saved.getId().toString(), requesterId,
            "pan=" + maskedPan + " accountId=" + accountId
        ));
 

		return cardMapper.toDto(card);
		
	}
    @Override
    @Transactional
    public CardDTO activate(UUID cardId, UUID requesterId, String confirmationCode) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        
        assertOwner(card, requesterId, "ACTIVATE_CARD");
 
        if (card.getStatus() != CardStatus.INACTIVE) {
            throw UnauthorizedOperationException.invalidStatus(
                "ACTIVATE_CARD", "Card", cardId, card.getStatus().name());
        }
 
        card.activate();
        cardRepository.save(card);
        
        notificationService.sendCardActivated(
            card.getOwner().getId(),
            card.getOwner().getEmail(),
            card.getPanMasked(),
            card.isVirtual() ? "Carte virtuelle" : "Carte bancaire"
        );
 
        auditLogRepository.save(AuditLog.success(
            "CARD_ACTIVATED", "Card",
            cardId.toString(), requesterId,
            "pan=" + card.getPanMasked()
        ));
 
        log.info("[CARD] Activée — id={} pan={}", cardId, card.getPanMasked());
        return cardMapper.toDto(card);
    }
    @Override
    @Transactional
    public CardDTO block(UUID cardId, UUID requesterId,
                          Set<UserRole> roles, String reason) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        assertOwnerOrOperator(card, requesterId, roles, "BLOCK_CARD");
 
        if (!card.getStatus().canTransitionTo(CardStatus.BLOCKED)) {
            throw UnauthorizedOperationException.invalidStatus(
                "BLOCK_CARD", "Card", cardId, card.getStatus().name());
        }
 
        card.block();
        cardRepository.save(card);
 
        notificationService.sendCardBlocked(
            card.getOwner().getId(),
            card.getOwner().getEmail(),
            card.getPanMasked(),
            reason
        );
 
        auditLogRepository.save(AuditLog.success(
            "CARD_BLOCKED", "Card",
            cardId.toString(), requesterId,
            "reason=" + reason + " pan=" + card.getPanMasked()
        ));
 
        log.warn("[CARD] Bloquée — id={} reason={}", cardId, reason);
        return cardMapper.toDto(card);
    }
 
    @Override
    @Transactional
    public CardDTO unblock(UUID cardId, UUID operatorId) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
 
        if (card.getStatus() != CardStatus.BLOCKED) {
            throw UnauthorizedOperationException.invalidStatus(
                "UNBLOCK_CARD", "Card", cardId, card.getStatus().name());
        }
 
        card.setStatus(CardStatus.ACTIVE);
        card.resetPin();  // réinitialiser le compteur PIN au déblocage
        cardRepository.save(card);
        cardRepository.resetPinAttempts(cardId, LocalDateTime.now());
 
        auditLogRepository.save(AuditLog.success(
            "CARD_UNBLOCKED", "Card",
            cardId.toString(), operatorId,
            "pan=" + card.getPanMasked()
        ));
 
        log.info("[CARD] Débloquée — id={} operator={}", cardId, operatorId);
        return cardMapper.toDto(card);
    }
    @Override
    @Transactional
    public void cancel(UUID cardId, UUID requesterId,
                        Set<UserRole> roles, String reason) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        assertOwnerOrOperator(card, requesterId, roles, "CANCEL_CARD");
 
        if (card.getStatus().isTerminal()) {
            throw UnauthorizedOperationException.invalidStatus(
                "CANCEL_CARD", "Card", cardId, card.getStatus().name());
        }
 
        card.cancel();
        cardRepository.save(card);
 
        auditLogRepository.save(AuditLog.success(
            "CARD_CANCELLED", "Card",
            cardId.toString(), requesterId,
            "reason=" + reason + " pan=" + card.getPanMasked()
        ));
 
        log.info("[CARD] Annulée — id={} reason={}", cardId, reason);
    }
 
    @Override
    @Transactional
    public CardDTO updateLimits(UUID cardId, UUID requesterId, Set<UserRole> roles,
                                 BigDecimal dailyPaymentLimit,
                                 BigDecimal dailyWithdrawalLimit) {
        Card card =  cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        assertOwnerOrOperator(card, requesterId, roles, "UPDATE_LIMITS");
 
        // Un client ne peut pas dépasser les plafonds maximaux autorisés
        boolean isOperator = isOperator(roles, requesterId);
        if (!isOperator) {
            if (dailyPaymentLimit.compareTo(MAX_CUSTOMER_PAYMENT_LIMIT) > 0) {
                throw new BankingException(
                    "Plafond de paiement maximum autorisé : "
                    + MAX_CUSTOMER_PAYMENT_LIMIT + " " + card.getCurrency(),
                    "LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (dailyWithdrawalLimit.compareTo(MAX_CUSTOMER_WITHDRAWAL_LIMIT) > 0) {
                throw new BankingException(
                    "Plafond de retrait maximum autorisé : "
                    + MAX_CUSTOMER_WITHDRAWAL_LIMIT + " " + card.getCurrency(),
                    "LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
 
        card.setDailyPaymentLimit(dailyPaymentLimit);
        card.setDailyWithdrawalLimit(dailyWithdrawalLimit);
        Card saved = cardRepository.save(card);
 
        auditLogRepository.save(AuditLog.success(
            "CARD_LIMITS_UPDATED", "Card",
            cardId.toString(), requesterId,
            "paymentLimit=" + dailyPaymentLimit + " withdrawalLimit=" + dailyWithdrawalLimit
        ));
 
        log.info("[CARD] Plafonds mis à jour — id={} payment={} withdrawal={}",
                 cardId, dailyPaymentLimit, dailyWithdrawalLimit);
        return cardMapper.toDto(saved);
    }
    @Override
    @Transactional
    public CardDTO updateSettings(UUID cardId, UUID requesterId, Set<UserRole> roles,
                                   Boolean contactlessEnabled,
                                   Boolean onlinePaymentsEnabled,
                                   Boolean internationalPaymentsEnabled) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        assertOwnerOrOperator(card, requesterId, roles, "UPDATE_SETTINGS");
 
        if (contactlessEnabled          != null) card.setContactlessEnabled(contactlessEnabled);
        if (onlinePaymentsEnabled       != null) card.setOnlinePaymentsEnabled(onlinePaymentsEnabled);
        if (internationalPaymentsEnabled != null) card.setInternationalPaymentsEnabled(internationalPaymentsEnabled);
 
        Card saved = cardRepository.save(card);
 
        auditLogRepository.save(AuditLog.success(
            "CARD_SETTINGS_UPDATED", "Card",
            cardId.toString(), requesterId,
            "contactless=" + contactlessEnabled
            + " online=" + onlinePaymentsEnabled
            + " international=" + internationalPaymentsEnabled
        ));
 
        log.info("[CARD] Paramètres mis à jour — id={}", cardId);
        return cardMapper.toDto(saved);
    }
 
    // ─────────────────────────────────────────────────────────
    //  resetPin
    // ─────────────────────────────────────────────────────────
 
    @Override
    @Transactional
    public void resetPin(UUID cardId, UUID requesterId,
                          Set<UserRole> roles, String otpCode) {
        Card card = cardRepository.findByIdWithOwnerAndAccount(cardId)
                .orElseThrow(() -> new BankingException(
                        "Carte introuvable : " + cardId,
                        "CARD_NOT_FOUND", HttpStatus.NOT_FOUND));
        assertOwnerOrOperator(card, requesterId, roles, "RESET_PIN");
 
        // Vérifier l'OTP si demandeur est le client
        boolean isOperator = isOperator(roles, requesterId);
        if (!isOperator && otpCode != null) {
            boolean valid = sessionCacheService.verifyOtp(
                card.getOwner().getId(), "PIN_RESET", otpCode);
            if (!valid) {
                throw new BankingException(
                    "Code OTP invalide ou expiré.",
                    "OTP_INVALID", HttpStatus.BAD_REQUEST);
            }
        }
 
        // Réinitialiser le compteur PIN et débloquer si bloquée par PIN
        cardRepository.resetPinAttempts(cardId, LocalDateTime.now());
        card.resetPin();
 
        if (card.getStatus() == CardStatus.BLOCKED && card.isPinBlocked()) {
            card.setStatus(CardStatus.ACTIVE);
        }
 
        cardRepository.save(card);
 
        auditLogRepository.save(AuditLog.success(
            "CARD_PIN_RESET", "Card",
            cardId.toString(), requesterId,
            "pan=" + card.getPanMasked()
        ));
 
        log.info("[CARD] PIN réinitialisé — id={}", cardId);
    }

	private void assertOwnerOrOperator(Card card, UUID requesterId,
            Set<UserRole> roles, String operation) {
		
		if (!isOperator(roles, requesterId)
		&& !card.getOwner().getId().equals(requesterId)) {
			throw UnauthorizedOperationException.accessDenied(
					operation, "Card", card.getId(), requesterId);
		}
	}
    private void assertOwner(Card card, UUID requesterId, String operation) {
        if (!card.getOwner().getId().equals(requesterId)) {
            throw UnauthorizedOperationException.accessDenied(
                operation, "Card", card.getId(), requesterId);
        }
    }

	private boolean isOperator(Set<UserRole> roles, UUID requesterId) {
		if (roles == null) return false;
		return roles.contains(UserRole.TELLER)
		|| roles.contains(UserRole.MANAGER)
		|| roles.contains(UserRole.ADMIN);
	}
    private String generatePan() {
        StringBuilder sb = new StringBuilder("4");
        for (int i = 0; i < 15; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }
    private String generateCvv() {
        return String.format("%03d", (int) (Math.random() * 1000));
    }

}
