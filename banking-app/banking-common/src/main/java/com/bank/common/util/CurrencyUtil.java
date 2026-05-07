package com.bank.common.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import com.bank.domain.enums.CurrencyCode;

/**
 * Utilitaires de manipulation monétaire.
 *
 * <p>Toutes les opérations d'arrondi respectent la norme ISO 4217 :
 * le nombre de décimales légales est lu depuis {@link CurrencyCode#getDecimalPlaces()}.</p>
 *
 * <p>La précision de calcul intermédiaire utilise {@code MathContext.DECIMAL128}
 * (34 chiffres significatifs) pour éviter les pertes de précision lors
 * des conversions de change.</p>
 *
 * <p><strong>Important :</strong> ce service ne fournit pas de taux de change en temps réel.
 * Les taux doivent être injectés depuis {@code RateCacheService} (Redis) qui les
 * récupère depuis le flux ECB ou un fournisseur de marché.</p>
 */

public final class CurrencyUtil {
	
	private static final MathContext MC = MathContext.DECIMAL128;
	private static final RoundingMode DEFAULT_ROUND = RoundingMode.HALF_EVEN;
	
	private CurrencyUtil() {}
	
    /**
     * Arrondit un montant au nombre de décimales légales de la devise.
     * Utilise HALF_EVEN (arrondi du banquier) — norme comptable internationale.
     *
     * @param amount   montant à arrondir
     * @param currency devise cible
     * @return montant arrondi
     */

	public static BigDecimal round(BigDecimal amount, CurrencyCode currency) {
		
		if (amount == null) 
			return BigDecimal.ZERO;
		
		return amount.setScale(currency.getDecimalPlaces(), DEFAULT_ROUND);
	}
	
    /**
     * Arrondit selon une politique explicite.
     */

	public static BigDecimal round(BigDecimal amount, CurrencyCode currency, RoundingMode mode) {
		
		if (amount == null)
			return BigDecimal.ZERO;
		
		return amount.setScale(currency.getDecimalPlaces(), mode);
		
	}
	/**
     * Convertit un montant d'une devise source vers une devise cible.
     *
     * @param amount       montant à convertir
     * @param from         devise source
     * @param to           devise cible
     * @param exchangeRate taux de change (1 unité de {@code from} = {@code exchangeRate} unités de {@code to})
     * @return montant converti, arrondi selon les décimales de {@code to}
     * @throws IllegalArgumentException si le taux est nul ou négatif
     */
	
	public static BigDecimal convert(BigDecimal amount, CurrencyCode from, CurrencyCode to, BigDecimal exchangeRate) {
		
		if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;
		
		if (from == to)
			return round(amount, to);
		
		validateExchangeRate(exchangeRate, from, to);
		
		BigDecimal converted = amount.multiply(exchangeRate, MC);
		
		return round(converted, to);
	}
    /**
     * Calcule le taux inverse : si 1 EUR = 1.08 USD, alors 1 USD = 1/1.08 EUR.
     *
     * @param rate taux direct (1 from = rate to)
     * @return taux inverse arrondi à 6 décimales
     */
	public static BigDecimal invertRate(BigDecimal rate) {
		
		validateRate(rate);
		return BigDecimal.ONE.divide(rate, 6, DEFAULT_ROUND);
	}
	
    /**
     * Calcule les frais de change appliqués à une conversion.
     * {@code spreadPercent} représente le spread en pourcentage
     * (ex : 0.5 = 0,5 %).
     *
     * @param amount        montant converti
     * @param currency      devise du montant converti
     * @param spreadPercent spread en pourcentage
     * @return frais arrondis à la devise cible
     */
    public static BigDecimal computeSpreadFee(BigDecimal amount, CurrencyCode currency,
            BigDecimal spreadPercent) {
	
    	if (amount == null || spreadPercent == null) return BigDecimal.ZERO;
	
    	BigDecimal fee = amount.multiply(spreadPercent, MC).divide(BigDecimal.valueOf(100), MC);
    	
    	return round(fee, currency);
	}
    
