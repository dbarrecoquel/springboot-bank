package com.bank.security;

import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fournisseur JWT — génération, validation et extraction des tokens.
 *
 * <p>Deux types de tokens sont gérés :</p>
 * <ul>
 *   <li><strong>Access token</strong> — courte durée (15 min par défaut),
 *       contient les claims utilisateur (userId, email, roles).
 *       Transmis dans le header {@code Authorization: Bearer <token>}.</li>
 *   <li><strong>Refresh token</strong> — longue durée (7 jours par défaut),
 *       contient uniquement {@code userId} et {@code type=REFRESH}.
 *       Stocké en Redis via {@code SessionCacheService}.</li>
 * </ul>
 *
 * <p>Algorithme : <strong>HS256</strong> (HMAC-SHA256) avec clé symétrique
 * d'au moins 256 bits. En production, préférer RS256 (clé asymétrique)
 * pour permettre la validation sans partager le secret.</p>
 *
 * <p>Claims standards utilisés :</p>
 * <pre>
 *   sub   : userId (UUID)
 *   jti   : identifiant unique du token (pour la blacklist)
 *   iat   : date d'émission
 *   exp   : date d'expiration
 *   email : adresse email
 *   roles : liste des rôles (claim custom)
 *   type  : "ACCESS" ou "REFRESH"
 * </pre>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    // ─────────────────────────────────────────────────────────
    //  Noms des claims custom
    // ─────────────────────────────────────────────────────────

    private static final String CLAIM_EMAIL   = "email";
    private static final String CLAIM_ROLES   = "roles";
    private static final String CLAIM_TYPE    = "type";

    private static final String TYPE_ACCESS   = "ACCESS";
    private static final String TYPE_REFRESH  = "REFRESH";

    // ─────────────────────────────────────────────────────────
    //  Configuration injectée
    // ─────────────────────────────────────────────────────────

    @Value("${banking.jwt.secret:mySecretKeyThatIsAtLeast256BitsLongForHS256AlgorithmSecureKey}")
    private String jwtSecret;

    @Value("${banking.jwt.expiration:900000}")
    private long accessTokenExpirationMs;

    @Value("${banking.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${banking.jwt.issuer:banking-app}")
    private String issuer;

    private SecretKey signingKey;

    // ─────────────────────────────────────────────────────────
    //  Initialisation
    // ─────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "La clé JWT doit faire au moins 256 bits (32 caractères). " +
                "Longueur actuelle : " + keyBytes.length + " octets."
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("[JWT] JwtTokenProvider initialisé — issuer={} accessTTL={}s refreshTTL={}s",
                 issuer,
                 accessTokenExpirationMs / 1000,
                 refreshTokenExpirationMs / 1000);
    }

    // ─────────────────────────────────────────────────────────
    //  Génération — Access Token
    // ─────────────────────────────────────────────────────────

    /**
     * Génère un access token JWT pour un utilisateur.
     *
     * @param user utilisateur authentifié
     * @return access token signé (HS256)
     */
    public String generateToken(User user) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpirationMs);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())           // jti — unique par token
            .issuer(issuer)
            .subject(user.getId().toString())           // sub = userId
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim(CLAIM_EMAIL, user.getEmail())
            .claim(CLAIM_ROLES, rolesToList(user.getRoles()))
            .claim(CLAIM_TYPE,  TYPE_ACCESS)
            .signWith(signingKey)
            .compact();
    }

    // ─────────────────────────────────────────────────────────
    //  Génération — Refresh Token
    // ─────────────────────────────────────────────────────────

    /**
     * Génère un refresh token JWT pour un utilisateur.
     * Contient uniquement le minimum nécessaire (userId + type).
     *
     * @param user utilisateur authentifié
     * @return refresh token signé (HS256)
     */
    public String generateRefreshToken(User user) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpirationMs);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(issuer)
            .subject(user.getId().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim(CLAIM_TYPE, TYPE_REFRESH)
            .signWith(signingKey)
            .compact();
    }

    // ─────────────────────────────────────────────────────────
    //  Validation
    // ─────────────────────────────────────────────────────────

    /**
     * Vérifie la signature et l'expiration d'un token JWT.
     *
     * @param token token à valider
     * @return {@code true} si le token est bien formé, signé et non expiré
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("[JWT] Token expiré — jti={}", extractJtiUnchecked(token));
            return false;
        } catch (SignatureException ex) {
            log.warn("[JWT] Signature invalide — token={}", truncate(token));
            return false;
        } catch (MalformedJwtException ex) {
            log.warn("[JWT] Token malformé — token={}", truncate(token));
            return false;
        } catch (UnsupportedJwtException ex) {
            log.warn("[JWT] Type de token non supporté");
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("[JWT] Token invalide — {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Vérifie qu'un token est un access token (pas un refresh token).
     * Protection contre l'utilisation d'un refresh token comme access token.
     */
    public boolean isAccessToken(String token) {
        try {
            String type = parseClaims(token).get(CLAIM_TYPE, String.class);
            return TYPE_ACCESS.equals(type);
        } catch (JwtException ex) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Extraction des claims
    // ─────────────────────────────────────────────────────────

    /**
     * Extrait l'identifiant utilisateur depuis un access token.
     *
     * @param token access token valide
     * @return UUID de l'utilisateur
     * @throws JwtException si le token est invalide
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * Extrait l'userId depuis un refresh token.
     * Méthode dédiée pour distinguer les deux usages dans le code appelant.
     */
    public UUID extractUserIdFromRefreshToken(String refreshToken) {
        Claims claims = parseClaims(refreshToken);
        validateTokenType(claims, TYPE_REFRESH);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extrait l'adresse email depuis un access token.
     */
    public String extractEmail(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    /**
     * Extrait les rôles depuis un access token.
     */
    @SuppressWarnings("unchecked")
    public Set<UserRole> extractRoles(String token) {
        List<String> roles = parseClaims(token).get(CLAIM_ROLES, List.class);
        if (roles == null) return Set.of();
        return roles.stream()
            .map(UserRole::valueOf)
            .collect(Collectors.toSet());
    }

    /**
     * Extrait le JWT ID (claim {@code jti}) — utilisé pour la blacklist.
     *
     * @param token token JWT valide
     * @return jti du token
     */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    /**
     * Extrait la date d'expiration d'un token.
     */
    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * Calcule la durée de validité restante d'un token.
     * Utile pour définir le TTL de la blacklist Redis.
     *
     * @param token access token
     * @return durée restante (minimun {@link Duration#ZERO} si expiré)
     */
    public Duration getRemainingValidity(String token) {
        try {
            Date expiry = extractExpiration(token);
            long remaining = expiry.getTime() - System.currentTimeMillis();
            return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
        } catch (ExpiredJwtException ex) {
            return Duration.ZERO;
        }
    }

    /**
     * Vérifie si un token est expiré (sans lever d'exception).
     */
    public boolean isExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        } catch (JwtException ex) {
            return true;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers privés
    // ─────────────────────────────────────────────────────────

    /**
     * Parse et vérifie le token — lève une {@link JwtException} si invalide.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private void validateTokenType(Claims claims, String expectedType) {
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException(
                "Type de token incorrect — attendu : " + expectedType +
                ", reçu : " + type
            );
        }
    }

    private List<String> rolesToList(Set<UserRole> roles) {
        return roles.stream()
            .map(UserRole::name)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Extrait le jti sans valider la signature — uniquement pour les logs.
     * Ne jamais utiliser à des fins de sécurité.
     */
    private String extractJtiUnchecked(String token) {
        try {
            String payload = token.split("\\.")[1];
            String decoded = new String(
                java.util.Base64.getUrlDecoder().decode(payload),
                StandardCharsets.UTF_8
            );
            // Extraction simple du jti par pattern matching
            int start = decoded.indexOf("\"jti\":\"") + 7;
            int end   = decoded.indexOf("\"", start);
            return start > 6 && end > start ? decoded.substring(start, end) : "unknown";
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private String truncate(String token) {
        if (token == null || token.length() < 20) return "****";
        return token.substring(0, 10) + "..." + token.substring(token.length() - 6);
    }
}