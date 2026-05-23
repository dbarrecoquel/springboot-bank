package com.bank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Handler 403 — réponse JSON quand un utilisateur authentifié
 * tente d'accéder à une ressource pour laquelle il n'a pas les droits.
 *
 * <p>Remplace la page HTML 403 par défaut de Spring Security.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("[SECURITY] Accès refusé — path={} ip={}",
                 request.getRequestURI(), request.getRemoteAddr());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
            "success",   false,
            "status",    403,
            "errorCode", "ACCESS_DENIED",
            "message",   "Accès refusé. Vous ne disposez pas des droits nécessaires.",
            "path",      request.getRequestURI(),
            "timestamp", LocalDateTime.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}