package com.bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bank.common.dto.UserDTO;
import com.bank.common.exception.BankingException;
import com.bank.common.mapper.UserMapper;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.Notification;
import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.notification.EmailAdapter;
import com.bank.infrastructure.persistence.AccountRepository;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.api.NotificationService;
import com.bank.service.impl.UserServiceImpl;

import net.bytebuddy.NamingStrategy.Suffixing.BaseNameResolver.ForGivenType;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
	@Mock
	private UserRepository userRepository;
	
	@Mock
	private UserMapper userMapper;
	
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private AuditLogRepository auditLogRepository;
	@Mock
	private NotificationService notificationService;
	@Mock
	private EmailAdapter emailAdapter;
	
	@Mock
	private SessionCacheService sessionCacheService;
	
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
	
	@Test
	void shouldFindAllPageable_success() {
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of(user));
		UserDTO.Summary expectedDTO = expectedUserDTOSummary(user);
		
		given(userRepository.findAll(pageable)).willReturn(page);
		given(userMapper.toSummary(user)).willReturn(expectedDTO);
		
		Page<UserDTO.Summary> summary = userService.findAll(pageable);
		
		assertThat(summary.getContent()).hasSize(1);
		assertThat(summary.getContent().get(0)).isEqualTo(expectedDTO);
		
		then(userRepository).should(times(1)).findAll(pageable);
		then(userMapper).should(times(1)).toSummary(user);
	}
	@Test
	void shouldFindAllPageable_empty() {
		Pageable pageable = PageRequest.of(0, 10); 
		Page<User> page = new PageImpl<User>(List.of());
		
		given(userRepository.findAll(pageable)).willReturn(page);
		
		Page<UserDTO.Summary> summary = userService.findAll(pageable);
		
		assertThat(summary.getContent()).isNotNull();
		assertThat(summary.getContent()).isEmpty();
		
		then(userRepository).should(times(1)).findAll(pageable);
		then(userMapper).should(never()).toSummary(user);
	}
