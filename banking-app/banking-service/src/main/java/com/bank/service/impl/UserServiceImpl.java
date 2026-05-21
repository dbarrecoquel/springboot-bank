package com.bank.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.api.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	
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
	
	
}