    /**
     * Additionne deux montants et arrondit le résultat.
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b, CurrencyCode currency) {
    	
    	BigDecimal result = nullSafe(a).add(nullSafe(b), MC);
    	return round(result, currency);
    }
    /**
     * Soustrait {@code b} de {@code a} et arrondit le résultat.
     */
    
    public static BigDecimal subtract(BigDecimal a, BigDecimal b, CurrencyCode currency) {
    	
    	BigDecimal result = nullSafe(a).subtract(nullSafe(b), MC);
    	
    	return round (result, currency);
    }
    
    /**
     * Multiplie un montant par un coefficient et arrondit.
     * Utile pour les calculs d'intérêts, de commissions, de TVA.
     */

    public static BigDecimal multiply(BigDecimal amount, BigDecimal factor, CurrencyCode currency) {
    	
    	BigDecimal result = nullSafe(amount).multiply(nullSafe(factor), MC);
    	
    	return round(result, currency);
    }
    
    // ─────────────────────────────────────────────────────────
    //  Calculs financiers
    // ─────────────────────────────────────────────────────────
 
    /**
     * Calcule les intérêts simples pour une période donnée.
     *
     * <pre>
     *   intérêts = principal × (taux / 100) × (jours / 365)
     * </pre>
     *
     * @param principal    capital de base
     * @param annualRate   taux annuel en pourcentage (ex : 2.5 pour 2,5%)
     * @param days         nombre de jours de la période
     * @param currency     devise
     * @return montant des intérêts arrondi
     */
    public static BigDecimal simpleInterest(BigDecimal principal, BigDecimal annualRate,
                                             int days, CurrencyCode currency) {
        if (principal == null || annualRate == null || days <= 0) 
        	return BigDecimal.ZERO;
        
        BigDecimal rate   = annualRate.divide(BigDecimal.valueOf(100), MC);
        BigDecimal period = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), MC);
        BigDecimal result = principal.multiply(rate, MC).multiply(period, MC);
        
        return round(result, currency);
    }
    /**
     * Calcule les intérêts composés mensuels.
     *
     * <pre>
     *   montant final = principal × (1 + taux_mensuel)^mois
     * </pre>
     *
     * @param principal  capital de base
     * @param annualRate taux annuel en pourcentage
     * @param months     durée en mois
     * @param currency   devise
     * @return capital + intérêts composés
     */
    public static BigDecimal computedInterest(BigDecimal principal,BigDecimal annualRate, int months, CurrencyCode currency) {
    	
    	if (principal == null || annualRate == null || months <= 0)
    		return nullSafe(principal);
    	
    	BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(100),MC)
    			.divide(BigDecimal.valueOf(12), MC);
    	BigDecimal factor = BigDecimal.ONE.add(monthlyRate, MC);
    	BigDecimal result = principal.multiply(factor.pow(months, MC),MC);
    	
    	return round(result, currency);
    	
    }
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
 
    public static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }
 
    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }
 
    public static boolean isGreaterThan(BigDecimal a, BigDecimal b) {
        return nullSafe(a).compareTo(nullSafe(b)) > 0;
    }
 
    public static boolean isLessThanOrEqual(BigDecimal a, BigDecimal b) {
        return nullSafe(a).compareTo(nullSafe(b)) <= 0;
    }
    /**
     * Formate un montant avec son symbole de devise.
     * Ex : 1250.50, EUR → "1 250,50 €"  |  1250.50, USD → "1 250,50 $"
     */
    public static String format(BigDecimal amount, CurrencyCode currency) {
        if (amount == null) 
        	return "0 " + currency.getSymbol();
        BigDecimal rounded = round(amount, currency);
        return String.format("%,."+currency.getDecimalPlaces()+"f %s",
                             rounded, currency.getSymbol());
    }
    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
 
    private static void validateRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le taux de change doit être strictement positif");
        }
    }
 
    private static void validateExchangeRate(BigDecimal rate,
                                              CurrencyCode from, CurrencyCode to) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format(
                "Taux de change invalide pour %s → %s : %s", from, to, rate));
        }
    }


}
