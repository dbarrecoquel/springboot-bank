package com.bank.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration Spring Security 6 pour l'application bancaire.
 *
 * <p>Principes appliqués :</p>
 * <ul>
 *   <li><strong>Stateless</strong> — pas de session HTTP côté serveur (JWT uniquement).</li>
 *   <li><strong>CSRF désactivé</strong> — inutile pour une API REST sans cookies de session.</li>
 *   <li><strong>CORS configuré</strong> — origines autorisées définies par profil.</li>
 *   <li><strong>Headers de sécurité</strong> — CSP, HSTS, X-Frame-Options, etc.</li>
 *   <li><strong>Method Security</strong> — {@code @PreAuthorize} activé sur tous les beans.</li>
 * </ul>
 *
 * <p>Matrice des accès :</p>
 * <pre>
 *   PUBLIC     : /api/v1/auth/**, /actuator/health, /actuator/info, /swagger-ui/**, /v3/api-docs/**
 *   CUSTOMER   : GET /api/v1/accounts/**, GET /api/v1/transactions/**, POST /api/v1/transfers/**
 *   TELLER     : tout CUSTOMER + GET /api/v1/users/**
 *   MANAGER    : tout TELLER + PUT /api/v1/accounts/**
 *   COMPLIANCE : GET /api/v1/audit/**, GET /api/v1/fraud/**
 *   ADMIN      : tout
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter         jwtAuthFilter;
    private final UserDetailsService    userDetailsService;
    private final JwtAuthEntryPoint     jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Value("${banking.cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private List<String> allowedOrigins;

    @Value("${banking.cors.allowed-origins-prod:}")
    private List<String> allowedOriginsProd;

    // ─────────────────────────────────────────────────────────
    //  Security Filter Chain
    // ─────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── Désactiver CSRF (API REST stateless) ────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS ────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Session stateless ───────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Gestion des erreurs d'authentification ───────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))

            // ── Headers de sécurité ─────────────────────────
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))   // HSTS 1 an
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(ct -> {})
                .referrerPolicy(ref ->
                    ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data:; " +
                        "frame-ancestors 'none';"
                    ))
            )

            // ── Autorisation par endpoint ────────────────────
            .authorizeHttpRequests(auth -> auth

                // ── Endpoints publics ────────────────────────
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/auth/verify-email"
                ).permitAll()

                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()

                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                .requestMatchers("/error").permitAll()

                // ── Actuator — admin uniquement ──────────────
                .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")

                // ── Comptes — lecture client ─────────────────
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/accounts/**")
                    .hasAnyRole("CUSTOMER", "TELLER", "MANAGER", "COMPLIANCE", "ADMIN")

                // ── Comptes — modification statut (blocage) ──
                .requestMatchers(HttpMethod.PUT,
                    "/api/v1/accounts/*/status",
                    "/api/v1/accounts/*/block",
                    "/api/v1/accounts/*/unblock")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── Comptes — clôture ────────────────────────
                .requestMatchers(HttpMethod.DELETE,
                    "/api/v1/accounts/*")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── Transactions — lecture ───────────────────
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/transactions/**")
                    .hasAnyRole("CUSTOMER", "TELLER", "MANAGER", "COMPLIANCE", "ADMIN")

                // ── Virements — initiation ───────────────────
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/transfers/**",
                    "/api/v1/transactions/**")
                    .hasAnyRole("CUSTOMER", "TELLER", "MANAGER", "ADMIN")

                // ── Transactions — validation compliance ──────
                .requestMatchers(HttpMethod.PUT,
                    "/api/v1/transactions/*/confirm",
                    "/api/v1/transactions/*/block")
                    .hasAnyRole("COMPLIANCE", "ADMIN")

                // ── Cartes — lecture ─────────────────────────
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/cards/**")
                    .hasAnyRole("CUSTOMER", "TELLER", "MANAGER", "ADMIN")

                // ── Cartes — gestion ─────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/cards/**")
                    .hasAnyRole("CUSTOMER", "MANAGER", "ADMIN")

                .requestMatchers(HttpMethod.PUT, "/api/v1/cards/**")
                    .hasAnyRole("CUSTOMER", "MANAGER", "ADMIN")

                // ── Utilisateurs — profil propre ─────────────
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/users/me",
                    "/api/v1/users/me/**")
                    .hasAnyRole("CUSTOMER", "TELLER", "MANAGER", "COMPLIANCE", "ADMIN")

                // ── Utilisateurs — liste et détail ───────────
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/users/**")
                    .hasAnyRole("TELLER", "MANAGER", "COMPLIANCE", "ADMIN")

                // ── Utilisateurs — modification ───────────────
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                .requestMatchers(HttpMethod.PATCH, "/api/v1/users/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── Audit et fraude — compliance ─────────────
                .requestMatchers(
                    "/api/v1/audit/**",
                    "/api/v1/fraud/**")
                    .hasAnyRole("COMPLIANCE", "ADMIN")

                // ── Administration ───────────────────────────
                .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                // ── Notifications ─────────────────────────────
                .requestMatchers("/api/v1/notifications/**")
                    .hasAnyRole("CUSTOMER", "TELLER", "MANAGER", "COMPLIANCE", "ADMIN")

                // ── Tout le reste — authentifié ───────────────
                .anyRequest().authenticated()
            )

            // ── JWT Filter avant UsernamePasswordAuthFilter ──
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─────────────────────────────────────────────────────────
    //  CORS
    // ─────────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Origines autorisées — dev + prod
        config.setAllowedOrigins(allowedOrigins);
        if (!allowedOriginsProd.isEmpty()) {
            config.setAllowedOrigins(allowedOriginsProd);
        }

        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "X-Device-Id",          // identifiant du device pour le refresh token
            "X-Forwarded-For"
        ));
        config.setExposedHeaders(List.of(
            "Authorization",
            "X-Total-Count"         // pour la pagination
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);    // cache preflight 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    // ─────────────────────────────────────────────────────────
    //  Authentication beans
    // ─────────────────────────────────────────────────────────

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt strength 12 — bon compromis sécurité/performance (≈250ms par hash)
        return new BCryptPasswordEncoder(12);
    }
}