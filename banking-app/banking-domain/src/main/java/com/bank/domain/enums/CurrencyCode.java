package com.bank.domain.enums;

import java.util.Arrays;
import java.util.Optional;

public enum CurrencyCode {
    // ── Zone euro ─────────────────────────────────────────────
    EUR(978,  2, "€",  "Euro"),
 
    // ── Devises majeures hors zone euro ───────────────────────
    USD(840,  2, "$",  "Dollar américain"),
    GBP(826,  2, "£",  "Livre sterling"),
    CHF(756,  2, "Fr", "Franc suisse"),
    JPY(392,  0, "¥",  "Yen japonais"),
    CAD(124,  2, "CA$","Dollar canadien"),
    AUD(036,  2, "A$", "Dollar australien"),
    SEK(752,  2, "kr", "Couronne suédoise"),
    NOK(578,  2, "kr", "Couronne norvégienne"),
    DKK(208,  2, "kr", "Couronne danoise"),
 
    // ── Devises émergentes ────────────────────────────────────
    CNY(156,  2, "¥",  "Yuan renminbi"),
    BRL(986,  2, "R$", "Real brésilien"),
    INR(356,  2, "₹",  "Roupie indienne"),
    MXN(484,  2, "$",  "Peso mexicain"),
    ZAR(710,  2, "R",  "Rand sud-africain"),
    AED(784,  2, "د.إ","Dirham des Émirats"),
    MAD(504,  2, "د.م.","Dirham marocain"),
    TND(788,  3, "DT", "Dinar tunisien"),
 
    // ── Autres devises SEPA ou fréquentes ────────────────────
    PLN(985,  2, "zł", "Zloty polonais"),
    CZK(203,  2, "Kč", "Couronne tchèque"),
    HUF(348,  2, "Ft", "Forint hongrois"),
    RON(946,  2, "lei","Leu roumain"),
    SGD(702,  2, "S$", "Dollar de Singapour"),
    HKD(344,  2, "HK$","Dollar de Hong Kong");
	
	private final int numericCode;
	private final int decimalPlaces;
	private final String symbol;
	private final String label;
	
    CurrencyCode(int numericCode, int decimalPlaces, String symbol, String label) {
        this.numericCode   = numericCode;
        this.decimalPlaces = decimalPlaces;
        this.symbol        = symbol;
        this.label         = label;
    }

	public int getNumericCode() {
		return numericCode;
	}

	public int getDecimalPlaces() {
		return decimalPlaces;
	}

	public String getSymbol() {
		return symbol;
	}

	public String getLabel() {
		return label;
	}
    /**
     * Indique si cette devise appartient à la zone SEPA.
     * Utilisé pour appliquer les règles de virement SEPA (délai J+1, frais nuls).
     */
	public boolean isSepa() {
		return switch (this) {
			case EUR, CHF, SEK, NOK, DKK, PLN, CZK, HUF, RON -> true;
			default -> false;
		};
	}
	/**
     * Indique si la devise est considérée comme majeure
     * (liquidité suffisante pour les opérations de change sans délai supplémentaire).
     */
	public boolean isMajor() {
		return switch (this) {
			case EUR, USD, GBP, CHF, JPY, CAD, AUD -> true;
			default -> false;
		};
	}
	
    /**
     * Retrouve une devise par son code ISO 4217 (insensible à la casse).
     *
     * @param code code sur 3 lettres, ex : "eur", "USD"
     * @return {@link Optional} contenant la devise, vide si non trouvée
     */
    public static Optional<CurrencyCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(c -> c.name().equalsIgnoreCase(code.trim()))
            .findFirst();
    }
    /**
     * Retrouve une devise par son code numérique ISO 4217.
     *
     * @param numericCode code numérique (ex : 978 pour EUR)
     * @return {@link Optional} contenant la devise, vide si non trouvée
     */
    public static Optional<CurrencyCode> fromNumericCode(int numericCode) {
        return Arrays.stream(values())
            .filter(c -> c.numericCode == numericCode)
            .findFirst();
    }
    /**
     * Représentation lisible pour les logs et les messages d'erreur.
     * Ex : {@code "EUR (€) — Euro"}
     */
    @Override
    public String toString() {
        return String.format("%s (%s) — %s", this.name(), this.symbol, this.label);
    }

}
