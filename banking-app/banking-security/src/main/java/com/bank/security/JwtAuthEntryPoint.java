package com.bank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Point d'entrée 401 — réponse JSON quand un utilisateur non authentifié
 * tente d'accéder à une ressource protégée.
 *
 * <p>Remplace la redirection HTML vers /login par défaut de Spring Security.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("[SECURITY] Accès non authentifié — path={} ip={}",
                 request.getRequestURI(), request.getRemoteAddr());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
            "success",   false,
            "status",    401,
            "errorCode", "UNAUTHORIZED",
            "message",   "Authentification requise. Veuillez vous connecter.",
            "path",      request.getRequestURI(),
            "timestamp", LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}