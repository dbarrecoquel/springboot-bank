package com.bank.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.common.exception.InsufficientFundsException;
import com.bank.common.exception.UnauthorizedOperationException;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Transaction;
import com.bank.domain.enums.AccountStatus;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.enums.TransactionType;
import com.bank.domain.event.TransactionCreatedEvent;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.api.FraudDetectionService;
import com.bank.service.api.TransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

	private final TransactionRepository transactionRepository;
	private final AuditLogRepository auditLogRepository;
	private final AccountRepository accountRepository;
	private final FraudDetectionService fraudDetectionService;
	private final TransactionEventProducer eventProducer;
	@Override
	public List<Transaction> getAllTransactions() {
		return transactionRepository.findAll();
	}
	@Override
	public Optional<Transaction> getTransactionById(UUID id) {
		return transactionRepository.findById(id);
	}
		
	@Override
	@Transactional
	public Transaction saveTransaction(Transaction transaction) {
		return transactionRepository.save(transaction);
	}
	@Override
	public Page<Transaction> getAllTransactions(Pageable page) {
		return transactionRepository.findAll(page);
	}
	
	@Override
	@Transactional
	public void deleteTransaction(UUID id) {
		transactionRepository.deleteById(id);
	}
	@Override
	public Optional<Transaction> findByReference(String reference) {
		return transactionRepository.findByReference(reference);
	}
	@Override
	public boolean existsByReference(String reference) {
		return transactionRepository.existsByReference(reference);
	}
	@Override
	public Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable) {
		return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
	}
	@Override
	public Page<Transaction> findByAccountIdAndStatusOrderByCreatedAtDesc(UUID accountId, TransactionStatus status,
			Pageable pageable) {
		return transactionRepository.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status, pageable);
	}
	@Override
	public Page<Transaction> findByAccountIdAndTypeOrderByCreatedAtDesc(UUID accountId, TransactionType type,
			Pageable pageable) {
		
		return transactionRepository.findByAccountIdAndTypeOrderByCreatedAtDesc(accountId, type, pageable);
	}
	@Override
	public Page<Transaction> findByAccountIdAndPeriod(UUID accountId, LocalDateTime from, LocalDateTime to,
			Pageable pageable) {
		return transactionRepository.findByAccountIdAndPeriod(accountId, from, to, pageable);
	}
	@Override
	public List<Transaction> findForStatement(UUID accountId, LocalDateTime from, LocalDateTime to) {
		return transactionRepository.findForStatement(accountId, from, to);
	}
	@Override
	public List<Transaction> findStuckTransactions(LocalDateTime timeout) {
		return transactionRepository.findStuckTransactions(timeout);
	}
	@Override
	public Page<Transaction> findByStatusOrderByCreatedAtAsc(TransactionStatus status, Pageable pageable) {
		return transactionRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
	}
	@Override
	@Transactional
	public void updateStatus(UUID id, TransactionStatus status, LocalDateTime updatedAt) {
		
		Transaction tx = transactionRepository.findById(id)
	            .orElseThrow(() -> new IllegalArgumentException(
	                "Transaction introuvable : " + id));
	 
        if (!tx.getStatus().canTransitionTo(status)) {
            throw UnauthorizedOperationException.invalidTransition(
                "Transaction", id,
                tx.getStatus().name(), status.name());
        }
		
		transactionRepository.updateStatus(id, status, updatedAt);
		
		 auditLogRepository.save(AuditLog.success(
	                status.name(), "Transaction",
	                id.toString(), null,
	                "ref=" + tx.getReference()
	            ));
	     
	            log.info("[TX] status={} — id={} ref={}", status.name(), id, tx.getReference());
		
	}
	@Override  
	@Transactional
	public void pendingTransaction(UUID id, LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.PENDING, updatedAt);
	}
	@Override
	@Transactional
	public void processingTransaction(UUID id, LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.PROCESSING, updatedAt);
	}
	@Override
	@Transactional
	public void approvedTransaction(UUID id, LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.APPROVED, updatedAt);
	}
	
	@Override
	@Transactional
	public void refusedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.REFUSED, updatedAt);
	}
	@Override
	@Transactional
	public void cancelledTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.CANCELLED, updatedAt);
	}
	@Override
	@Transactional
	public void reversedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.REVERSED, updatedAt);
	}
	
	@Override
	@Transactional
	public void confirmedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.CONFIRMED, updatedAt);
	}
	@Override
	@Transactional
	public void blockedTransaction(UUID id , LocalDateTime updatedAt) {
		updateStatus(id,TransactionStatus.BLOCKED, updatedAt);
	}
	@Override
	@Transactional
	public void settle(UUID id) {
		
		Transaction tx = transactionRepository.findById(id)
	            .orElseThrow(() -> new IllegalArgumentException(
	                "Transaction introuvable : " + id));
	 
        if (!tx.getStatus().canTransitionTo(TransactionStatus.SETTLED)) {
            throw UnauthorizedOperationException.invalidTransition(
                "Transaction", id,
                tx.getStatus().name(), TransactionStatus.SETTLED.name());
        }

		transactionRepository.settle(id, LocalDateTime.now());
		
		
        auditLogRepository.save(AuditLog.success(
                "TRANSACTION_SETTLED", "Transaction",
                id.toString(), null,
                "ref=" + tx.getReference()
            ));
     
            log.info("[TX] Réglée — id={} ref={}", id, tx.getReference());


	}
	@Override
	@Transactional
	public void flagFraud(UUID id, BigDecimal score, LocalDateTime updatedAt) {
		
		int updated = transactionRepository.flagFraud(id, score, updatedAt);
		
		if (updated == 0)
		{
			throw new IllegalArgumentException("transaction id not found :" + id);
		}
		log.warn("[TRANSACTION] flagFraud updated id={} score={}",id,score);
	}
	@Override
	public long countRecentByAccount(UUID accountId, LocalDateTime since) {
		return transactionRepository.countRecentByAccount(accountId, since);
	}
	@Override
	public BigDecimal sumDebitedAmountSince(UUID accountId, LocalDateTime since) {
		return transactionRepository.sumDebitedAmountSince(accountId, since);
	}
	@Override
	public List<Transaction> findRecentByCounterpartIban(UUID accountId, String iban, LocalDateTime since) {
		return transactionRepository.findRecentByCounterpartIban(accountId, iban, since);
	}
	@Override
	public BigDecimal sumSettledByTypeAndPeriod(UUID accountId, List<TransactionType> types, LocalDateTime from,
			LocalDateTime to) {
		return transactionRepository.sumSettledByTypeAndPeriod(accountId, types, from, to);
	}
	@Override
	public List<Object[]> volumeByTypeAndPeriod(LocalDateTime from, LocalDateTime to) {
		return transactionRepository.volumeByTypeAndPeriod(from, to);
	}
	@Override
	public Page<Transaction> findAmlCandidates(BigDecimal threshold, LocalDateTime from, LocalDateTime to,
			Pageable pageable) {
		return transactionRepository.findAmlCandidates(threshold, from, to, pageable);
	}
	
	@Override
	public String generateReference() {
	    return "TX-" + UUID.randomUUID()
	            .toString()
	            .replace("-", "")
	            .substring(0, 16)
	            .toUpperCase();
	}
	@Override
	@Transactional
	public Transaction initiateSepaTransfer(UUID sourceAccountId, UUID requesterId, String destinationIban,
			String beneficiaryName, BigDecimal amount, CurrencyCode currency, String label, String endToEndId,
			boolean instant) {
		
		validateAmount(amount);
		validateLabel(label);
		
		//charger le compte source
		Account source = loadAndLock(sourceAccountId);
		
		//verifier l'ownerShip
		assertOwnerOrOperator(source, requesterId, endToEndId);
		
		//verifier le status du compte
		assertActive(source, "SEPA_TRANSFER");
	
		
		//verifier les fonds disponible
		assertSufficientFunds(source,amount);
		//debiter le compte
		source.debit(amount);
		accountRepository.save(source);
		
		//construire et persister la transaction
		
		String reference = generateReference();
		String ete = (endToEndId != null && !endToEndId.isBlank())
	            ? endToEndId : generateEndToEndId();
		
		Transaction tx = buildTx(reference, TransactionType.SEPA_TRANSFER, amount, currency, source, label);
		tx.setCounterpartIban(destinationIban);
		tx.setCounterpartName(beneficiaryName);
		tx.setEndToEndId(ete);
		tx.setStatus(TransactionStatus.PENDING);
		
		Transaction saved = transactionRepository.save(tx);
		
		// calculer le score de fraud et appliquer si necessaire;
		TransactionCreatedEvent event = TransactionCreatedEvent.of(
	            saved.getId(), reference, TransactionType.SEPA_TRANSFER,
	            amount, currency, sourceAccountId,
	            destinationIban, beneficiaryName,
	            source.getOwner().getId(), null, null
	        );
		
		BigDecimal fraudScore = fraudDetectionService.calculateRiskScore(event);
		
		if (fraudScore.compareTo(BigDecimal.valueOf(0.40)) >= 0) {
            transactionRepository.flagFraud(saved.getId(), fraudScore, LocalDateTime.now());
            fraudDetectionService.analyze(event);

		}
		
		eventProducer.publishTransactionCreated(event);
        auditLogRepository.save(AuditLog.success(
                "TRANSACTION_INITIATED", "Transaction",
                saved.getId().toString(), requesterId,
                "SEPA ref=" + reference + " amount=" + amount + " " + currency
            ));

		return saved;
	}
	@Override
	@Transactional
	public Transaction initiateInternalTransfer(UUID sourceAccountId, UUID destinationAccountId, UUID requesterId,
			BigDecimal amount, CurrencyCode currency, String label) {
		
		validateAmount(amount);
		validateLabel(label);
		
		if (sourceAccountId.equals(destinationAccountId)) {
			
            throw new IllegalArgumentException(
                    "Les comptes source et destination ne peuvent pas être identiques.");
	
		}
		
		// Verrous pessimistes sur les deux comptes (ordre déterministe pour éviter le deadlock)
		Account[] accounts = lockTwoAccounts(sourceAccountId, destinationAccountId);
		
        Account source = accounts[0];
        Account dest   = accounts[1];
        
        //verifications
        assertActive(source, "INTERNAL_TRANSFER");    
        assertSufficientFunds(source,amount);
        
        //mouvement
        source.debit(amount);
        dest.credit(amount);
        accountRepository.save(source);
        accountRepository.save(dest);
        
        //creation transaction
        String reference = generateReference();
        
        Transaction tx = buildTx(
            reference, TransactionType.INTERNAL_TRANSFER,
            amount, currency, source, label
        );
        tx.setCounterpartIban(dest.getIban());
        tx.setCounterpartName(dest.getOwner().getFullName());
        tx.setStatus(TransactionStatus.SETTLED); // interne → réglé immédiatement
 
        Transaction saved = transactionRepository.save(tx);
 
        TransactionCreatedEvent event = TransactionCreatedEvent.of(
            saved.getId(), reference, TransactionType.INTERNAL_TRANSFER,
            amount, currency, sourceAccountId,
            dest.getIban(), dest.getOwner().getFullName(),
            requesterId, null, null
        );
        eventProducer.publishTransactionCreated(event);
 
        log.info("[TX] Virement interne — ref={} amount={} {} from={} to={}",
                 reference, amount, currency, sourceAccountId, destinationAccountId);
 
        return saved;

	}
	@Override
	@Transactional
	public Transaction initiateInternationalTransfer(UUID sourceAccountId, UUID requesterId, String destinationIban, String beneficiaryName, String beneficiaryBic, BigDecimal amount, CurrencyCode currency, String label) {
		validateAmount(amount);
		validateLabel(label);
		
		Account source = loadAndLock(sourceAccountId);
		
		assertOwnerOrOperator(source, sourceAccountId, label);
		assertActive(source, "INTERNATIONAL_TRANSFER");
		assertSufficientFunds(source, amount);
		
		source.debit(amount);
		accountRepository.save(source);
		
		String reference = generateReference();
		
		Transaction tx = buildTx(
	            reference, TransactionType.INTERNATIONAL_TRANSFER,
	            amount, currency, source, label
	    );
		
		tx.setCounterpartIban(destinationIban);
        tx.setCounterpartName(beneficiaryName);
        tx.setCounterpartBic(beneficiaryBic);
        tx.setEndToEndId(resolveEndToEndId(null));
        
        Transaction saved = transactionRepository.save(tx);
        TransactionCreatedEvent event = buildEvent(saved, source, destinationIban, beneficiaryName, null);
        applyFraudCheck(saved, event);
        
        eventProducer.publishTransactionCreated(event);
        audit("TRANSACTION_SWIFT_INITIATED", saved, requesterId,
              "ref=" + reference + " bic=" + beneficiaryBic);
 
        log.info("[TX] SWIFT initié — ref={} amount={} {} bic={}", reference, amount, currency, beneficiaryBic);
        return saved;
		
	}
	@Override
	@Transactional
	public Transaction cashWithdrawal(UUID accountId, UUID cardId, UUID requesterId,
            BigDecimal amount, CurrencyCode currency) {
	
		validateAmount(amount);
		
		Account account = loadAndLock(accountId);
		
		assertActive(account,"CASH_WITHDRAWAL");
		assertSufficientFunds(account, amount);
		
		account.debit(amount);
		accountRepository.save(account);
		
		String reference = generateReference();
		
		Transaction tx = buildTx(reference, TransactionType.CASH_WITHDRAWAL, amount, currency, account, "Retrait espece");
		tx.setStatus(TransactionStatus.SETTLED);
		
		Transaction saved = transactionRepository.save(tx);
        eventProducer.publishTransactionCreated(
                buildEvent(saved, account, null, "DAB", cardId));
        
        audit("TRANSACTION_CASH_WITHDRAWAL", saved, requesterId, "ref=" + reference);
        
        log.info("[TX] Retrait DAB — ref={} amount={} {} account={}", reference, amount, currency, accountId);
        return saved;

	}

	@Override
	@Transactional
	public Transaction cashDeposit(UUID accountId, UUID operatorId, BigDecimal amount, CurrencyCode currency) {
		
		validateAmount(amount);
        
		Account account = loadAndLock(accountId);
        
        assertActive(account, "CASH_DEPOSIT");
        
        account.credit(amount);
        accountRepository.save(account);
        
        String reference = generateReference();
      
        Transaction tx = buildTx(reference, TransactionType.CASH_DEPOSIT, amount, currency, account, "Depot espece");
        tx.setStatus(TransactionStatus.SETTLED);
        
        Transaction saved = transactionRepository.save(tx);
        
        
        eventProducer.publishTransactionCreated(TransactionCreatedEvent.of(
            saved.getId(), reference, TransactionType.CASH_DEPOSIT,
            amount, currency, accountId, null, "Guichet",
            account.getOwner().getId(), null, null
        ));
 
        log.info("[TX] Dépôt espèces — ref={} amount={} {} account={}",
                 reference, amount, currency, accountId);
 
        return saved;

	}
	@Override
	@Transactional
	public Transaction cardPayment(UUID accountId, UUID cardId, UUID requesterId, BigDecimal amount, CurrencyCode currency, String merchandName, String label) {
		
		validateAmount(amount);
		
		Account account = loadAndLock(accountId);
		
		assertActive(account, "CARD_PAYMENT");
		assertSufficientFunds(account, amount);
		
		account.debit(amount);
		accountRepository.save(account);
		
		String reference = generateReference();
		Transaction tx = buildTx(reference, TransactionType.CARD_PAYMENT, amount, currency, account, label != null ? label : "Payment " + merchandName);
		tx.setCounterpartName(merchandName);
		
		Transaction saved = transactionRepository.save(tx);
		TransactionCreatedEvent event = buildEvent(saved, account, null, merchandName, cardId);
        applyFraudCheck(saved, event);
        eventProducer.publishTransactionCreated(event);
        audit("TRANSACTION_CARD_PAYMENT", saved, requesterId,
              "ref=" + reference + " merchant=" + merchandName);
 
        log.info("[TX] Paiement carte — ref={} amount={} {} merchant={}", reference, amount, currency, merchandName);
        return saved;
		
	}
	@Override
    @Transactional
    public Transaction cardRefund(UUID accountId, UUID originalTransactionId,
                                   UUID requesterId, BigDecimal amount,
                                   CurrencyCode currency, String reason) {
		
		validateAmount(amount);
		Account account = loadAndLock(accountId);
		
		assertActive(account, "CARD_REFUND");
		
		Transaction original = transactionRepository.findById(originalTransactionId)
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Transaction d'origine introuvable : " + originalTransactionId));
        if (original.getType() != TransactionType.CARD_PAYMENT) {
            throw new IllegalArgumentException(
                "La transaction d'origine n'est pas un paiement carte : "
                + original.getType());
        }

        if (amount.compareTo(original.getAmount()) > 0) {
            throw new IllegalArgumentException(
                "Le montant remboursé ne peut pas dépasser le montant d'origine : "
                + original.getAmount());
        }
        
        account.credit(amount);
        accountRepository.save(account);
        
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.CARD_REFUND, amount, currency, account, "Remboursement" + reason);
        tx.setCounterpartName(original.getCounterpartName());
        tx.setStatus(TransactionStatus.SETTLED);
        
        Transaction saved = transactionRepository.save(tx);
        eventProducer.publishTransactionCreated(
            buildEvent(saved, account, null, original.getCounterpartName(), null));
        audit("TRANSACTION_CARD_REFUND", saved, requesterId,
              "ref=" + reference + " originalRef=" + original.getReference());
 
        log.info("[TX] Remboursement carte — ref={} amount={} {} originalRef={}",
                 reference, amount, currency, original.getReference());
        return saved;

        
    }
	@Override
	@Transactional
	public Transaction directDebit(UUID accountId, String mandateId, String creditorName, String creditorIban, BigDecimal amount, CurrencyCode currency, String label) {
		
		validateAmount(amount);
        Account account = loadAndLock(accountId);
        assertActive(account, "DIRECT_DEBIT");
        assertSufficientFunds(account, amount);
        account.debit(amount);
        accountRepository.save(account);
 
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.DIRECT_DEBIT,
                                  amount, currency, account, label);
        tx.setCounterpartIban(creditorIban);
        tx.setCounterpartName(creditorName);
        tx.setMandateId(mandateId);
 
        Transaction saved = transactionRepository.save(tx);
        eventProducer.publishTransactionCreated(
            buildEvent(saved, account, creditorIban, creditorName, null));
        audit("TRANSACTION_DIRECT_DEBIT", saved, null,
              "ref=" + reference + " mandate=" + mandateId);
 
        log.info("[TX] Prélèvement SEPA — ref={} amount={} {} creditor={} mandate={}",
                 reference, amount, currency, creditorName, mandateId);
        return saved;

		
	}
    @Override
    @Transactional
    public Transaction directDebitRefund(UUID accountId, UUID originalTransactionId,
                                          UUID requesterId) {
        Transaction original = transactionRepository.findById(originalTransactionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Transaction d'origine introuvable : " + originalTransactionId));
 
        if (original.getType() != TransactionType.DIRECT_DEBIT) {
            throw new IllegalArgumentException(
                "La transaction d'origine n'est pas un prélèvement SEPA");
        }
 
        // Délai légal : 8 semaines pour une opération autorisée,
        // 13 mois pour une opération non autorisée
        if (original.getCreatedAt().isBefore(LocalDateTime.now().minusWeeks(8))) {
            throw new IllegalArgumentException(
                "Le délai légal de remboursement (8 semaines) est dépassé");
        }
 
        Account account = loadAndLock(accountId);
        assertActive(account, "DIRECT_DEBIT_REFUND");
 
        account.credit(original.getAmount());
        accountRepository.save(account);
 
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.DIRECT_DEBIT_REFUND,
                                  original.getAmount(), original.getCurrency(),
                                  account, "Remboursement prélèvement — " + original.getReference());
        tx.setCounterpartIban(original.getCounterpartIban());
        tx.setCounterpartName(original.getCounterpartName());
        tx.setMandateId(original.getMandateId());
        tx.setStatus(TransactionStatus.SETTLED);
 
        Transaction saved = transactionRepository.save(tx);
        eventProducer.publishTransactionCreated(
            buildEvent(saved, account, original.getCounterpartIban(),
                       original.getCounterpartName(), null));
        audit("TRANSACTION_DIRECT_DEBIT_REFUND", saved, requesterId,
              "ref=" + reference + " originalRef=" + original.getReference());
 
        log.info("[TX] Remboursement prélèvement — ref={} originalRef={}",
                 reference, original.getReference());
        return saved;
    }
    @Override
    @Transactional
    public Transaction creditInterest(UUID accountId, BigDecimal amount,
                                       CurrencyCode currency, String periodLabel) {
        validateAmount(amount);
 
        Account account = loadAndLock(accountId);
        // Les intérêts peuvent être crédités même sur un compte bloqué (obligation légale)
 
        account.credit(amount);
        accountRepository.save(account);
 
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.INTEREST_CREDIT,
                                  amount, currency, account, periodLabel);
        tx.setStatus(TransactionStatus.SETTLED);
 
        Transaction saved = transactionRepository.save(tx);
        audit("TRANSACTION_INTEREST_CREDIT", saved, null,
              "ref=" + reference + " period=" + periodLabel);
 
        log.info("[TX] Intérêts créditeurs — ref={} amount={} {} account={} period={}",
                 reference, amount, currency, accountId, periodLabel);
        return saved;
    }
    @Override
    @Transactional
    public Transaction debitInterest(UUID accountId, BigDecimal amount,
                                      CurrencyCode currency, String periodLabel) {
        validateAmount(amount);
 
        Account account = loadAndLock(accountId);
 
        account.debit(amount);
        accountRepository.save(account);
 
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.INTEREST_DEBIT,
                                  amount, currency, account, periodLabel);
        tx.setStatus(TransactionStatus.SETTLED);
 
        Transaction saved = transactionRepository.save(tx);
        audit("TRANSACTION_INTEREST_DEBIT", saved, null,
              "ref=" + reference + " period=" + periodLabel);
 
        log.info("[TX] Intérêts débiteurs — ref={} amount={} {} account={} period={}",
                 reference, amount, currency, accountId, periodLabel);
        return saved;
    }
    @Override
    @Transactional
    public Transaction applyFee(UUID accountId, BigDecimal amount,
                                 CurrencyCode currency, String feeType) {
        validateAmount(amount);
 
        Account account = loadAndLock(accountId);
 
        account.debit(amount);
        accountRepository.save(account);
 
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.FEE,
                                  amount, currency, account, feeType);
        tx.setStatus(TransactionStatus.SETTLED);
 
        Transaction saved = transactionRepository.save(tx);
        audit("TRANSACTION_FEE_APPLIED", saved, null,
              "ref=" + reference + " feeType=" + feeType);
 
        log.info("[TX] Frais bancaires — ref={} amount={} {} type={} account={}",
                 reference, amount, currency, feeType, accountId);
        return saved;
    }
    @Override
    @Transactional
    public Transaction currencyExchange(UUID sourceAccountId, UUID destinationAccountId,
                                         UUID requesterId, BigDecimal amount,
                                         CurrencyCode fromCurrency, CurrencyCode toCurrency,
                                         BigDecimal exchangeRate) {
        validateAmount(amount);
        if (fromCurrency == toCurrency) {
            throw new IllegalArgumentException(
                "Les devises source et destination doivent être différentes");
        }
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Le taux de change doit être strictement positif");
        }
 
        Account[] accounts = lockTwoAccounts(sourceAccountId, destinationAccountId);
        Account source = accounts[0];
        Account dest   = accounts[1];
 
        assertActive(source, "CURRENCY_EXCHANGE");
        assertSufficientFunds(source, amount);
 
        BigDecimal convertedAmount = amount.multiply(exchangeRate)
            .setScale(toCurrency.getDecimalPlaces(), java.math.RoundingMode.HALF_EVEN);
 
        source.debit(amount);
        dest.credit(convertedAmount);
        accountRepository.save(source);
        accountRepository.save(dest);
 
        String reference = generateReference();
        Transaction tx = buildTx(reference, TransactionType.CURRENCY_EXCHANGE,
                                  amount, fromCurrency, source,
                                  "Change " + fromCurrency + " → " + toCurrency
                                  + " (taux : " + exchangeRate + ")");
        tx.setExchangeRate(exchangeRate);
        tx.setAmountEur(fromCurrency == CurrencyCode.EUR ? amount : null);
        tx.setStatus(TransactionStatus.SETTLED);
 
        Transaction saved = transactionRepository.save(tx);
        eventProducer.publishTransactionCreated(
            buildEvent(saved, source, dest.getIban(), dest.getOwner().getFullName(), null));
        audit("TRANSACTION_CURRENCY_EXCHANGE", saved, requesterId,
              "ref=" + reference + " " + amount + " " + fromCurrency
              + " → " + convertedAmount + " " + toCurrency);
 
        log.info("[TX] Change — ref={} {} {} → {} {} rate={}",
                 reference, amount, fromCurrency, convertedAmount, toCurrency, exchangeRate);
        return saved;
    }

    private Account[] lockTwoAccounts(UUID sourceId, UUID destinationId) {
        UUID firstId  = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;
 
        Account first  = loadAndLock(firstId);
        Account second = loadAndLock(secondId);
 
        return firstId.equals(sourceId)
            ? new Account[]{first, second}
            : new Account[]{second, first};
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Le montant doit être strictement positif — reçu : " + amount);
        }
    }
 
    private void validateLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Le motif du virement est obligatoire");
        }
        if (label.length() > 140) {
            throw new IllegalArgumentException(
                "Le motif ne doit pas dépasser 140 caractères (norme SEPA)");
        }
    }
    private void assertSufficientFunds(Account account, BigDecimal amount) {
        if (!account.canDebit(amount)) {
            throw InsufficientFundsException.of(
                account.getId(), amount,
                account.availableBalance(), account.getCurrency().name());
        }
    }

    private String generateEndToEndId() {
        return "E2E-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    private void assertActive(Account account, String operation) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw UnauthorizedOperationException.invalidStatus(
                operation, "Account", account.getId(), account.getStatus().name());
        }
    }
    private Transaction buildTx(String reference, TransactionType type,
            BigDecimal amount, CurrencyCode currency,
            Account account, String label) {
    	
    	return Transaction.create(reference, type, amount, currency, account, label);
	}

	private TransactionCreatedEvent buildEvent(Transaction tx, Account source,
	                          String counterpartIban,
	                          String counterpartName,
	                          UUID cardId) {
		return TransactionCreatedEvent.of(
		tx.getId(), tx.getReference(), tx.getType(),
		tx.getAmount(), tx.getCurrency(),
		source.getId(), counterpartIban, counterpartName,
		source.getOwner().getId(), null, cardId
				);
	}
    private Account loadAndLock(UUID accountId) {
        return accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Compte introuvable : " + accountId));
    }
    private void assertOwnerOrOperator(Account account, UUID requesterId, String operation) {
        if (!account.getOwner().getId().equals(requesterId)) {
            boolean isOperator = accountRepository
                .existsByIdAndOwnerId(account.getId(), requesterId);
            if (!isOperator) {
                throw UnauthorizedOperationException.accessDenied(
                    operation, "Account", account.getId(), requesterId);
            }
        }
    }
    private String resolveEndToEndId(String provided) {
        return (provided != null && !provided.isBlank())
            ? provided
            : "E2E-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

	private void applyFraudCheck(Transaction saved, TransactionCreatedEvent event) {
		BigDecimal score = fraudDetectionService.calculateRiskScore(event);
		if (score.compareTo(new BigDecimal("0.40")) >= 0) {
		transactionRepository.flagFraud(saved.getId(), score, LocalDateTime.now());
		fraudDetectionService.analyze(event);
		}
	}

	private void audit(String action, Transaction tx, UUID actorId, String detail) {
		auditLogRepository.save(AuditLog.success(
				action, "Transaction", tx.getId().toString(), actorId, detail));
	}

}