/*	@Test
    void register_success() {
        // 1. GIVEN - Variables locales strictes et hermétiques
        String inputEmail = "test@test.fr";
        String inputPhone = "012345678";
        String inputPassword = "test";
        
        // On crée un faux profil pour le retour du mapper
        UserDTO.Profile mockProfile = mock(UserDTO.Profile.class);
        
        // Configuration des comportements de validation
        given(userRepository.existsByEmail(eq(inputEmail))).willReturn(false);
        given(userRepository.existsByPhoneNumber(eq(inputPhone))).willReturn(false);
        given(passwordEncoder.encode(eq(inputPassword))).willReturn("$2$a1234556");
        
        // L'astuce suprême : on accepte n'importe quel User créé par le service, 
        // et on retourne CE MÊME USER (au lieu de l'objet global du BeforeEach)
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User internalUser = invocation.getArgument(0);
            // Si l'id n'est pas encore mis par le service, on en force un pour l'audit log
            if (internalUser.getId() == null) {
                internalUser.setId(UUID.randomUUID());
            }
            return internalUser;
        });
        
        // On accepte n'mapper n'importe quel User vers notre faux profil
        given(userMapper.toProfile(any(User.class))).willReturn(mockProfile);
        
        // On neutralise la notification de manière préventive
        doNothing().when(notificationService).sendEmailVerification(any(), any(), any(), any());

        // 2. WHEN - Appel du service avec les variables locales
        UserDTO.Profile result = userService.register(
            "test", "test", LocalDate.now().minusYears(20), 
            inputEmail, inputPassword, inputPhone
        );

        // 3. THEN - Vérifications de bout en bout
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockProfile);
        
        then(userRepository).should(times(1)).existsByEmail(inputEmail);
        then(userRepository).should(times(1)).existsByPhoneNumber(inputPhone);
        then(passwordEncoder).should(times(1)).encode(inputPassword);
        then(userRepository).should(times(1)).save(any(User.class));
        then(notificationService).should(times(1)).sendEmailVerification(any(), eq(inputEmail), any(), any());
        then(auditLogRepository).should(times(1)).save(any(AuditLog.class)); // 🔓 Débloqué !
        then(userMapper).should(times(1)).toProfile(any(User.class));
    }
    */
	@Test
	void getProfileSuccess() {
		UserDTO.Profile expectedProfile = expectedUserDTOProfile(user); 
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(userMapper.toProfile(user)).willReturn(expectedProfile);
		
		UserDTO.Profile profile = userService.getProfile(userId);
		assertThat(profile).isNotNull();
		assertThat(expectedProfile).isEqualTo(profile);
		
		then(userRepository).should(times(1)).findById(userId);
		then(userMapper).should(times(1)).toProfile(user);
		
	}
	@Test
	void getProfileError() {
		given(userRepository.findById(userId)).willReturn(Optional.empty());
		
		assertThatThrownBy(() -> 
		 	userService.getProfile(userId)
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("Utilisateur introuvable");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("USER_NOT_FOUND");
		 });
		
		then(userRepository).should(times(1)).findById(userId);
		then(userMapper).should(never()).toProfile(user);
		
	}
	@Test
	void shouldUpdateUser()
	{
		UUID operatorId = UUID.randomUUID();
		UserDTO expectedDTO = expectedUserDTO(userId);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(userRepository.save(any(User.class)))
         .willAnswer(invocation -> invocation.getArgument(0));
		given(userMapper.toDto(any(User.class))).willReturn(expectedDTO);
		
		UserDTO result = userService.updateUser(
	            userId,
	            "Jane",
	            "Smith",
	            "10 rue Victor Hugo",
	            "Bâtiment A",
	            "Paris",
	            "75001",
	            "FR",
	            operatorId
	    );
		assertThat(result).isNotNull();
	    assertThat(user.getFirstName()).isEqualTo("Jane");
	    assertThat(user.getLastName()).isEqualTo("Smith");
	    assertThat(user.getAddressLine1()).isEqualTo("10 rue Victor Hugo");
	    assertThat(user.getAddressLine2()).isEqualTo("Bâtiment A");
	    assertThat(user.getCity()).isEqualTo("Paris");
	    assertThat(user.getPostalCode()).isEqualTo("75001");
	    assertThat(user.getCountryCode()).isEqualTo("FR");

	    then(userRepository).should().findById(userId);
	    then(userRepository).should().save(user);

	    then(auditLogRepository)
	            .should()
	            .save(any(AuditLog.class));

	    then(userMapper)
	            .should()
	            .toDto(user);
	}
	@Test
	void shouldUpdateOnlyProvidedFields() {
		
		UUID operatorId = UUID.randomUUID();
		UserDTO expectedDTO = expectedUserDTO(userId);
		user.setCity("Lille");
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(userRepository.save(any(User.class)))
         .willAnswer(invocation -> invocation.getArgument(0));
		given(userMapper.toDto(any(User.class))).willReturn(expectedDTO);
		UserDTO result =  userService.updateUser(
	            userId,
	            "Jane",
	            null,
	            null,
	            null,
	            null,
	            null,
	            null,
	            operatorId
	    );
		
		assertThat(result).isNotNull();
		assertEquals("Jane", user.getFirstName());
	    // inchangés
	    assertEquals("toto", user.getLastName());
	    assertEquals("Lille", user.getCity());
	    then(userRepository).should().findById(userId);
	    then(userRepository).should().save(user);
	    then(auditLogRepository)
        .should()
        .save(any(AuditLog.class));

	    then(userMapper)
        .should()
        .toDto(user);
	}
	@Test
	void shouldEnabledUser() {
		UUID operatorID = UUID.randomUUID();
		user.setEnabled(true);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(userRepository.save(any(User.class))).willReturn(user);
		
		userService.setEnabled(userId,false, operatorID);
		assertThat(user.isEnabled()).isFalse();
		then(userRepository).should(times(1)).findById(userId);
        then(userRepository).should(times(1)).save(user);
        
        then(sessionCacheService).should(times(1)).invalidateAllUserSessions(userId);
        then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
	}
	@Test
	void setEnabled_toTrue_shouldEnableUserWithoutInvalidatingSessions() {
        // Given (Arrange)
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        
        user.setId(userId);
        user.setEnabled(false);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willReturn(user);

        // When (Act)
        userService.setEnabled(userId, true, operatorId);

        // Then (Assert)
        assertThat(user.isEnabled()).isTrue();
        
        then(userRepository).should(times(1)).findById(userId);
        then(userRepository).should(times(1)).save(user);
        
        then(sessionCacheService).should(never()).invalidateAllUserSessions(any());
        
        then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
    }
	private UserDTO.Summary expectedUserDTOSummary(User user){
		
		return new UserDTO.Summary(user.getId(), 
				user.getFullName(), 
				user.getEmail(), 
				user.getPhoneNumber(),
				true,
				user.isKycVerified(),
				user.getRoles(),
				user.getCreatedAt());
		
	}
	@Test
	void shouldKycVerified() {
		UUID operatorID = UUID.randomUUID();
		user.setEnabled(true);
		user.setKycVerified(false);
		user.setEmailVerified(false);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		
		userService.validateKyc(userId, operatorID);
		assertThat(user.isKycVerified()).isTrue();
		then(userRepository).should(times(1)).findById(userId);
		then(userRepository).should(times(1)).validateKyc(eq(userId), any(LocalDateTime.class));
        then(userRepository).should(never()).save(user);   
        then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
	}
	@Test
	void shouldKycAlreadyVerified() {
		UUID operatorID = UUID.randomUUID();
		user.setEnabled(true);
		user.setKycVerified(true);
		user.setEmailVerified(false);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		assertThatThrownBy(() -> 
	 	userService.validateKyc(userId,operatorID)
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("KYC");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("KYC_ALREADY_VERIFIED");
		 });
		then(userRepository).should(times(1)).findById(userId);
		then(userRepository).should(never()).validateKyc(eq(userId), any(LocalDateTime.class));
        then(userRepository).should(never()).save(user);   
        then(auditLogRepository).should(never()).save(any(AuditLog.class));
	}
	@Test
	void addRoleSuccess() {
		UserRole newRole = UserRole.ADMIN;
		UUID operatorID = UUID.randomUUID();
		user.getRoles().add(UserRole.CUSTOMER);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(userRepository.save(any(User.class))).willReturn(user);
		
		userService.addRole(userId, newRole, operatorID);
		assertThat(user.hasRole(newRole)).isTrue();
		
		then(userRepository).should(times(1)).findById(userId);
		then(userRepository).should(times(1)).save(user);
		then(sessionCacheService).should(times(1)).invalidateAllUserSessions(userId);
        then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
	}
	@Test
	void addRoleFailled() {
		UserRole newRole = UserRole.CUSTOMER;
		UUID operatorID = UUID.randomUUID();
		user.getRoles().add(UserRole.CUSTOMER);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		
		
		assertThatThrownBy(() -> 
			userService.addRole(userId, newRole, operatorID)
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("utilisateur");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ROLE_ALREADY_EXISTS");
		 });
		
		then(userRepository).should(times(1)).findById(userId);
		then(userRepository).should(never()).save(user);
		then(sessionCacheService).should(never()).invalidateAllUserSessions(userId);
        then(auditLogRepository).should(never()).save(any(AuditLog.class));
	}
	@Test
	void removeRoleSuccess() {
		UserRole oldRole = UserRole.ADMIN;
		UUID operatorID = UUID.randomUUID();
		user.getRoles().add(UserRole.CUSTOMER);
		user.getRoles().add(UserRole.ADMIN);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(userRepository.save(any(User.class))).willReturn(user);
		
		userService.removeRole(userId, oldRole, operatorID);
		assertThat(user.hasRole(oldRole)).isFalse();
		
		then(userRepository).should(times(1)).findById(userId);
		then(userRepository).should(times(1)).save(user);
		then(sessionCacheService).should(times(1)).invalidateAllUserSessions(userId);
        then(auditLogRepository).should(times(1)).save(any(AuditLog.class));
	}
	@Test
	void removeRoleFailled() {
		UserRole oldRole = UserRole.ADMIN;
		UUID operatorID = UUID.randomUUID();
		user.getRoles().add(UserRole.CUSTOMER);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		
		
		assertThatThrownBy(() -> 
			userService.removeRole(userId, oldRole, operatorID)
		).isInstanceOf(BankingException.class) .satisfies(ex -> {
			 BankingException bankingEx = (BankingException) ex;
			 assertThat(bankingEx.getMessage()).contains("utilisateur");
			 assertThat(bankingEx.getErrorCode()).isEqualTo("ROLE_NOT_FOUND");
		 });
		
		then(userRepository).should(times(1)).findById(userId);
		then(userRepository).should(never()).save(user);
		then(sessionCacheService).should(never()).invalidateAllUserSessions(userId);
        then(auditLogRepository).should(never()).save(any(AuditLog.class));
	}
	@Test
	void findPendingKyc_success() {
		Pageable pageable = PageRequest.of(0, 10);
		List<User> userList = List.of(user);
		Page<User> userPage = new PageImpl<>(userList,pageable,userList.size());
		UserDTO.Summary expected = expectedUserDTOSummary(user);
		
		given(userRepository.findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(pageable)).willReturn(userPage);
		given(userMapper.toSummary(any(User.class))).willReturn(expected);
		
		Page<UserDTO.Summary> page = userService.findPendingKyc(pageable);
		assertThat(page).isNotNull();
		assertThat(page).hasSize(1);
		assertThat(page.getContent().get(0)).isEqualTo(expected);
		then(userRepository).should(times(1))
        .findByKycVerifiedFalseAndEnabledTrueOrderByCreatedAtAsc(pageable);
		
		
		
	}
	private UserDTO.Profile expectedUserDTOProfile(User user){
		
		return new UserDTO.Profile(userId,user.getFirstName(), user.getLastName(),user.getEmail(),user.getPhoneNumber(), true, true, true);
		
	}
	private UserDTO expectedUserDTO(UUID userId) {
	    return new UserDTO(
	        userId,
	        "John",
	        "Doe",
	        "John Doe",
	        java.time.LocalDate.now().minusYears(30), // Majeur (30 ans)
	        "FR",
	        "john.doe@test.fr",
	        "0123456789",
	        "123 Rue de la Banque",
	        "Appartement 4B",
	        "Paris",
	        "75001",
	        "FR",
	        true,                                     // enabled
	        true,                                     // emailVerified
	        false,                                    // phoneVerified
	        true,                                     // kycVerified
	        java.time.LocalDateTime.now().minusDays(5), // kycVerifiedAt
	        java.time.LocalDateTime.now().minusHours(2),// lastLoginAt
	        java.util.Set.of(UserRole.CUSTOMER),
	        java.time.LocalDateTime.now().minusMonths(1),// createdAt
	        java.time.LocalDateTime.now()              // updatedAt
	    );
	}
}
