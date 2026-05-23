package com.bank.service.impl;

import com.bank.common.dto.JwtResponse;
import com.bank.common.dto.LoginRequest;
import com.bank.common.exception.BankingException;
import com.bank.domain.entity.AuditLog;
import com.bank.domain.entity.User;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.cache.SessionCacheService.RefreshTokenData;
import com.bank.infrastructure.persistence.AuditLogRepository;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.security.JwtTokenProvider;
import com.bank.service.api.AuthService;
import com.bank.service.api.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implémentation du service d'authentification.
 *
 * <p>Dépendances croisées :</p>
 * <ul>
 *   <li>{@link JwtTokenProvider}    — génération / validation JWT (banking-security).</li>
 *   <li>{@link SessionCacheService} — blacklist + refresh tokens Redis (banking-infrastructure).</li>
 *   <li>{@link NotificationService} — envoi OTP, emails de sécurité (banking-service).</li>
 *   <li>{@link UserRepository}      — accès base de données (banking-infrastructure).</li>
 *   <li>{@link AuditLogRepository}  — traçabilité (banking-infrastructure).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository     userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtTokenProvider   jwtTokenProvider;
    private final SessionCacheService sessionCacheService;
    private final NotificationService notificationService;

    @Value("${banking.jwt.expiration:900000}")
    private long jwtExpirationMs;

    @Value("${banking.jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    @Value("${banking.app.url:https://app.bank.com}")
    private String appUrl;

    // Politique mot de passe : 8+ chars, 1 majuscule, 1 chiffre, 1 caractère spécial
    private static final Pattern PASSWORD_POLICY = Pattern.compile(
        "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,128}$"
    );

    // ─────────────────────────────────────────────────────────
    //  Authentification — login
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public JwtResponse login(LoginRequest request, String ipAddress, String userAgent) {

        // 1. Charger l'utilisateur avec ses rôles
        User user = userRepository.findByEmailWithRoles(request.email())
            .orElseThrow(() -> {
                log.warn("[AUTH] Tentative de connexion — email inconnu : {}", request.email());
                return authFailedException("Identifiants invalides");
            });

        // 2. Vérifier le compte non verrouillé
        if (user.isAccountLocked()) {
            log.warn("[AUTH] Compte verrouillé — userId={} until={}",
                     user.getId(), user.getLockedUntil());
            auditFailure("USER_LOGIN_FAILED", user, ipAddress, "Compte verrouillé");
            throw new BankingException(
                "Compte temporairement verrouillé. Réessayez dans 30 minutes.",
                "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN
            );
        }

        // 3. Vérifier le compte activé
        if (!user.isEnabled()) {
            auditFailure("USER_LOGIN_FAILED", user, ipAddress, "Compte désactivé");
            throw new BankingException(
                "Compte désactivé. Contactez votre conseiller.",
                "ACCOUNT_DISABLED", HttpStatus.FORBIDDEN
            );
        }

        // 4. Vérifier le mot de passe
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, ipAddress);
            throw authFailedException("Identifiants invalides");
        }

        // 5. Vérifier l'OTP si 2FA activé
        if (user.hasRole(com.bank.domain.enums.UserRole.CUSTOMER)
                && request.otpCode() != null) {
            try {
                boolean valid = sessionCacheService.verifyOtp(
                    user.getId(), "2FA", request.otpCode());
                if (!valid) {
                    auditFailure("USER_LOGIN_FAILED", user, ipAddress, "OTP incorrect");
                    throw authFailedException("Code OTP invalide");
                }
            } catch (SessionCacheService.OtpExpiredException ex) {
                throw new BankingException(
                    "Code OTP expiré. Demandez un nouveau code.",
                    "OTP_EXPIRED", HttpStatus.UNAUTHORIZED
                );
            } catch (SessionCacheService.OtpLockedException ex) {
                throw new BankingException(
                    "Trop de tentatives OTP incorrectes. Reconnectez-vous.",
                    "OTP_LOCKED", HttpStatus.FORBIDDEN
                );
            }
        }

        // 6. Login réussi — réinitialiser les tentatives
        user.recordSuccessfulLogin();
        userRepository.save(user);
        userRepository.resetLoginAttempts(user.getId(), LocalDateTime.now());

        // 7. Détecter le nouvel appareil
        String deviceId = resolveDeviceId(request.deviceId(), userAgent, ipAddress);
        boolean isNewDevice = isNewDevice(user.getId(), deviceId);
        if (isNewDevice) {
            notifyNewDevice(user, ipAddress, userAgent);
        }

        // 8. Générer les tokens
        String accessToken  = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 9. Stocker le refresh token en Redis
        sessionCacheService.storeRefreshToken(
            user.getId(), deviceId, refreshToken,
            user.getRoles(), request.rememberMe()
        );

        // 10. Auditer la connexion réussie
        auditSuccess("USER_LOGIN_SUCCESS", user, ipAddress,
                     "device=" + deviceId + " newDevice=" + isNewDevice);

        log.info("[AUTH] Connexion réussie — userId={} ip={} newDevice={}",
                 user.getId(), ipAddress, isNewDevice);

        return buildJwtResponse(user, accessToken, refreshToken, request.rememberMe());
    }

    // ─────────────────────────────────────────────────────────
    //  Refresh token
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public JwtResponse refreshToken(String refreshToken, String deviceId) {

        // 1. Extraire l'userId depuis le refresh token
        UUID userId = jwtTokenProvider.extractUserIdFromRefreshToken(refreshToken);

        // 2. Charger les données en cache Redis
        RefreshTokenData stored = sessionCacheService.getRefreshToken(userId, deviceId);
        if (stored == null || stored.isExpired()) {
            throw new BankingException(
                "Session expirée. Veuillez vous reconnecter.",
                "REFRESH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED
            );
        }

        // 3. Vérifier la correspondance du token
        if (!stored.token().equals(refreshToken)) {
            // Token inconnu — possible token replay attack
            log.error("[AUTH] Refresh token mismatch — userId={} deviceId={}", userId, deviceId);
            sessionCacheService.invalidateAllUserSessions(userId);
            throw new BankingException(
                "Session invalide. Toutes vos sessions ont été révoquées par sécurité.",
                "REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED
            );
        }

        // 4. Recharger l'utilisateur (rôles peuvent avoir changé)
        User user = userRepository.findByIdWithAccounts(userId)
            .orElseThrow(() -> new BankingException(
                "Utilisateur introuvable", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (!user.isEnabled() || user.isAccountLocked()) {
            sessionCacheService.invalidateAllUserSessions(userId);
            throw new BankingException(
                "Compte désactivé ou verrouillé.", "ACCOUNT_UNAVAILABLE", HttpStatus.FORBIDDEN);
        }

        // 5. Rotation du refresh token (sliding window)
        String newAccessToken  = jwtTokenProvider.generateToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        sessionCacheService.rotateRefreshToken(userId, deviceId, newRefreshToken, user.getRoles());

        log.debug("[AUTH] Refresh token roté — userId={} deviceId={}", userId, deviceId);

        return buildJwtResponse(user, newAccessToken, newRefreshToken, false);
    }

    // ─────────────────────────────────────────────────────────
    //  Logout
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void logout(UUID userId, String accessToken, String deviceId) {
        // Blacklister l'access token pour sa durée de vie restante
        Duration remaining = jwtTokenProvider.getRemainingValidity(accessToken);
        String   jti       = jwtTokenProvider.extractJti(accessToken);
        sessionCacheService.blacklistToken(jti, remaining);

        // Révoquer le refresh token du device
        sessionCacheService.revokeRefreshToken(userId, deviceId);

        auditLogRepository.save(AuditLog.success(
            "USER_LOGOUT", "User", userId.toString(), userId,
            "device=" + deviceId
        ));

        log.info("[AUTH] Déconnexion — userId={} deviceId={}", userId, deviceId);
    }

    @Override
    @Transactional
    public void logoutAllDevices(UUID userId, String accessToken) {
        // Blacklister l'access token courant
        Duration remaining = jwtTokenProvider.getRemainingValidity(accessToken);
        String   jti       = jwtTokenProvider.extractJti(accessToken);
        sessionCacheService.blacklistToken(jti, remaining);

        // Invalider toutes les sessions Redis
        sessionCacheService.invalidateAllUserSessions(userId);

        auditLogRepository.save(AuditLog.success(
            "USER_LOGOUT_ALL", "User", userId.toString(), userId,
            "Toutes les sessions révoquées"
        ));

        log.info("[AUTH] Déconnexion globale — userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────
    //  Vérification email
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void sendEmailVerification(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BankingException(
                "Utilisateur introuvable", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new BankingException(
                "Email déjà vérifié", "EMAIL_ALREADY_VERIFIED", HttpStatus.CONFLICT);
        }

        String token = UUID.randomUUID().toString();
        // Stocker le token en cache Redis (clé : otp:{userId}:EMAIL_VERIFY, TTL 24h)
        sessionCacheService.storeOtp(userId, "EMAIL_VERIFY", token);

        notificationService.sendEmailVerification(
            userId, user.getEmail(), token, user.getFullName());

        log.info("[AUTH] Email de vérification envoyé — userId={}", userId);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        // Le token contient l'userId encodé — format : {userId}:{otp}
        // En pratique, le lien de vérification redirige vers l'API avec userId + token
        throw new UnsupportedOperationException(
            "verifyEmail(token) doit être appelé avec verifyEmail(userId, token)");
    }

    /**
     * Version complète avec userId explicite — utilisée par le controller.
     */
    @Transactional
    public void verifyEmail(UUID userId, String token) {
        boolean valid = sessionCacheService.verifyOtp(userId, "EMAIL_VERIFY", token);
        if (!valid) {
            throw new BankingException(
                "Lien de vérification invalide ou expiré.",
                "EMAIL_VERIFY_INVALID", HttpStatus.BAD_REQUEST
            );
        }

        userRepository.verifyEmail(userId, LocalDateTime.now());

        auditLogRepository.save(AuditLog.success(
            "USER_EMAIL_VERIFIED", "User", userId.toString(), userId, null));

        log.info("[AUTH] Email vérifié — userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────
    //  Réinitialisation du mot de passe
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void initiatePasswordReset(String email) {
        // On ne révèle pas si l'email existe (protection énumération)
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            sessionCacheService.storeOtp(user.getId(), "RESET_PASSWORD", token);
            notificationService.sendPasswordReset(
                user.getId(), user.getEmail(), token, user.getFullName());

            log.info("[AUTH] Reset mot de passe initié — userId={}", user.getId());
        });
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        throw new UnsupportedOperationException(
            "resetPassword(token, newPassword) doit être appelé avec userId explicite");
    }

    /**
     * Version complète avec userId — utilisée par le controller.
     */
    @Transactional
    public void resetPassword(UUID userId, String token, String newPassword) {
        validatePasswordPolicy(newPassword);

        boolean valid = sessionCacheService.verifyOtp(userId, "RESET_PASSWORD", token);
        if (!valid) {
            throw new BankingException(
                "Lien de réinitialisation invalide ou expiré.",
                "RESET_TOKEN_INVALID", HttpStatus.BAD_REQUEST
            );
        }

        String newHash = passwordEncoder.encode(newPassword);

        User user = userRepository.findById(userId).orElseThrow(() ->
            new BankingException("Utilisateur introuvable",
                                 "USER_NOT_FOUND", HttpStatus.NOT_FOUND));
        user.setPasswordHash(newHash);
        userRepository.save(user);

        // Invalider toutes les sessions existantes
        sessionCacheService.invalidateAllUserSessions(userId);

        auditLogRepository.save(AuditLog.success(
            "USER_PASSWORD_RESET", "User", userId.toString(), userId, null));

        log.info("[AUTH] Mot de passe réinitialisé — userId={}", userId);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String currentPassword,
                                String newPassword, String currentDeviceId) {
        validatePasswordPolicy(newPassword);

        User user = userRepository.findById(userId).orElseThrow(() ->
            new BankingException("Utilisateur introuvable",
                                 "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

        // Vérifier l'ancien mot de passe
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            auditLogRepository.save(AuditLog.failure(
                "USER_PASSWORD_CHANGE_FAILED", "User",
                userId.toString(), userId, "Mot de passe actuel incorrect"
            ));
            throw new BankingException(
                "Mot de passe actuel incorrect.",
                "WRONG_CURRENT_PASSWORD", HttpStatus.BAD_REQUEST
            );
        }

        // Vérifier que le nouveau mot de passe est différent
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BankingException(
                "Le nouveau mot de passe doit être différent de l'ancien.",
                "SAME_PASSWORD", HttpStatus.BAD_REQUEST
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalider toutes les sessions SAUF le device courant
        sessionCacheService.invalidateAllUserSessions(userId);
        // Re-stocker la session courante si nécessaire — géré par le controller
        // qui appellera refreshToken() après le changement

        auditLogRepository.save(AuditLog.success(
            "USER_PASSWORD_CHANGED", "User", userId.toString(), userId,
            "device=" + currentDeviceId
        ));

        log.info("[AUTH] Mot de passe changé — userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────
    //  Validation de token
    // ─────────────────────────────────────────────────────────

    @Override
    public boolean validateToken(String token) {
        if (!jwtTokenProvider.isTokenValid(token)) return false;
        String jti = jwtTokenProvider.extractJti(token);
        return !sessionCacheService.isTokenBlacklisted(jti);
    }

    @Override
    public UUID extractUserId(String token) {
        if (!validateToken(token)) {
            throw new BankingException(
                "Token invalide ou expiré.", "TOKEN_INVALID", HttpStatus.UNAUTHORIZED);
        }
        return jwtTokenProvider.extractUserId(token);
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers privés
    // ─────────────────────────────────────────────────────────

    private void handleFailedLogin(User user, String ipAddress) {
        user.recordFailedLogin();
        userRepository.save(user);
        userRepository.incrementFailedLoginAttempts(user.getId(), LocalDateTime.now());

        if (user.isAccountLocked()) {
            log.warn("[AUTH] Compte verrouillé après échecs — userId={}", user.getId());
        }

        auditFailure("USER_LOGIN_FAILED", user, ipAddress,
                     "Tentative " + user.getFailedLoginAttempts() + "/5");
    }

    private String resolveDeviceId(String requestDeviceId,
                                    String userAgent, String ipAddress) {
        if (requestDeviceId != null && !requestDeviceId.isBlank()) {
            return requestDeviceId;
        }
        // Générer un deviceId stable basé sur userAgent + ip (fingerprinting léger)
        return UUID.nameUUIDFromBytes(
            (userAgent + ":" + ipAddress).getBytes()).toString();
    }

    private boolean isNewDevice(UUID userId, String deviceId) {
        return sessionCacheService.getRefreshToken(userId, deviceId) == null;
    }

    private void notifyNewDevice(User user, String ipAddress, String userAgent) {
        try {
            String loginTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));
            notificationService.sendNewDeviceLoginAlert(
                user.getId(), user.getEmail(), ipAddress, userAgent, loginTime);
        } catch (Exception ex) {
            // Ne pas bloquer le login si la notification échoue
            log.warn("[AUTH] Échec notification nouvel appareil — userId={} error={}",
                     user.getId(), ex.getMessage());
        }
    }

    private JwtResponse buildJwtResponse(User user, String accessToken,
                                          String refreshToken, boolean rememberMe) {
        long ttlMs = rememberMe ? refreshExpirationMs * 4L : refreshExpirationMs;

        return JwtResponse.of(
            accessToken,
            refreshToken,
            jwtExpirationMs / 1000,
            LocalDateTime.now().plus(Duration.ofMillis(jwtExpirationMs)),
            LocalDateTime.now().plus(Duration.ofMillis(ttlMs)),
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRoles()
        );
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || !PASSWORD_POLICY.matcher(password).matches()) {
            throw new BankingException(
                "Le mot de passe doit contenir au moins 8 caractères, " +
                "une majuscule, un chiffre et un caractère spécial.",
                "WEAK_PASSWORD", HttpStatus.BAD_REQUEST
            );
        }
    }

    private BankingException authFailedException(String message) {
        return new BankingException(message, "AUTHENTICATION_FAILED", HttpStatus.UNAUTHORIZED);
    }

    private void auditSuccess(String action, User user,
                               String ipAddress, String detail) {
        auditLogRepository.save(
            AuditLog.success(action, "User", user.getId().toString(), user.getId(), detail)
                    .withIpAddress(ipAddress)
        );
    }

    private void auditFailure(String action, User user,
                               String ipAddress, String detail) {
        auditLogRepository.save(
            AuditLog.failure(action, "User", user.getId().toString(), user.getId(), detail)
                    .withIpAddress(ipAddress)
        );
    }
}