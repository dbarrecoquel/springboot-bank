package com.bank.security;

import com.bank.domain.entity.User;
import com.bank.domain.enums.UserRole;
import com.bank.infrastructure.cache.SessionCacheService;
import com.bank.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * Handler de succès OAuth2 — déclenché après une authentification OAuth2
 * réussie (Google, Apple, etc.).
 *
 * <p>Séquence :</p>
 * <ol>
 *   <li>Extraire l'email et le nom depuis les attributs OAuth2.</li>
 *   <li>Rechercher l'utilisateur en base par email.</li>
 *   <li>Si absent → créer un nouveau compte (provisioning automatique).</li>
 *   <li>Générer un access token + refresh token JWT.</li>
 *   <li>Stocker le refresh token en Redis.</li>
 *   <li>Rediriger le client vers le frontend avec les tokens en paramètres
 *       ou retourner un JSON selon le mode ({@code redirect} ou {@code json}).</li>
 * </ol>
 *
 * <p>Providers supportés :</p>
 * <ul>
 *   <li><strong>Google</strong> — attributs : {@code email}, {@code name},
 *       {@code given_name}, {@code family_name}, {@code picture}.</li>
 *   <li><strong>Apple</strong> — attributs : {@code email}, {@code sub} (user ID Apple).</li>
 * </ul>
 *
 * <p>Sécurité : les tokens ne sont jamais exposés dans l'URL en production
 * (risque de fuite dans les logs serveur et l'historique navigateur).
 * En production, le mode {@code json} avec un cookie HttpOnly est préférable.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider    jwtTokenProvider;
    private final SessionCacheService sessionCacheService;
    private final UserRepository      userRepository;
    private final ObjectMapper        objectMapper;

    @Value("${banking.oauth2.redirect-uri:http://localhost:3000/auth/callback}")
    private String redirectUri;

    @Value("${banking.oauth2.response-mode:redirect}")
    private String responseMode;   // "redirect" ou "json"

    @Value("${banking.jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    // ─────────────────────────────────────────────────────────
    //  Handler principal
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest  request,
                                         HttpServletResponse response,
                                         Authentication      authentication)
            throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        // 1. Extraire les attributs OAuth2
        OAuth2UserInfo userInfo = extractUserInfo(oauth2User, request);

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            log.error("[OAUTH2] Email absent dans les attributs OAuth2 — provider={}",
                      userInfo.provider());
            writeErrorResponse(response, "Email introuvable dans le profil OAuth2.");
            return;
        }

        try {
            // 2. Trouver ou créer l'utilisateur
            User user = findOrCreateUser(userInfo);

            // 3. Générer les tokens JWT
            String accessToken  = jwtTokenProvider.generateToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);

            // 4. Stocker le refresh token
            String deviceId = "oauth2-" + userInfo.provider() + "-"
                + UUID.nameUUIDFromBytes(
                    (userInfo.email() + request.getRemoteAddr()).getBytes());

            sessionCacheService.storeRefreshToken(
                user.getId(), deviceId, refreshToken, user.getRoles(), false);

            log.info("[OAUTH2] Authentification réussie — userId={} provider={} email={}",
                     user.getId(), userInfo.provider(), userInfo.email());

            // 5. Répondre selon le mode configuré
            if ("json".equalsIgnoreCase(responseMode)) {
                writeJsonResponse(response, user, accessToken, refreshToken);
            } else {
                redirectWithTokens(response, accessToken, refreshToken);
            }

        } catch (Exception ex) {
            log.error("[OAUTH2] Erreur traitement OAuth2 — email={} error={}",
                      userInfo.email(), ex.getMessage(), ex);
            writeErrorResponse(response, "Erreur lors de l'authentification OAuth2.");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Provisioning utilisateur
    // ─────────────────────────────────────────────────────────

    /**
     * Recherche l'utilisateur par email ou en crée un nouveau.
     * Les nouveaux comptes OAuth2 nécessitent une validation KYC ultérieure.
     */
    private User findOrCreateUser(OAuth2UserInfo info) {
        return userRepository.findByEmail(info.email())
            .orElseGet(() -> {
                log.info("[OAUTH2] Nouveau compte créé via OAuth2 — email={} provider={}",
                         info.email(), info.provider());
                return createOAuth2User(info);
            });
    }

    private User createOAuth2User(OAuth2UserInfo info) {
        // Mot de passe aléatoire non utilisable — l'utilisateur se connecte via OAuth2
        String unusablePassword = "$2a$12$" + UUID.randomUUID().toString().replace("-", "");

        User user = User.create(
            info.firstName() != null ? info.firstName() : extractFirstName(info.name()),
            info.lastName()  != null ? info.lastName()  : extractLastName(info.name()),
            LocalDate.of(1900, 1, 1),   // date de naissance à compléter lors du KYC
            info.email(),
            unusablePassword
        );

        user.setEmailVerified(true);    // email vérifié par le provider OAuth2
        user.setRoles(EnumSet.of(UserRole.CUSTOMER));

        User saved = userRepository.save(user);

        log.info("[OAUTH2] Utilisateur provisionné — userId={} email={}",
                 saved.getId(), saved.getEmail());

        return saved;
    }

    // ─────────────────────────────────────────────────────────
    //  Extraction des attributs OAuth2 selon le provider
    // ─────────────────────────────────────────────────────────

    private OAuth2UserInfo extractUserInfo(OAuth2User oauth2User,
                                            HttpServletRequest request) {
        // Déterminer le provider depuis l'URL de callback
        // ex : /login/oauth2/code/google → "google"
        String uri      = request.getRequestURI();
        String provider = "unknown";
        if (uri.contains("/google"))  provider = "google";
        else if (uri.contains("/apple")) provider = "apple";
        else if (uri.contains("/facebook")) provider = "facebook";

        Map<String, Object> attrs = oauth2User.getAttributes();

        return switch (provider) {
            case "google" -> new OAuth2UserInfo(
                provider,
                getString(attrs, "email"),
                getString(attrs, "given_name"),
                getString(attrs, "family_name"),
                getString(attrs, "name"),
                getString(attrs, "picture")
            );
            case "apple" -> new OAuth2UserInfo(
                provider,
                getString(attrs, "email"),
                getString(attrs, "firstName"),   // disponible seulement au premier login Apple
                getString(attrs, "lastName"),
                null,
                null
            );
            default -> new OAuth2UserInfo(
                provider,
                getString(attrs, "email"),
                null, null,
                getString(attrs, "name"),
                null
            );
        };
    }

    // ─────────────────────────────────────────────────────────
    //  Réponses HTTP
    // ─────────────────────────────────────────────────────────

    /**
     * Redirection vers le frontend avec les tokens en paramètres de l'URL.
     * À n'utiliser qu'en développement — risque de fuite en production.
     */
    private void redirectWithTokens(HttpServletResponse response,
                                     String accessToken,
                                     String refreshToken) throws IOException {
        String url = redirectUri
            + "?accessToken="  + accessToken
            + "&refreshToken=" + refreshToken;
        response.sendRedirect(url);
    }

    /**
     * Réponse JSON — recommandé en production.
     * Le refresh token doit ensuite être stocké dans un cookie HttpOnly côté client.
     */
    private void writeJsonResponse(HttpServletResponse response,
                                    User user,
                                    String accessToken,
                                    String refreshToken) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
            "success",      true,
            "accessToken",  accessToken,
            "refreshToken", refreshToken,
            "tokenType",    "Bearer",
            "userId",       user.getId().toString(),
            "email",        user.getEmail(),
            "fullName",     user.getFullName(),
            "timestamp",    LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeErrorResponse(HttpServletResponse response,
                                     String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
            "success",   false,
            "status",    401,
            "errorCode", "OAUTH2_ERROR",
            "message",   message,
            "timestamp", LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private String getString(Map<String, Object> attrs, String key) {
        Object val = attrs.get(key);
        return val != null ? val.toString() : null;
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Prénom";
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Nom";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "Nom";
    }

    // ─────────────────────────────────────────────────────────
    //  Record interne — attributs OAuth2 normalisés
    // ─────────────────────────────────────────────────────────

    private record OAuth2UserInfo(
        String provider,
        String email,
        String firstName,
        String lastName,
        String name,
        String pictureUrl
    ) {}
}