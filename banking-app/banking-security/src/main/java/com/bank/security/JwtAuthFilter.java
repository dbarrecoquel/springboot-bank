package com.bank.security;

import com.bank.domain.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filtre d'authentification JWT — s'exécute une seule fois par requête.
 *
 * <p>Séquence de traitement :</p>
 * <ol>
 *   <li>Extraire le token du header {@code Authorization: Bearer <token>}.</li>
 *   <li>Valider la signature et l'expiration via {@link JwtTokenProvider}.</li>
 *   <li>Vérifier que le token n'est pas blacklisté (logout, changement de mot de passe).</li>
 *   <li>Vérifier que le token est bien un access token (pas un refresh token).</li>
 *   <li>Construire l'objet {@link UsernamePasswordAuthenticationToken} et
 *       l'injecter dans le {@link SecurityContextHolder}.</li>
 * </ol>
 *
 * <p>En cas d'erreur d'authentification, le filtre répond directement
 * avec un JSON structuré {@code ApiError} plutôt que de propager l'exception —
 * cela évite la redirection vers une page de login HTML non pertinente pour une API REST.</p>
 *
 * <p>Les endpoints publics (login, register, actuator/health) sont exclus
 * du filtre via {@link SecurityConfig} — ce filtre ne vérifie pas lui-même
 * si l'endpoint nécessite une authentification.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider   jwtTokenProvider;
    private final ObjectMapper       objectMapper;

    private static final String BEARER_PREFIX = "Bearer ";

    // ─────────────────────────────────────────────────────────
    //  Filtre principal
    // ─────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Extraire le token du header Authorization
        String token = extractBearerToken(request);

        if (token == null) {
            // Pas de token — laisser Spring Security décider (endpoint public ou 401)
            filterChain.doFilter(request, response);
            return;
        }

        // Ne pas re-authentifier si le contexte est déjà renseigné
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticateRequest(token, request);
            filterChain.doFilter(request, response);

        } catch (JwtAuthException ex) {
            log.warn("[JWT] Authentification refusée — path={} reason={} ip={}",
                     path, ex.getErrorCode(), getClientIp(request));
            writeErrorResponse(response, ex);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Logique d'authentification
    // ─────────────────────────────────────────────────────────

    private void authenticateRequest(String token, HttpServletRequest request)
            throws JwtAuthException {

        // 1. Valider signature + expiration
        if (!jwtTokenProvider.isTokenValid(token)) {
            throw new JwtAuthException(
                "Token JWT invalide ou expiré.",
                "TOKEN_INVALID",
                HttpStatus.UNAUTHORIZED
            );
        }

        // 2. Vérifier que c'est bien un access token (pas un refresh token)
        if (!jwtTokenProvider.isAccessToken(token)) {
            throw new JwtAuthException(
                "Un refresh token ne peut pas être utilisé comme access token.",
                "WRONG_TOKEN_TYPE",
                HttpStatus.UNAUTHORIZED
            );
        }

        // 3. Extraire les claims
        UUID         userId = jwtTokenProvider.extractUserId(token);
        String       email  = jwtTokenProvider.extractEmail(token);
        Set<UserRole> roles  = jwtTokenProvider.extractRoles(token);

        if (userId == null || email == null) {
            throw new JwtAuthException(
                "Claims JWT manquants.",
                "TOKEN_CLAIMS_MISSING",
                HttpStatus.UNAUTHORIZED
            );
        }

        // 4. Construire les authorities Spring Security
        List<SimpleGrantedAuthority> authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
            .collect(Collectors.toList());

        // 5. Construire le principal — on utilise l'email comme username
        //    et on attache l'userId dans les détails pour y accéder dans les controllers
        BankingUserPrincipal principal = new BankingUserPrincipal(userId, email, roles);

        // 6. Créer l'Authentication et l'injecter dans le SecurityContext
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("[JWT] Authentification réussie — userId={} email={} roles={}",
                  userId, email, roles);
    }

    // ─────────────────────────────────────────────────────────
    //  Exclusions — endpoints qui ne nécessitent pas de token
    // ─────────────────────────────────────────────────────────

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/register")
            || path.startsWith("/api/v1/auth/refresh")
            || path.startsWith("/api/v1/auth/reset-password")
            || path.startsWith("/api/v1/auth/forgot-password")
            || path.startsWith("/api/v1/auth/verify-email")
            || path.startsWith("/actuator/health")
            || path.startsWith("/actuator/info")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/error");
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers — extraction du token
    // ─────────────────────────────────────────────────────────

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers — réponse d'erreur JSON
    // ─────────────────────────────────────────────────────────

    private void writeErrorResponse(HttpServletResponse response,
                                     JwtAuthException ex) throws IOException {
        response.setStatus(ex.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorBody = Map.of(
            "success",   false,
            "status",    ex.getHttpStatus().value(),
            "errorCode", ex.getErrorCode(),
            "message",   ex.getMessage(),
            "timestamp", LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ─────────────────────────────────────────────────────────
    //  Principal bancaire — attaché au SecurityContext
    // ─────────────────────────────────────────────────────────

    /**
     * Principal personnalisé exposé dans le {@link SecurityContextHolder}.
     *
     * <p>Accessible dans les controllers via :</p>
     * <pre>
     * {@code @AuthenticationPrincipal BankingUserPrincipal principal}
     * </pre>
     *
     * <p>Ou via SpEL dans {@code @PreAuthorize} :</p>
     * <pre>
     * {@code @PreAuthorize("#accountId == authentication.principal.userId")}
     * </pre>
     */
    public record BankingUserPrincipal(
        UUID          userId,
        String        email,
        Set<UserRole> roles
    ) {
        public boolean hasRole(UserRole role) {
            return roles.contains(role);
        }

        /** Nom d'affichage pour les logs Spring Security. */
        @Override
        public String toString() {
            return "BankingUserPrincipal{userId=" + userId + ", email=" + email + "}";
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Exception interne — erreur d'authentification JWT
    // ─────────────────────────────────────────────────────────

    /**
     * Exception non vérifiée levée lors d'un échec d'authentification JWT.
     * Interceptée dans {@link #doFilterInternal} pour produire une réponse JSON.
     */
    public static class JwtAuthException extends RuntimeException {

        private final String     errorCode;
        private final HttpStatus httpStatus;

        public JwtAuthException(String message, String errorCode, HttpStatus httpStatus) {
            super(message);
            this.errorCode  = errorCode;
            this.httpStatus = httpStatus;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }
    }
}