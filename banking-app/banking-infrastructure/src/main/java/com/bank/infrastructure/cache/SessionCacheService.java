package com.bank.infrastructure.cache;

import com.bank.domain.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
 
    @Value("${banking.jwt.expiration:900000}")
    private long jwtExpirationMs;
 
    @Value("${banking.jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;
 
    private static final int OTP_MAX_ATTEMPTS  = 5;
    private static final int OTP_TTL_MINUTES   = 10;
    private static final int OTP_COOLDOWN_MIN  = 1;   // délai entre deux envois OTP
    
    /**
     * Ajoute un JWT à la blacklist.
     * Le token sera considéré invalide dès ce moment, même s'il n'a pas expiré.
     *
     * @param jti           JWT ID (claim {@code jti})
     * @param remainingTtl  durée restante de validité du token
     */
    public void blacklistToken(String jti, Duration remainingTtl) {
        String key = RedisConfig.PREFIX_TOKEN_BLACKLIST + jti;
        stringRedisTemplate.opsForValue().set(key, "1", remainingTtl);
        log.debug("[SESSION] Token blacklisté — jti={} ttl={}s", jti, remainingTtl.getSeconds());
    }
    /**
     * Vérifie si un JWT est blacklisté.
     *
     * @param jti JWT ID
     * @return {@code true} si le token est invalide
     */
    public boolean isTokenBlacklisted(String jti) {
        String key = RedisConfig.PREFIX_TOKEN_BLACKLIST + jti;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
    
    /**
     * Blackliste tous les refresh tokens d'un utilisateur.
     * Appelé lors d'un changement de mot de passe ou d'un blocage de compte.
     *
     * @param userId identifiant de l'utilisateur
     */
    public void invalidateAllUserSessions(UUID userId) {
        String pattern = "jwt:refresh:" + userId + ":*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("[SESSION] Sessions utilisateur invalidées — userId={} count={}",
                     userId, keys.size());
        }
    }
    
    /**
     * Stocke un refresh token avec ses métadonnées.
     *
     * @param userId    propriétaire
     * @param deviceId  identifiant du device (permet la révocation par device)
     * @param token     valeur opaque du refresh token
     * @param roles     rôles au moment de la création (pour validation rapide)
     * @param rememberMe prolonge le TTL si {@code true}
     */
    public void storeRefreshToken(UUID userId, String deviceId,
                                   String token, Set<UserRole> roles,
                                   boolean rememberMe) {
        String key = buildRefreshKey(userId, deviceId);
        long ttlMs = rememberMe ? refreshExpirationMs * 4 : refreshExpirationMs;
 
        RefreshTokenData data = new RefreshTokenData(
            token, userId, deviceId, roles,
            LocalDateTime.now(),
            LocalDateTime.now().plus(Duration.ofMillis(ttlMs))
        );
 
        redisTemplate.opsForValue().set(key, data, Duration.ofMillis(ttlMs));
        log.debug("[SESSION] Refresh token stocké — userId={} deviceId={} ttlDays={}",
                  userId, deviceId, Duration.ofMillis(ttlMs).toDays());
    }
 
    /**
     * Récupère et valide un refresh token.
     *
     * @return les données du token, ou {@code null} si inexistant/expiré
     */
    public RefreshTokenData getRefreshToken(UUID userId, String deviceId) {
        String key = buildRefreshKey(userId, deviceId);
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw instanceof RefreshTokenData data) {
            return data;
        }
        return null;
    }
 
    /**
     * Révoque le refresh token d'un device spécifique.
     */
    public void revokeRefreshToken(UUID userId, String deviceId) {
        String key = buildRefreshKey(userId, deviceId);
        stringRedisTemplate.delete(key);
        log.info("[SESSION] Refresh token révoqué — userId={} deviceId={}", userId, deviceId);
    }
 
    /**
     * Rotation du refresh token — remplace l'ancien par le nouveau.
     * Appelé à chaque rafraîchissement (sliding window).
     */
    public void rotateRefreshToken(UUID userId, String deviceId,
                                    String newToken, Set<UserRole> roles) {
        revokeRefreshToken(userId, deviceId);
        storeRefreshToken(userId, deviceId, newToken, roles, false);
        log.debug("[SESSION] Refresh token roté — userId={} deviceId={}", userId, deviceId);
    }
 
    // ─────────────────────────────────────────────────────────
    //  OTP — codes à usage unique
    // ─────────────────────────────────────────────────────────
 
    /**
     * Stocke un code OTP pour un utilisateur et un usage donné.
     *
     * @param userId   identifiant de l'utilisateur
     * @param purpose  usage : "2FA", "EMAIL_VERIFY", "PHONE_VERIFY", "RESET_PASSWORD"
     * @param code     code à 6 chiffres
     */
    public void storeOtp(UUID userId, String purpose, String code) {
        String key = buildOtpKey(userId, purpose);
 
        // Vérifier le cooldown (évite le spam d'envoi OTP)
        String cooldownKey = key + ":cooldown";
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            log.warn("[SESSION] OTP cooldown actif — userId={} purpose={}", userId, purpose);
            throw new OtpCooldownException(
                "Veuillez attendre " + OTP_COOLDOWN_MIN + " minute(s) avant de renvoyer un code.");
        }
 
        // Stocker le code et initialiser le compteur de tentatives
        stringRedisTemplate.opsForValue().set(
            key, code + ":0", Duration.ofMinutes(OTP_TTL_MINUTES));
        stringRedisTemplate.opsForValue().set(
            cooldownKey, "1", Duration.ofMinutes(OTP_COOLDOWN_MIN));
 
        log.debug("[SESSION] OTP stocké — userId={} purpose={} ttl={}min",
                  userId, purpose, OTP_TTL_MINUTES);
    }
 
    /**
     * Vérifie un code OTP.
     *
     * @param userId  identifiant de l'utilisateur
     * @param purpose usage du code
     * @param code    code soumis par l'utilisateur
     * @return {@code true} si le code est correct
     * @throws OtpExpiredException  si le code a expiré
     * @throws OtpLockedException   si le nombre max de tentatives est atteint
     */
    public boolean verifyOtp(UUID userId, String purpose, String code) {
        String key = buildOtpKey(userId, purpose);
        String stored = stringRedisTemplate.opsForValue().get(key);
 
        if (stored == null) {
            throw new OtpExpiredException("Code OTP expiré ou inexistant — userId=" + userId);
        }
 
        String[] parts    = stored.split(":", 2);
        String storedCode = parts[0];
        int    attempts   = Integer.parseInt(parts[1]);
 
        if (attempts >= OTP_MAX_ATTEMPTS) {
            stringRedisTemplate.delete(key);
            log.warn("[SESSION] OTP verrouillé (max tentatives) — userId={} purpose={}",
                     userId, purpose);
            throw new OtpLockedException("Trop de tentatives — code invalidé.");
        }
 
        if (!storedCode.equals(code)) {
            // Incrémenter le compteur
            stringRedisTemplate.opsForValue().set(
                key, storedCode + ":" + (attempts + 1),
                Duration.ofMinutes(OTP_TTL_MINUTES));
            log.warn("[SESSION] OTP incorrect — userId={} purpose={} attempts={}/{}",
                     userId, purpose, attempts + 1, OTP_MAX_ATTEMPTS);
            return false;
        }
 
        // Code correct — supprimer après usage (one-time)
        stringRedisTemplate.delete(key);
        log.info("[SESSION] OTP validé — userId={} purpose={}", userId, purpose);
        return true;
    }
 
    /**
     * Invalide un OTP (ex : après changement d'email avant confirmation).
     */
    public void invalidateOtp(UUID userId, String purpose) {
        stringRedisTemplate.delete(buildOtpKey(userId, purpose));
    }
 
    // ─────────────────────────────────────────────────────────
    //  Velocity check — compteurs glissants anti-fraude
    // ─────────────────────────────────────────────────────────
 
    /**
     * Incrémente le compteur de transactions pour un compte sur une fenêtre temporelle.
     * Utilise INCR + EXPIRE atomique via pipeline Redis.
     *
     * @param accountId   identifiant du compte
     * @param windowKey   clé de fenêtre temporelle (ex : "2024012714" pour heure 14)
     * @param windowTtlSeconds durée de vie de la fenêtre
     * @return nombre de transactions sur cette fenêtre après incrémentation
     */
    public long incrementVelocityCounter(UUID accountId, String windowKey,
                                          long windowTtlSeconds) {
        String key = RedisConfig.PREFIX_VELOCITY + accountId + ":" + windowKey;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        // Définir le TTL seulement à la première incrémentation
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, windowTtlSeconds, TimeUnit.SECONDS);
        }
        return count != null ? count : 1L;
    }
 
    /**
     * Lit le compteur de velocity sans l'incrémenter.
     */
    public long getVelocityCounter(UUID accountId, String windowKey) {
        String key = RedisConfig.PREFIX_VELOCITY + accountId + ":" + windowKey;
        String val = stringRedisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }
 
    /**
     * Réinitialise le compteur de velocity (ex : après levée d'une alerte fraude).
     */
    public void resetVelocityCounter(UUID accountId, String windowKey) {
        stringRedisTemplate.delete(RedisConfig.PREFIX_VELOCITY + accountId + ":" + windowKey);
    }
 
    // ─────────────────────────────────────────────────────────
    //  Helpers privés
    // ─────────────────────────────────────────────────────────
 
    private String buildRefreshKey(UUID userId, String deviceId) {
        return "jwt:refresh:" + userId + ":" + deviceId;
    }
 
    private String buildOtpKey(UUID userId, String purpose) {
        return RedisConfig.PREFIX_OTP + userId + ":" + purpose;
    }
 
    // ─────────────────────────────────────────────────────────
    //  Record interne — données du refresh token
    // ─────────────────────────────────────────────────────────
 
    public record RefreshTokenData(
        String          token,
        UUID            userId,
        String          deviceId,
        Set<UserRole>   roles,
        LocalDateTime   issuedAt,
        LocalDateTime   expiresAt
    ) {
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
 
    // ─────────────────────────────────────────────────────────
    //  Exceptions métier OTP
    // ─────────────────────────────────────────────────────────
 
    public static class OtpExpiredException extends RuntimeException {
        public OtpExpiredException(String msg) { super(msg); }
    }
 
    public static class OtpLockedException extends RuntimeException {
        public OtpLockedException(String msg) { super(msg); }
    }
 
    public static class OtpCooldownException extends RuntimeException {
        public OtpCooldownException(String msg) { super(msg); }
    }


}
