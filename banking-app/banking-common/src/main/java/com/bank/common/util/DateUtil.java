package com.bank.common.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Utilitaires de manipulation de dates et d'heures pour le domaine bancaire.
 *
 * <p>Toutes les méthodes opèrent en UTC sauf indication contraire.
 * Les dates de valeur bancaire (date de règlement, date de prélèvement)
 * doivent toujours être manipulées en {@link LocalDate} sans composante horaire
 * pour éviter les ambiguïtés de fuseau horaire.</p>
 *
 * <p>Calendrier bancaire simplifié : les jours ouvrés excluent les week-ends.
 * Les jours fériés ne sont pas gérés ici — utiliser un service dédié
 * ({@code BankingCalendarService}) pour les cas nécessitant leur exclusion.</p>
 */
public final class DateUtil {

    // ─────────────────────────────────────────────────────────
    //  Formats standards
    // ─────────────────────────────────────────────────────────

    public static final DateTimeFormatter ISO_DATE           = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_DATETIME       = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter FR_DATE            = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter FR_DATETIME        = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    public static final DateTimeFormatter SEPA_DATE          = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter STATEMENT_DATETIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    public static final DateTimeFormatter CARD_EXPIRY        = DateTimeFormatter.ofPattern("MM/yy");

    private DateUtil() {}

    // ─────────────────────────────────────────────────────────
    //  Maintenant
    // ─────────────────────────────────────────────────────────

    /** Date et heure courantes en UTC. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Date courante en UTC. */
    public static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** Timestamp courant en UTC (pour les AuditLog). */
    public static Instant nowInstant() {
        return Instant.now();
    }

    // ─────────────────────────────────────────────────────────
    //  Jours ouvrés bancaires (sans week-ends)
    // ─────────────────────────────────────────────────────────

