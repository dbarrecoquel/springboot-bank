package com.bank.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.common.dto.AccountDTO;
import com.bank.common.dto.UserDTO;
import com.bank.common.dto.UserDTO.Summary;
import com.bank.common.exception.BankingException;
import com.bank.common.mapper.UserMapper;
import com.bank.domain.entity.Account;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.api.NotificationService;
import com.bank.service.api.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final NotificationService notificationService;
	private final SessionCacheService sessionCacheService;
	private final AuditLogRepository auditLogRepository;
	@Override
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}
	@Override
	public Optional<User> getUserById(UUID id) {
		return userRepository.findById(id);
	}
		
	@Override
	@Transactional
	public User saveUser(User user) {
		return userRepository.save(user);
	}
	@Override
	public Page<User> getAllUsers(Pageable page) {
		return userRepository.findAll(page);
	}
	
	@Override
	@Transactional
	public void deleteUser(UUID id) {
		userRepository.deleteById(id);
	}
	@Override
	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email);
	}
	@Override
	public Optional<User> findByEmailWithRoles(String email) {
		return userRepository.findByEmailWithRoles(email);
	}
	@Override
	public boolean existsByEmail(String email) {
		return userRepository.existsByEmail(email);
	}
	@Override
	public boolean existsByPhoneNumber(String phoneNumber) {
		return userRepository.existsByPhoneNumber(phoneNumber);
	}
	@Override
	public Optional<User> findByPhoneNumber(String phoneNumber) {
		return userRepository.findByPhoneNumber(phoneNumber);
	}
	@Override
	public Optional<User> findByEmailOrPhone(String value) {
		return userRepository.findByEmailOrPhone(value);
	}
	@Override
	public Page<User> searchByNameOrEmail(String query, Pageable pageable) {
		return userRepository.searchByNameOrEmail(query, pageable);
	}
	@Override
	public Page<User> findByRole(UserRole role, Pageable pageable) {
		return userRepository.findByRole(role, pageable);
	}
	@Override
	public Page<User> findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(Pageable pageable) {
		return userRepository.findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(pageable);
	}
	@Override
	@Transactional
	public void incrementFailedLoginAttempts(UUID id) {
		
		int updated = userRepository.incrementFailedLoginAttempts(id, LocalDateTime.now());
		
		if (updated == 0) 
		{
            throw new IllegalArgumentException("user introuvable : " + id);
        }
		
		log.info("[User]failedLoginAttempt userId={}",id);
	}
	@Override
	@Transactional
	public void resetLoginAttempts(UUID id) {
		
		int updated = userRepository.resetLoginAttempts(id, LocalDateTime.now());
		
		if (updated == 0) 
		{
            throw new IllegalArgumentException("user introuvable : " + id);
        }
		
		log.info("[User]resetLoginAttempts userId={}",id);
	}
	@Override
	@Transactional
	public void lockUntil(UUID id, LocalDateTime until) {
		
		int updated = userRepository.lockUntil(id, until, LocalDateTime.now());
		
		if (updated == 0) 
		{
            throw new IllegalArgumentException("user introuvable : " + id);
        }
		
		log.info("[User]lockUntil userId={}",id);
	}
	@Override
	@Transactional
	public void verifyEmail(UUID id) {
		
		int updated = userRepository.verifyEmail(id, LocalDateTime.now());
		
		if (updated == 0) 
		{
            throw new IllegalArgumentException("user introuvable : " + id);
        }
		
		log.info("[User]verifyEmail userId={}",id);
	}
	@Override
	@Transactional
	public void validateKyc(UUID id) {
		
		int updated = userRepository.validateKyc(id, LocalDateTime.now());
		
		if (updated == 0) 
		{
            throw new IllegalArgumentException("user introuvable : " + id);
        }
		
		log.info("[User]validateKyc userId={}",id);
	}
	@Override
	public long countRegisteredBetween(LocalDateTime from, LocalDateTime to) {
		return userRepository.countRegisteredBetween(from, to);
	}
	@Override
	public Page<User> findInactiveUsers(LocalDateTime since, Pageable pageable) {
		return userRepository.findInactiveUsers(since, pageable);
	}
	@Override
	public Page<User> findByEnabledFalseOrderByUpdatedAtDesc(Pageable pageable) {
		return userRepository.findByEnabledFalseOrderByUpdatedAtDesc(pageable);
	}
	@Override
	public boolean existsByEmailOrPhone(String email, String phone) {
		return userRepository.existsByEmailOrPhone(email, phone);
	}
	@Override
	public boolean existsByEmailAndIdNot(String email, UUID excludeId) {
		return userRepository.existsByEmailAndIdNot(email, excludeId);
	}
	@Override
	public Optional<User> findByIdWithAccounts(UUID id) {
		return userRepository.findByIdWithAccounts(id);
	}
	@Override
	public Page<Summary> findAll(Pageable of) {
		
		Page<User> page = userRepository.findAll(of);
        
        List<UserDTO.Summary> summaries = page.getContent().stream().map(userMapper::toSummary).collect(Collectors.toList());
        
        return new PageImpl<UserDTO.Summary>(summaries, of, page.getTotalElements());
	}
	@Override
	@Transactional
	public UserDTO.Profile register(String firstName, String lastName, LocalDate dateOfBirth, String email, String password, String phoneNumber){
		
		if (userRepository.existsByEmail(email)) {
			
			throw new BankingException("Cette adresse email est déjà utilisée.",
	                "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT);
			
		}
		
		if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber))
		{
            throw new BankingException(
                    "Ce numéro de téléphone est déjà utilisé.",
                    "PHONE_ALREADY_EXISTS", HttpStatus.CONFLICT);
		}
		if (dateOfBirth != null
                && dateOfBirth.isAfter(LocalDate.now().minusYears(18))) {
            throw new BankingException(
                "Vous devez être majeur pour ouvrir un compte.",
                "UNDERAGE", HttpStatus.UNPROCESSABLE_ENTITY);
        }
		String passwordHash = passwordEncoder.encode(password);
		
		User user = User.create(firstName, lastName, dateOfBirth, email, passwordHash);
		user.setPhoneNumber(phoneNumber);
		user.setId(UUID.randomUUID());
		
		User saved = userRepository.save(user);
		
        notificationService.sendEmailVerification(
                saved.getId(), saved.getEmail(),
                UUID.randomUUID().toString(), saved.getFullName()
            );

        auditLogRepository.save(AuditLog.success(
                "USER_REGISTERED", "User",
                saved.getId().toString(), saved.getId(),
                "email=" + email
            ));
     
            log.info("[USER] Inscription — id={} email={}", saved.getId(), email);
	
		return userMapper.toProfile(user);
		
	}
    @Override
    @Transactional
    public UserDTO updateUser(UUID targetId,
                               String firstName, String lastName,
                               String addressLine1, String addressLine2,
                               String city, String postalCode, String countryCode,
                               UUID operatorId) {
        User user = userRepository.findById(targetId).orElseThrow(() -> new BankingException(
                "Utilisateur introuvable : " + targetId,
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND));
 
        if (firstName    != null) user.setFirstName(firstName);
        if (lastName     != null) user.setLastName(lastName);
        if (addressLine1 != null) user.setAddressLine1(addressLine1);
        if (addressLine2 != null) user.setAddressLine2(addressLine2);
        if (city         != null) user.setCity(city);
        if (postalCode   != null) user.setPostalCode(postalCode);
        if (countryCode  != null) user.setCountryCode(countryCode);
 
        User saved = userRepository.save(user);
 
        auditLogRepository.save(AuditLog.success(
            "USER_UPDATED", "User",
            targetId.toString(), operatorId,
            "operator=" + operatorId
        ));
 
        log.info("[USER] Modifié par opérateur — id={} operator={}", targetId, operatorId);
        return userMapper.toDto(saved);
    }

	@Override
    public UserDTO.Profile getProfile(UUID userId) {
		User user = userRepository.findById(userId).orElseThrow(() -> new BankingException(
                "Utilisateur introuvable : " + userId,
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        return userMapper.toProfile(user);
    }
	
    @Override
    @Transactional
    public void setEnabled(UUID userId, boolean enabled, UUID operatorId) {
        User user =  userRepository.findById(userId).orElseThrow(() -> new BankingException(
                "Utilisateur introuvable : " + userId,
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND));
        user.setEnabled(enabled);
        userRepository.save(user);
 
        if (!enabled) {
            // Invalider toutes les sessions actives
            sessionCacheService.invalidateAllUserSessions(userId);
        }
 
        auditLogRepository.save(AuditLog.success(
            enabled ? "USER_ENABLED" : "USER_DISABLED",
            "User", userId.toString(), operatorId,
            "operator=" + operatorId
        ));
 
        log.info("[USER] {} — id={} operator={}",
                 enabled ? "Activé" : "Désactivé", userId, operatorId);
    }

	
	

	
}
