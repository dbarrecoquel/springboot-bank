package com.bank.service.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bank.common.dto.UserDTO;
import com.bank.common.dto.UserDTO.Profile;
import com.bank.common.dto.UserDTO.Summary;
import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;

public interface UserService {

	public List<User> getAllUsers();
	public Optional<User> getUserById(UUID id);
	public User saveUser(User user);
	public Page<User> getAllUsers(Pageable page);
	public void deleteUser(UUID id);
	
	public Optional<User> findByEmail(String email);
	public Optional<User> findByEmailWithRoles(String email);
	public boolean existsByEmail(String email);
	public boolean existsByPhoneNumber(String phoneNumber);
	public Optional<User> findByPhoneNumber(String phoneNumber);
	public Optional<User> findByEmailOrPhone(String value);
	public Page<User> searchByNameOrEmail(String query, Pageable pageable);
	public Page<User> findByRole(UserRole role, Pageable pageable);
	public Page<User> findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(Pageable pageable);
	public void incrementFailedLoginAttempts(UUID id);
	public void resetLoginAttempts(UUID id);
	public void lockUntil(UUID id, LocalDateTime until);
	public void verifyEmail(UUID id);
	public void validateKyc(UUID id);
	public long countRegisteredBetween(LocalDateTime from, LocalDateTime to);
	public Page<User> findInactiveUsers(LocalDateTime since, Pageable pageable);
	public Page<User> findByEnabledFalseOrderByUpdatedAtDesc(Pageable pageable);
	public boolean existsByEmailOrPhone(String email, String phone);
	boolean existsByEmailAndIdNot(String email, UUID excludeId);
	public Optional<User> findByIdWithAccounts(UUID id);
	public Page<Summary> findAll(Pageable of);
	public Profile register(String firstName, String lastName, LocalDate dateOfBirth, String email, String password,
			String phoneNumber);
	public Profile getProfile(UUID userId);
	public UserDTO updateUser(UUID targetId, String firstName, String lastName, String addressLine1, String addressLine2,
			String city, String postalCode, String countryCode, UUID operatorId);
	public void setEnabled(UUID userId, boolean enabled, UUID operatorId);
	public void validateKyc(UUID userId, UUID operatorId);
	public void addRole(UUID userId, UserRole role, UUID operatorId);
	public void removeRole(UUID userId, UserRole role, UUID operatorId);
	public Page<Summary> findPendingKyc(Pageable pageable);
}
