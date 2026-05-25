package com.bank.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.bank.domain.entity.Account;
import com.bank.domain.entity.Transaction;
import com.bank.domain.entity.User;
import com.bank.domain.enums.AccountType;
import com.bank.domain.enums.CurrencyCode;
import com.bank.domain.enums.TransactionStatus;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.messaging.TransactionEventProducer;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.TransactionRepository;
import com.bank.service.impl.FraudDetectionServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService — tests unitaires")
class FraudServiceTest {

   

}