package com.bank.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.impl.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
	@Mock
	private UserRepository userRepository;
	
	@InjectMocks
	private UserServiceImpl userService;
	
	private User user;
	private UUID userId;
	
	@BeforeEach
	void setup() {

		userId = UUID.randomUUID();
	    user = new User();
		user.setId(userId);
		user.setEmail("test@test.fr");
		user.setFirstName("toto");
		user.setLastName("toto");
		user.setPhoneNumber("0123456789");
		
	}
	
	@Test
	void shouldFindAllUsers() {
		
		List<User> users = List.of(user);
		
		when(userRepository.findAll()).thenReturn(users);
		
		List<User> result = userService.getAllUsers();
		
		assertNotNull(result);
		assertEquals(1, result.size());
		
		verify(userRepository).findAll();
		
	}
	
	@Test
	void shouldFindAllUsersPageable() {
		
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.findAll(pageable)).thenReturn(page);
		
		Page<User> users = userService.getAllUsers(pageable);
		
		assertEquals(1, users.getContent().size());
		
		verify(userRepository).findAll(pageable);
	}
	
	@Test
	void shouldGetUserById() {
		
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.getUserById(userId);
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findById(userId);
	}
	
	@Test
	void shouldSaveUser() {
		
		when(userRepository.save(user)).thenReturn(user);
		
		User result = userService.saveUser(user);
		
		assertNotNull(result);
		assertEquals("toto", result.getFirstName());
		assertEquals("toto", result.getLastName());
		
		verify(userRepository).save(user);
		
	}
	
	@Test
	void shouldFindByEmail() {
		
		when(userRepository.findByEmail("toto@toto.fr")).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.findByEmail("toto@toto.fr");
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findByEmail("toto@toto.fr");
	}
	
	@Test
	void shouldFindByEmailWithRoles() {
		
		when(userRepository.findByEmailWithRoles("toto@toto.fr")).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.findByEmailWithRoles("toto@toto.fr");
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findByEmailWithRoles("toto@toto.fr");
	}
	
	@Test
	void shouldExistsByEmail() {
		
		when(userRepository.existsByEmail("toto@toto.fr")).thenReturn(true);
		
		boolean result = userService.existsByEmail("toto@toto.fr");
		
		assertTrue(result);
		
		verify(userRepository).existsByEmail("toto@toto.fr");
	}
	
	@Test
	void shouldFindByPhoneNumber() {
		
		when(userRepository.findByPhoneNumber("0123456789")).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.findByPhoneNumber("0123456789");
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findByPhoneNumber("0123456789");
	}
	@Test
	void shouldExistsByPhoneNumber() {
		
		when(userRepository.existsByPhoneNumber("0123456789")).thenReturn(true);
		
		boolean result = userService.existsByPhoneNumber("0123456789");
		
		assertTrue(result);
		
		verify(userRepository).existsByPhoneNumber("0123456789");
	}
	@Test
	void shouldFindByEmailOrPhoneNumber_usingEmail() {
		
		when(userRepository.findByEmailOrPhone("toto@toto.fr")).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.findByEmailOrPhone("toto@toto.fr");
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findByEmailOrPhone("toto@toto.fr");
	}
	@Test
	void shouldFindByEmailOrPhoneNumber_usingPhone() {
		
		when(userRepository.findByEmailOrPhone("0123456789")).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.findByEmailOrPhone("0123456789");
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findByEmailOrPhone("0123456789");
	}
	
	@Test
	void shouldSearchByEmailOrName_usingEmail() {

		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.searchByNameOrEmail("toto@toto.fr", pageable)).thenReturn(page);
		
		Page<User> result = userService.searchByNameOrEmail("toto@toto.fr", pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(userRepository).searchByNameOrEmail("toto@toto.fr", pageable);
		
	}
	@Test
	void shouldSearchByEmailOrName_usingName() {

		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.searchByNameOrEmail("toto", pageable)).thenReturn(page);
		
		Page<User> result = userService.searchByNameOrEmail("toto", pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(userRepository).searchByNameOrEmail("toto", pageable);
		
	}
	
	@Test
	void shouldFindByRole() {
		
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.findByRole(UserRole.CUSTOMER, pageable)).thenReturn(page);
		
		Page<User> result = userService.findByRole(UserRole.CUSTOMER, pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(userRepository).findByRole(UserRole.CUSTOMER, pageable);
		
	}
	
	@Test
	void shouldFindByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc() {
		
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(pageable)).thenReturn(page);
		
		Page<User> result = userService.findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(userRepository).findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(pageable);
	}
	
	@Test
	void shouldIncrementFailedLoginAttempts() {
		
		when(userRepository.incrementFailedLoginAttempts(eq(userId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> userService.incrementFailedLoginAttempts(userId));
		
		verify(userRepository).incrementFailedLoginAttempts(eq(userId), any(LocalDateTime.class));
	}
	@Test
	void shouldThrowExceptionWhenIncrementFailedLoginAttempts() {
		
		when(userRepository.incrementFailedLoginAttempts(eq(userId), any(LocalDateTime.class))).thenReturn(0);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> userService.incrementFailedLoginAttempts(userId));
		
		assertTrue(exception.getMessage().contains("user introuvable"));
		
		verify(userRepository).incrementFailedLoginAttempts(eq(userId), any(LocalDateTime.class));
		
	}
	@Test
	void shouldResetLoginAttempts() {
		
		when(userRepository.resetLoginAttempts(eq(userId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> userService.resetLoginAttempts(userId));
		
		verify(userRepository).resetLoginAttempts(eq(userId), any(LocalDateTime.class));
	}
	@Test
	void shouldThrowExceptionWhenResetLoginAttempts() {
		
		when(userRepository.resetLoginAttempts(eq(userId), any(LocalDateTime.class))).thenReturn(0);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> userService.resetLoginAttempts(userId));
		
		assertTrue(exception.getMessage().contains("user introuvable"));
		
		verify(userRepository).resetLoginAttempts(eq(userId), any(LocalDateTime.class));
		
	}
	@Test
	void shouldLockUntil() {
		
		LocalDateTime since = LocalDateTime.now().minusMonths(6);
		
		when(userRepository.lockUntil(eq(userId), eq(since), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> userService.lockUntil(userId, since));
		
		verify(userRepository).lockUntil(eq(userId), eq(since), any(LocalDateTime.class));
	}
	@Test
	void shouldThrowExceptionWhenLockUntil() {
		
		LocalDateTime since = LocalDateTime.now().minusMonths(6);
		
		when(userRepository.lockUntil(eq(userId), eq(since), any(LocalDateTime.class))).thenReturn(0);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> userService.lockUntil(userId, since));
		
		assertTrue(exception.getMessage().contains("user introuvable"));
		
		verify(userRepository).lockUntil(eq(userId), eq(since), any(LocalDateTime.class));
		
	}
	@Test
	void shouldVerifyEmail() {
		
		when(userRepository.verifyEmail(eq(userId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> userService.verifyEmail(userId));
		
		verify(userRepository).verifyEmail(eq(userId), any(LocalDateTime.class));
	}
	@Test
	void shouldThrowExceptionWhenVerifyEmail() {
		
		when(userRepository.verifyEmail(eq(userId), any(LocalDateTime.class))).thenReturn(0);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> userService.verifyEmail(userId));
		
		assertTrue(exception.getMessage().contains("user introuvable"));
		
		verify(userRepository).verifyEmail(eq(userId), any(LocalDateTime.class));
		
	}
	@Test
	void shouldValidateKyc() {
		
		when(userRepository.validateKyc(eq(userId), any(LocalDateTime.class))).thenReturn(1);
		
		assertDoesNotThrow(() -> userService.validateKyc(userId));
		
		verify(userRepository).validateKyc(eq(userId), any(LocalDateTime.class));
	}
	@Test
	void shouldThrowExceptionWhenValidateKyc() {
		
		when(userRepository.validateKyc(eq(userId), any(LocalDateTime.class))).thenReturn(0);
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> userService.validateKyc(userId));
		
		assertTrue(exception.getMessage().contains("user introuvable"));
		
		verify(userRepository).validateKyc(eq(userId), any(LocalDateTime.class));
		
	}
	@Test
	void shouldCountRegisteredBetween() {
		LocalDateTime from = LocalDateTime.now().minusMonths(6);
		LocalDateTime to = LocalDateTime.now();
		
		when(userRepository.countRegisteredBetween(from, to)).thenReturn(1L);
		
		long result = userRepository.countRegisteredBetween(from, to);
		
		assertEquals(1L, result);
		
		verify(userRepository).countRegisteredBetween(from, to);
	}
	
	@Test
	void shouldFindInactiveUsers() {

		LocalDateTime from = LocalDateTime.now().minusMonths(6);
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.findInactiveUsers(from, pageable)).thenReturn(page);
		
		Page<User> result = userService.findInactiveUsers(from, pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(userRepository).findInactiveUsers(from, pageable);
	}
	
	@Test
	void shouldFindByEnabledFalseOrderByUpdatedAtDesc() {
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		
		when(userRepository.findByEnabledFalseOrderByUpdatedAtDesc(pageable)).thenReturn(page);
		
		Page<User> result = userService.findByEnabledFalseOrderByUpdatedAtDesc(pageable);
		
		assertEquals(1, result.getContent().size());
		
		verify(userRepository).findByEnabledFalseOrderByUpdatedAtDesc(pageable);
		
	}
	
	@Test
	void shouldExistsByEmailOrPhone() {
		
		when(userRepository.existsByEmailOrPhone("toto@toto.fr", "0123456789")).thenReturn(true);
		
		boolean result = userService.existsByEmailOrPhone("toto@toto.fr", "0123456789");
		
		assertTrue(result);
		
		verify(userRepository).existsByEmailOrPhone("toto@toto.fr", "0123456789");
	}
	
	@Test
	void shouldExistsByEmailAndIdNot() {
		
		UUID random = UUID.randomUUID();
		
		when(userRepository.existsByEmailAndIdNot("toto@toto.fr", random)).thenReturn(true);
		
		boolean result = userService.existsByEmailAndIdNot("toto@toto.fr", random);
		
		verify(userRepository).existsByEmailAndIdNot("toto@toto.fr", random);
	}
	
	@Test
	void shouldFindByIdWithAccounts() {
		
		when(userRepository.findByIdWithAccounts(userId)).thenReturn(Optional.of(user));
		
		Optional<User> result = userService.findByIdWithAccounts(userId);
		
		assertTrue(result.isPresent());
		
		verify(userRepository).findByIdWithAccounts(userId);
		
	}
}
