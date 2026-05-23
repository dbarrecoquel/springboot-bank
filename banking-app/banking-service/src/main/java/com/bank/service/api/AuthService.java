package com.bank.service.api;

import com.bank.common.dto.JwtResponse;
import com.bank.common.dto.LoginRequest;

import java.util.UUID;

/**
 * Interface du service d'authentification.
 *
 * <p>Responsabilités :</p>
 * <ul>
 *   <li>Authentification par email/mot de passe avec support 2FA (OTP).</li>
 *   <li>Génération, rotation et révocation des tokens JWT.</li>
 *   <li>Gestion du cycle de vie des sessions (refresh, logout, logout all).</li>
 *   <li>Flux de vérification email et réinitialisation de mot de passe.</li>
 * </ul>
 *
 * <p>Sécurité :</p>
 * <ul>
 *   <li>Verrouillage du compte après 5 tentatives échouées (30 min).</li>
 *   <li>Détection de connexion depuis un nouvel appareil.</li>
 *   <li>Invalidation de toutes les sessions lors d'un changement de mot de passe.</li>
 * </ul>
 */
public interface AuthService {

    // ─────────────────────────────────────────────────────────
    //  Authentification
    // ─────────────────────────────────────────────────────────

    /**
     * Authentifie un utilisateur et retourne les tokens JWT.
     *
     * <p>Séquence :</p>
     * <ol>
     *   <li>Vérification email + mot de passe BCrypt.</li>
     *   <li>Contrôle du statut (enabled, non verrouillé).</li>
     *   <li>Vérification OTP si 2FA activé.</li>
     *   <li>Détection nouvel appareil → notification email.</li>
     *   <li>Génération access token + refresh token.</li>
     * </ol>
     *
     * @param request  credentials de connexion
     * @param ipAddress adresse IP de l'appelant (pour l'audit et le rate limiting)
     * @param userAgent user-agent du client
     * @return tokens JWT et informations utilisateur
     * @throws com.bank.common.exception.BankingException si les credentials sont invalides
     *         ou le compte verrouillé
     */
    JwtResponse login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * Rafraîchit un access token expiré à partir d'un refresh token valide.
     * Applique la rotation du refresh token (sliding window).
     *
     * @param refreshToken refresh token opaque
     * @param deviceId     identifiant du device (doit correspondre au token stocké)
     * @return nouveaux tokens JWT
     * @throws com.bank.common.exception.BankingException si le refresh token est invalide
     *         ou expiré
     */
    JwtResponse refreshToken(String refreshToken, String deviceId);

    /**
     * Déconnecte l'utilisateur du device courant.
     * Blackliste l'access token et révoque le refresh token du device.
     *
     * @param userId       identifiant de l'utilisateur
     * @param accessToken  token JWT courant (sera blacklisté)
     * @param deviceId     device à déconnecter
     */
    void logout(UUID userId, String accessToken, String deviceId);

    /**
     * Déconnecte l'utilisateur de tous ses appareils.
     * Invalide tous les refresh tokens et blackliste le token courant.
     *
     * @param userId      identifiant de l'utilisateur
     * @param accessToken token JWT courant
     */
    void logoutAllDevices(UUID userId, String accessToken);

    // ─────────────────────────────────────────────────────────
    //  Vérification email
    // ─────────────────────────────────────────────────────────

    /**
     * Envoie un email de vérification à l'adresse de l'utilisateur.
     *
     * @param userId identifiant de l'utilisateur
     */
    void sendEmailVerification(UUID userId);

    /**
     * Confirme la vérification de l'adresse email via le token reçu par email.
     *
     * @param token token de vérification (UUID, validité 24h)
     * @throws com.bank.common.exception.BankingException si le token est invalide ou expiré
     */
    void verifyEmail(String token);

    // ─────────────────────────────────────────────────────────
    //  Réinitialisation du mot de passe
    // ─────────────────────────────────────────────────────────

    /**
     * Initie le flux de réinitialisation de mot de passe.
     * Envoie un email avec un lien de réinitialisation (validité 15 min).
     * Ne révèle pas si l'email existe en base (protection contre l'énumération).
     *
     * @param email adresse email de l'utilisateur
     */
    void initiatePasswordReset(String email);

    /**
     * Réinitialise le mot de passe avec le token de réinitialisation.
     * Invalide toutes les sessions existantes après le changement.
     *
     * @param token       token de réinitialisation reçu par email
     * @param newPassword nouveau mot de passe en clair (sera hashé)
     * @throws com.bank.common.exception.BankingException si le token est invalide,
     *         expiré ou le mot de passe ne respecte pas la politique de sécurité
     */
    void resetPassword(String token, String newPassword);

    /**
     * Change le mot de passe d'un utilisateur authentifié.
     * Vérifie l'ancien mot de passe avant d'appliquer le nouveau.
     * Invalide toutes les sessions sauf la courante.
     *
     * @param userId          identifiant de l'utilisateur
     * @param currentPassword mot de passe actuel
     * @param newPassword     nouveau mot de passe
     * @param currentDeviceId device courant (session conservée)
     * @throws com.bank.common.exception.BankingException si l'ancien mot de passe
     *         est incorrect ou le nouveau ne respecte pas la politique
     */
    void changePassword(UUID userId, String currentPassword,
                         String newPassword, String currentDeviceId);

    // ─────────────────────────────────────────────────────────
    //  Validation de token
    // ─────────────────────────────────────────────────────────

    /**
     * Vérifie si un access token JWT est valide (signature, expiration, blacklist).
     *
     * @param token access token à valider
     * @return {@code true} si le token est valide et non blacklisté
     */
    boolean validateToken(String token);

    /**
     * Extrait l'identifiant utilisateur d'un access token JWT valide.
     *
     * @param token access token
     * @return identifiant de l'utilisateur
     * @throws com.bank.common.exception.BankingException si le token est invalide
     */
    UUID extractUserId(String token);
}