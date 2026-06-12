package com.bank.api.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.JwtResponse;
import com.bank.common.dto.LoginRequest;
import com.bank.security.JwtAuthFilter.BankingUserPrincipal;
import com.bank.service.api.AuthService;
import com.bank.service.api.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Login, logout, refresh token, reset mot de passe")
public class AuthController {

	private final AuthService authService;
	private final NotificationService notificationService;
	
    @PostMapping("/login")
    @Operation(summary = "Connexion par email et mot de passe",
               description = "Retourne un access token (15 min) et un refresh token (7 jours). " +
                             "Un code OTP est requis si le 2FA est activé.")
    public ResponseEntity<ApiResponse<JwtResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
 
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
 
        JwtResponse response = authService.login(request, ipAddress, userAgent);
 
        log.info("[AUTH] Connexion — email={} ip={} newDevice={}",
                 request.email(), ipAddress,
                 response.mfaSetupRequired() || response.passwordChangeRequired());
 
        return ResponseEntity.ok(ApiResponse.ok("Connexion réussie", response));
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir l'access token",
	    description = "Échange un refresh token valide contre un nouvel access token. " +
	                  "Applique la rotation du refresh token (sliding window).")
	public ResponseEntity<ApiResponse<JwtResponse>> refresh(
	 @Valid @RequestBody RefreshRequest request) {
	
		JwtResponse response = authService.refreshToken(
		 request.refreshToken(), request.deviceId());
		
		log.debug("[AUTH] Token rafraîchi — deviceId={}", request.deviceId());
		
		return ResponseEntity.ok(ApiResponse.ok("Token rafraîchi", response));
    }
    @PostMapping("/logout")
    @Operation(summary = "Déconnexion du device courant",
    description = "Blackliste l'access token courant et révoque le refresh token " +
                  "du device. Les autres sessions restent actives.")
	public ResponseEntity<ApiResponse<Void>> logout(
	 @Valid @RequestBody LogoutRequest request,
	 @AuthenticationPrincipal BankingUserPrincipal principal,
	 HttpServletRequest httpRequest) {
	
		String accessToken = extractBearerToken(httpRequest);
		
		authService.logout(principal.userId(), accessToken, request.deviceId());
		
		log.info("[AUTH] Déconnexion — userId={} deviceId={}",
		      principal.userId(), request.deviceId());
	
		return ResponseEntity.ok(ApiResponse.ok("Déconnexion réussie"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Déconnexion de tous les appareils",
               description = "Invalide toutes les sessions actives de l'utilisateur. " +
                             "Utile en cas de suspicion de compromission du compte.")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal BankingUserPrincipal principal,
            HttpServletRequest httpRequest) {
 
        String accessToken = extractBearerToken(httpRequest);
 
        authService.logoutAllDevices(principal.userId(), accessToken);
 
        log.info("[AUTH] Déconnexion globale — userId={}", principal.userId());
 
        return ResponseEntity.ok(
            ApiResponse.ok("Déconnexion de tous les appareils réussie"));
    }
    @PostMapping("/forgot-password")
    @Operation(summary = "Initier la réinitialisation du mot de passe",
               description = "Envoie un email avec un lien de réinitialisation valable 15 min. " +
                             "Ne révèle pas si l'email existe en base (protection anti-énumération).")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
 
        authService.initiatePasswordReset(request.email());
 
        // Réponse identique que l'email existe ou non
        return ResponseEntity.ok(ApiResponse.ok(
            "Si cette adresse email est enregistrée, " +
            "vous recevrez un lien de réinitialisation sous peu."));
    }
    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe avec le token reçu par email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
 
        // Le token de réinitialisation encode l'userId dans son format
        // En pratique, le lien email contient aussi l'userId :
        // /reset-password?userId=xxx&token=yyy
        authService.resetPassword(request.userId(), request.token(),
                                   request.newPassword());
 
        log.info("[AUTH] Mot de passe réinitialisé — userId={}", request.userId());
 
        return ResponseEntity.ok(ApiResponse.ok(
            "Mot de passe réinitialisé avec succès. " +
            "Toutes vos sessions ont été déconnectées."));
    }
    @PostMapping("/change-password")
    @Operation(summary = "Changer le mot de passe (utilisateur authentifié)",
               description = "Requiert l'ancien mot de passe. " +
                             "Invalide toutes les autres sessions après le changement.")
    public ResponseEntity<ApiResponse<JwtResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        authService.changePassword(
            principal.userId(),
            request.currentPassword(),
            request.newPassword(),
            request.deviceId()
        );
 
        log.info("[AUTH] Mot de passe changé — userId={}", principal.userId());
 
        return ResponseEntity.ok(ApiResponse.ok(
            "Mot de passe changé avec succès. " +
            "Reconnectez-vous avec votre nouveau mot de passe."));
    }
    @PostMapping("/verify-email")
    @Operation(summary = "Vérifier l'adresse email via le token reçu par email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
 
        authService.verifyEmail(request.userId(), request.token());
 
        log.info("[AUTH] Email vérifié — userId={}", request.userId());
 
        return ResponseEntity.ok(ApiResponse.ok("Adresse email vérifiée avec succès."));
    }
    @PostMapping("/send-otp")
    @Operation(summary = "Envoyer un code OTP pour la double authentification",
               description = "Génère et envoie un OTP par SMS. " +
                             "Valable 10 minutes, 5 tentatives maximum.")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @AuthenticationPrincipal BankingUserPrincipal principal) {
 
        // Récupérer le numéro depuis le service utilisateur (non implémenté ici)
        // En pratique, le numéro est chargé depuis le profil utilisateur
        String otp = generateOtp();
 
        notificationService.sendOtpSms(
            principal.userId(),
            null, // numéro résolu dans NotificationServiceImpl
            otp
        );
 
        // Stocker le code dans Redis via le SessionCacheService
        // (appelé dans NotificationServiceImpl ou directement ici)
 
        log.debug("[AUTH] OTP envoyé — userId={}", principal.userId());
 
        return ResponseEntity.ok(ApiResponse.ok(
            "Code OTP envoyé par SMS. Valable 10 minutes."));
    }
 
    // ─────────────────────────────────────────────────────────
    //  GET /api/v1/auth/validate — validation de token (usage interne)
    // ─────────────────────────────────────────────────────────
 
    @GetMapping("/validate")
    @Operation(summary = "Vérifier la validité d'un access token",
               description = "Usage interne (API Gateway, micro-services). " +
                             "Retourne 200 si le token est valide, 401 sinon.")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            HttpServletRequest httpRequest) {
 
        String token = extractBearerToken(httpRequest);
        if (token == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.unauthorized("Token absent"));
        }
 
        boolean valid = authService.validateToken(token);
        if (!valid) {
            return ResponseEntity.status(401)
                .body(ApiResponse.unauthorized("Token invalide ou expiré"));
        }
 
        UUID userId = authService.extractUserId(token);
 
        return ResponseEntity.ok(ApiResponse.ok("Token valide",
            new TokenValidationResponse(true, userId)));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
    
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
 
    private String generateOtp() {
        return String.format("%06d", (int) (Math.random() * 1_000_000));
    }
    public record RefreshRequest(
    		 
            @NotBlank(message = "Le refresh token est obligatoire")
            String refreshToken,
     
            @NotBlank(message = "L'identifiant du device est obligatoire")
            String deviceId
        ) {}
    public record LogoutRequest(
    		 
            @NotBlank(message = "L'identifiant du device est obligatoire")
            String deviceId
        ) {}
     
    public record ForgotPasswordRequest(
 
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format email invalide")
        @Size(max = 150)
        String email
    ) {}
 
    public record ResetPasswordRequest(
 
        @NotNull
        UUID userId,
 
        @NotBlank(message = "Le token de réinitialisation est obligatoire")
        String token,
 
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 128,
              message = "Le mot de passe doit contenir entre 8 et 128 caractères")
        String newPassword
    ) {}
 
    public record ChangePasswordRequest(
 
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,
 
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 128)
        String newPassword,
 
        @NotBlank(message = "L'identifiant du device est obligatoire")
        String deviceId
    ) {}
 
    public record VerifyEmailRequest(
 
        @NotNull
        UUID userId,
 
        @NotBlank(message = "Le token de vérification est obligatoire")
        String token
    ) {}

    public record TokenValidationResponse(
            boolean valid,
            UUID    userId
        ) {}

}
