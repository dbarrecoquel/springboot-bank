package com.bank.common.util;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

/**
 * Validateur d'IBAN (International Bank Account Number) — ISO 13616.
 *
 * <p>Algorithme officiel :</p>
 * <ol>
 *   <li>Déplacer les 4 premiers caractères en fin de chaîne.</li>
 *   <li>Remplacer chaque lettre par sa valeur numérique (A=10, B=11, … Z=35).</li>
 *   <li>Calculer le modulo 97 — un IBAN valide donne exactement 1.</li>
 * </ol>
 *
 * <p>En plus du checksum, ce validateur vérifie la longueur attendue
 * par pays (registre SWIFT, 75 pays supportés).</p>
 */

public final class IbanValidator {

	private IbanValidator() {
		
	}
	
	private static final Map<String, Integer> IBAN_LENGTHS = Map.ofEntries(
	        // Zone euro / SEPA
	        Map.entry("AD", 24), Map.entry("AT", 20), Map.entry("BE", 16),
	        Map.entry("BG", 22), Map.entry("CH", 21), Map.entry("CY", 28),
	        Map.entry("CZ", 24), Map.entry("DE", 22), Map.entry("DK", 18),
	        Map.entry("EE", 20), Map.entry("ES", 24), Map.entry("FI", 18),
	        Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GR", 27),
	        Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22),
	        Map.entry("IS", 26), Map.entry("IT", 27), Map.entry("LI", 21),
	        Map.entry("LT", 20), Map.entry("LU", 20), Map.entry("LV", 21),
	        Map.entry("MC", 27), Map.entry("MT", 31), Map.entry("NL", 18),
	        Map.entry("NO", 15), Map.entry("PL", 28), Map.entry("PT", 25),
	        Map.entry("RO", 24), Map.entry("SE", 24), Map.entry("SI", 19),
	        Map.entry("SK", 24), Map.entry("SM", 27),
	        // Hors SEPA fréquents
	        Map.entry("AE", 23), Map.entry("AL", 28), Map.entry("BA", 20),
	        Map.entry("BR", 29), Map.entry("GE", 22), Map.entry("IL", 23),
	        Map.entry("JO", 30), Map.entry("KW", 30), Map.entry("LB", 28),
	        Map.entry("MA", 28), Map.entry("MU", 30), Map.entry("QA", 29),
	        Map.entry("RS", 22), Map.entry("SA", 24), Map.entry("TN", 24),
	        Map.entry("TR", 26)
	    );
	
    /**
     * Valide un IBAN complet (format + checksum modulo 97).
     *
     * @param iban chaîne brute (espaces tolérés, insensible à la casse)
     * @return {@code true} si l'IBAN est valide
     */
	public static boolean isValid(String iban) {
		
		if (iban == null || iban.isBlank()) return false;
		
		String normalized = normalize(iban);
		
		if (!matchesPattern(normalized))
			return false;
		if (!hasCorrectLength(normalized))
			return false;
		return checksum(normalized);
	}
	/**
     * Valide et lève une exception si l'IBAN est invalide.
     *
     * @param iban IBAN à valider
     * @throws IllegalArgumentException si invalide
     */
	public static void validate(String iban) {
		
		if (!isValid(iban)) {
			String safe = iban != null ? iban.replace("\\s", "") : "null";
            throw new IllegalArgumentException(
                    "IBAN invalide : " + mask(safe)
                );

		}
			
	}
    /**
     * Extrait le code pays (2 premières lettres) d'un IBAN.
     *
     * @param iban IBAN validé
     * @return code pays ISO 3166-1 alpha-2 (ex : "FR")
     */
    public static String extractCountryCode(String iban) {
        if (iban == null || iban.length() < 2) {
            throw new IllegalArgumentException("IBAN trop court pour extraire le code pays");
        }
        return normalize(iban).substring(0, 2);
    }
    
    /**
     * Formate un IBAN normalisé en groupes de 4 caractères séparés par des espaces.
     * Ex : {@code "FR7614508059320004073780129"} → {@code "FR76 1450 8059 3200 0407 3780 129"}
     */

    public static String format(String iban) {
    	String normalized = normalize(iban);
    	StringBuilder sb = new StringBuilder();
    	
    	for (int i = 0; i < normalized.length(); i +=4)
    	{
    		if (sb.length() > 0) sb.append(' ');
    		sb.append(normalized,i, Math.min(i + 4, normalized.length()));
    	}
    	return sb.toString();
    }
    /**
     * Masque partiellement un IBAN pour les logs et les affichages client.
     * Ex : {@code "FR76****0129"}
     */
    public static String mask(String iban) {
    	
    	if (iban == null || iban.isBlank()) return "****";
    	
    	String n = normalize(iban);
    	
    	if (n.length() <= 8) return "****";
    	
    	return n.substring(0, 4) + "****" + n.substring(n.length() - 4);
    }
    
    /**
     * Retourne la longueur attendue pour le code pays donné.
     */
    public static Optional<Integer> expectedLength(String countryCode) {
    	
    	return Optional.ofNullable(IBAN_LENGTHS.get(countryCode != null ? countryCode.toUpperCase() : null));
    }
    /**
     * Indique si le pays est membre de la zone SEPA.
     */
    public static boolean isSepaCountry(String iban) {
        String cc = extractCountryCode(normalize(iban));
        return switch (cc) {
            case "AT","BE","BG","CH","CY","CZ","DE","DK","EE","ES",
                 "FI","FR","GB","GR","HR","HU","IE","IS","IT","LI",
                 "LT","LU","LV","MC","MT","NL","NO","PL","PT","RO",
                 "SE","SI","SK","SM","AD" -> true;
            default -> false;
        };
    }
    /**
     * Supprime les espaces et met en majuscules.
     */
    private static String normalize(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase();
    }
    
    /**
     * Vérifie que l'IBAN ne contient que des lettres et des chiffres,
     * avec les 2 premiers caractères alphabétiques et les 2 suivants numériques.
     */
    private static boolean matchesPattern(String iban) {
        return iban.matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
    }
    
    /**
     * Vérifie la longueur par rapport au registre du pays.
     * Si le pays est inconnu du registre, on accepte (longueur entre 15 et 34).
     */
    private static boolean hasCorrectLength(String iban) {
        String cc = iban.substring(0, 2);
        Integer expected = IBAN_LENGTHS.get(cc);
        if (expected != null) {
            return iban.length() == expected;
        }
        // Pays inconnu du registre : vérification de la plage ISO 13616
        return iban.length() >= 15 && iban.length() <= 34;
    }
    /**
     * Calcule le checksum MOD 97-10 (ISO 7064).
     * Étapes :
     * 1. Déplacer les 4 premiers caractères à la fin.
     * 2. Remplacer chaque lettre par sa valeur numérique (A=10, B=11, …, Z=35).
     * 3. Calculer le modulo 97 — doit être égal à 1.
     */
    private static boolean checksum(String iban) {
    	String rearranged = iban.substring(4) + iban.substring(0,4);
    	StringBuilder numeric = new StringBuilder();
    	
    	for (char c : rearranged.toCharArray()) {
    		
    		if (Character.isLetter(c))
    			numeric.append(c - 'A' + 10);
    		else
    			numeric.append(c);
    	}
    	try {
    		return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).equals(BigInteger.ONE);
    	}
    	catch (NumberFormatException e) {
    		return false;
    	}
    }


}