    /**
     * Ajoute {@code n} jours ouvrés (lundi-vendredi) à une date.
     *
     * @param date  date de départ
     * @param days  nombre de jours ouvrés à ajouter (positif)
     * @return date résultante
     */
    public static LocalDate addBusinessDays(LocalDate date, int days) {
        if (days < 0) throw new IllegalArgumentException("Le nombre de jours doit être positif");
        LocalDate result = date;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            if (isBusinessDay(result)) added++;
        }
        return result;
    }

    /**
     * Calcule le nombre de jours ouvrés entre deux dates.
     */
    public static long businessDaysBetween(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) return -businessDaysBetween(to, from);
        return from.datesUntil(to)
                   .filter(DateUtil::isBusinessDay)
                   .count();
    }

    /**
     * Indique si la date est un jour ouvré (lundi à vendredi).
     */
    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * Retourne le prochain jour ouvré à partir d'une date.
     * Si la date est déjà un jour ouvré, la retourne telle quelle.
     */
    public static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate result = date;
        while (!isBusinessDay(result)) {
            result = result.plusDays(1);
        }
        return result;
    }

    /**
     * Date de valeur SEPA standard (J+1 ouvré).
     */
    public static LocalDate sepaValueDate(LocalDate executionDate) {
        return addBusinessDays(executionDate, 1);
    }

    /**
     * Date de valeur SEPA instantané (même jour si avant cut-off, sinon J+1).
     *
     * @param executionDateTime date/heure d'exécution
     * @param cutOffHour        heure limite pour exécution en J (ex : 20 pour 20h00)
     */
    public static LocalDate sepaInstantValueDate(LocalDateTime executionDateTime, int cutOffHour) {
        LocalDate today = executionDateTime.toLocalDate();
        if (executionDateTime.getHour() < cutOffHour && isBusinessDay(today)) {
            return today;
        }
        return nextBusinessDay(today.plusDays(1));
    }

    // ─────────────────────────────────────────────────────────
    //  Périodes bancaires
    // ─────────────────────────────────────────────────────────

    /**
     * Premier jour du mois courant.
     */
    public static LocalDate startOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * Dernier jour du mois courant.
     */
    public static LocalDate endOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * Premier jour de l'année courante.
     */
    public static LocalDate startOfYear(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfYear());
    }

    /**
     * Nombre de jours dans le mois d'une date.
     */
    public static int daysInMonth(LocalDate date) {
        return date.lengthOfMonth();
    }

    /**
     * Calcule l'âge en années à partir d'une date de naissance.
     * Utilisé pour les vérifications KYC (majorité, etc.).
     */
    public static int ageInYears(LocalDate birthDate) {
        return (int) ChronoUnit.YEARS.between(birthDate, LocalDate.now());
    }

    /**
     * Vérifie si une personne est majeure (≥ 18 ans).
     */
    public static boolean isAdult(LocalDate birthDate) {
        return ageInYears(birthDate) >= 18;
    }

    // ─────────────────────────────────────────────────────────
    //  Cartes bancaires
    // ─────────────────────────────────────────────────────────

    /**
     * Indique si une carte est expirée à la date du jour.
     * Une carte est valide jusqu'au dernier jour du mois d'expiration.
     *
     * @param expiryDate date d'expiration (généralement le 1er du mois d'expiry)
     */
    public static boolean isCardExpired(LocalDate expiryDate) {
        return endOfMonth(expiryDate).isBefore(LocalDate.now());
    }

    /**
     * Indique si une carte expire dans les {@code daysThreshold} prochains jours.
     * Utilisé pour l'envoi des notifications de renouvellement.
     */
    public static boolean isExpiringSoon(LocalDate expiryDate, int daysThreshold) {
        LocalDate lastDay = endOfMonth(expiryDate);
        return !lastDay.isBefore(LocalDate.now())
            && ChronoUnit.DAYS.between(LocalDate.now(), lastDay) <= daysThreshold;
    }

    /**
     * Formate une date d'expiration carte au format MM/YY.
     */
    public static String formatCardExpiry(LocalDate expiryDate) {
        return expiryDate.format(CARD_EXPIRY);
    }

    // ─────────────────────────────────────────────────────────
    //  Parsing robuste
    // ─────────────────────────────────────────────────────────

    /**
     * Parse une date en essayant successivement plusieurs formats courants.
     * Formats tentés dans l'ordre : ISO (yyyy-MM-dd), FR (dd/MM/yyyy).
     *
     * @param input chaîne à parser
     * @return {@link Optional} contenant la date, vide si aucun format ne correspond
     */
    public static Optional<LocalDate> parseDate(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{ISO_DATE, FR_DATE}) {
            try {
                return Optional.of(LocalDate.parse(input.trim(), fmt));
            } catch (DateTimeParseException ignored) { /* essayer le suivant */ }
        }
        return Optional.empty();
    }

    /**
     * Parse une date/heure ISO 8601 ({@code yyyy-MM-dd'T'HH:mm:ss}).
     */
    public static Optional<LocalDateTime> parseDateTime(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDateTime.parse(input.trim(), ISO_DATETIME));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Formatage
    // ─────────────────────────────────────────────────────────

    public static String formatIso(LocalDate date) {
        return date == null ? null : date.format(ISO_DATE);
    }

    public static String formatIso(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(ISO_DATETIME);
    }

    public static String formatFr(LocalDate date) {
        return date == null ? null : date.format(FR_DATE);
    }

    public static String formatFr(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FR_DATETIME);
    }

    /**
     * Formate une date pour un relevé de compte lisible.
     * Ex : 2024-01-27T14:32:00 → "27 janv. 2024, 14:32"
     */
    public static String formatStatement(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(STATEMENT_DATETIME);
    }

    // ─────────────────────────────────────────────────────────
    //  Intervalles
    // ─────────────────────────────────────────────────────────

    /**
     * Vérifie qu'une date est comprise dans un intervalle [from, to] inclus.
     */
    public static boolean isBetween(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    /**
     * Vérifie qu'une date/heure est comprise dans un intervalle [from, to] inclus.
     */
    public static boolean isBetween(LocalDateTime dateTime, LocalDateTime from, LocalDateTime to) {
        return !dateTime.isBefore(from) && !dateTime.isAfter(to);
    }

    /**
     * Nombre de jours entre deux dates (valeur absolue).
     */
    public static long daysBetween(LocalDate from, LocalDate to) {
        return Math.abs(ChronoUnit.DAYS.between(from, to));
    }

    /**
     * Nombre de mois entre deux dates (valeur absolue).
     */
    public static long monthsBetween(LocalDate from, LocalDate to) {
        return Math.abs(ChronoUnit.MONTHS.between(from, to));
    }
}