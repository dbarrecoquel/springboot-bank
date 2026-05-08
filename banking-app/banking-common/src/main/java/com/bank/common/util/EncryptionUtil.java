package com.bank.common.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.bank.common.exception.EncryptionException;

public final class EncryptionUtil {

	private static final String ALGORITHM = "AES";
	private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
	private static final int KEY_SIZE_BITS = 256;
	private static final int IV_LENGTH_BYTES = 12;
	private static final int GCM_TAG_LENGTH = 128;
	
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	
	private EncryptionUtil() {}
    /**
     * Chiffre une chaîne en clair avec AES-256-GCM.
     *
     * <p>Un IV aléatoire de 12 octets est généré à chaque appel et
     * préfixé au ciphertext. Deux appels avec la même entrée produisent
     * deux ciphertexts différents (sécurité IND-CPA).</p>
     *
     * @param plaintext texte à chiffrer (UTF-8)
     * @param key       clé AES-256
     * @return payload Base64 URL-safe : {@code IV || ciphertext+tag}
     * @throws EncryptionException si le chiffrement échoue
     */
	public static String encrypt(String plaintext, SecretKey key) {
		
		if (plaintext == null) return null;
		
		try {
			
			byte[] iv = generateIv();
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
		
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			
			ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH_BYTES + ciphertext.length);
			buf.put(iv);
			buf.put(ciphertext);
			
			return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
		}
		
		catch (Exception e) {
			throw new EncryptionException("Echec du chiffrement AES-256-GCM",e);
		}
		
	}
    /**
     * Déchiffre un payload produit par {@link #encrypt(String, SecretKey)}.
     *
     * <p>Le tag d'authentification GCM est vérifié automatiquement —
     * toute altération du ciphertext lève une {@code EncryptionException}.</p>
     *
     * @param encryptedBase64 payload Base64 URL-safe
     * @param key             clé AES-256 utilisée au chiffrement
     * @return texte en clair (UTF-8)
     * @throws EncryptionException si le déchiffrement ou la vérification d'intégrité échoue
     */
    public static String decrypt(String encryptedBase64, SecretKey key) {
        
    	if (encryptedBase64 == null) 
        	return null;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedBase64);
 
            if (decoded.length < IV_LENGTH_BYTES) {
                throw new EncryptionException("Payload chiffré invalide — trop court");
            }
 
            // Extraire l'IV et le ciphertext
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);
 
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
 
            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
 
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Échec du déchiffrement — données altérées ou clé incorrecte", e);
        }
    }
    /**
     * Génère une nouvelle clé AES-256 aléatoire.
     * À utiliser uniquement en phase d'initialisation — stocker la clé
     * dans un secret manager, jamais en clair dans le code ou la config Git.
     *
     * @return clé AES-256
     */
    public static SecretKey generateKey() {
    	
    	try {
    		KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
    		kg.init(KEY_SIZE_BITS, SECURE_RANDOM);
    		return kg.generateKey();
    	}
    	catch (NoSuchAlgorithmException e) {
    		throw new EncryptionException("Algorithm AES indisponible", e);
    		
    	}
    }

    /**
     * Encode une clé en Base64 URL-safe pour stockage.
     *
     * @param key clé AES-256
     * @return représentation Base64 de la clé
     */
    public static String encodeKey(SecretKey key) {
    	
    	return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
    }
    
    /**
     * Recrée un {@link SecretKey} depuis sa représentation Base64.
     * À appeler au démarrage de l'application pour injecter la clé
     * lue depuis Vault ou Secrets Manager.
     *
     * @param base64Key clé encodée en Base64 URL-safe
     * @return clé AES-256 utilisable
     * @throws EncryptionException si le format est invalide
     */
    public static SecretKey decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new EncryptionException("La clé de chiffrement ne peut pas être vide");
        }
        try {
            byte[] keyBytes = Base64.getUrlDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new EncryptionException(
                    "Longueur de clé invalide : " + keyBytes.length + " octets (attendu : 32 pour AES-256)");
            }
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("Format Base64 invalide pour la clé de chiffrement", e);
        }
    }
    /**
     * Masque un PAN (numéro de carte) pour affichage client ou logs.
     * Conserve les 6 premiers et les 4 derniers chiffres (règle PCI-DSS).
     * Ex : 4111111111111111 → 411111***111 → "**** **** **** 1111" (format affichage)
     *
     * @param pan numéro de carte brut (13 à 19 chiffres)
     * @return PAN masqué au format {@code **** **** **** XXXX}
     */
    public static String maskPan(String pan) {
        if (pan == null) 
        	return "****";
        
        String digits = pan.replaceAll("\\s", "");
       
        if (digits.length() < 8) 
        	return "****";
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }
    /**
     * Vérifie que le PAN masqué a le bon format {@code **** **** **** XXXX}.
     */
    public static boolean isValidMaskedPan(String maskedPan) {
        return maskedPan != null
            && maskedPan.matches("^\\*{4} \\*{4} \\*{4} \\d{4}$");
    }
    
    private static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

}
