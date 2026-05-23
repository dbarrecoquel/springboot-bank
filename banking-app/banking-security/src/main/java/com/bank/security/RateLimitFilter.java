package com.bank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre de limitation de débit (rate limiting) basé sur Bucket4j.
 *
 * <p>Stratégie Token Bucket : chaque client dispose d'un "seau" de tokens
 * rechargé à intervalles réguliers. Chaque requête consomme un token.
 * Quand le seau est vide, la requête est rejetée avec un 429.</p>
 *
 * <p>Deux niveaux de limitation :</p>
 * <ul>
 *   <li><strong>Global</strong> — toutes les requêtes : 200 req/min par IP.</li>
 *   <li><strong>Auth</strong> — endpoints {@code /api/v1/auth/**} :
 *       20 req/min par IP (protection force brute).</li>
 * </ul>
 *
 * <p>Les buckets sont stockés en mémoire locale ({@link ConcurrentHashMap}).
 * Pour un déploiement multi-instances, remplacer par Bucket4j Redis
 * ({@code bucket4j-redis}) afin de partager les compteurs entre pods.</p>
 *
 * <p>Headers retournés :</p>
 * <pre>
 *   X-RateLimit-Remaining : tokens restants dans la fenêtre courante
 *   X-RateLimit-Retry-After : secondes avant rechargement (si 429)
 * </pre>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // ── Limites configurables ────────────────────────────────

    @Value("${banking.rate-limit.global.capacity:200}")
    private int globalCapacity;

    @Value("${banking.rate-limit.global.refill-per-minute:200}")
    private int globalRefillPerMinute;

    @Value("${banking.rate-limit.auth.capacity:20}")
    private int authCapacity;

    @Value("${banking.rate-limit.auth.refill-per-minute:20}")
    private int authRefillPerMinute;

    @Value("${banking.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    // ── Buckets en mémoire — un par IP par type ──────────────

    private final ConcurrentHashMap<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> authBuckets   = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────
    //  Filtre principal
    // ─────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String path     = request.getRequestURI();
        boolean isAuth  = path.startsWith("/api/v1/auth/");

        // Choisir le bucket selon le type d'endpoint
        Bucket bucket = isAuth
            ? authBuckets.computeIfAbsent(clientIp, ip -> buildAuthBucket())
            : globalBuckets.computeIfAbsent(clientIp, ip -> buildGlobalBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Ajouter les headers de rate limit dans tous les cas
        response.setHeader("X-RateLimit-Remaining",
                           String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader("X-RateLimit-Retry-After", String.valueOf(retryAfterSeconds));

            log.warn("[RATE-LIMIT] Limite dépassée — ip={} path={} retryAfter={}s",
                     clientIp, path, retryAfterSeconds);

            writeRateLimitResponse(response, retryAfterSeconds);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Exclusions
    // ─────────────────────────────────────────────────────────

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
            || path.startsWith("/actuator/info")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs");
    }

    // ─────────────────────────────────────────────────────────
    //  Construction des buckets
    // ─────────────────────────────────────────────────────────

    /**
     * Bucket global — 200 requêtes rechargées par minute.
     */
    private Bucket buildGlobalBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(globalCapacity)
            .refillGreedy(globalRefillPerMinute, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    /**
     * Bucket auth — 20 requêtes rechargées par minute (anti force brute).
     */
    private Bucket buildAuthBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(authCapacity)
            .refillGreedy(authRefillPerMinute, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    // ─────────────────────────────────────────────────────────
    //  Réponse 429
    // ─────────────────────────────────────────────────────────

    private void writeRateLimitResponse(HttpServletResponse response,
                                         long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
            "success",    false,
            "status",     429,
            "errorCode",  "TOO_MANY_REQUESTS",
            "message",    "Trop de requêtes. Réessayez dans " + retryAfterSeconds + " seconde(s).",
            "retryAfter", retryAfterSeconds,
            "timestamp",  LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // ─────────────────────────────────────────────────────────
    //  Extraction IP client
    // ─────────────────────────────────────────────────────────

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}