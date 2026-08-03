package com.risen.incidentboard.domain;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The one place the Java and SQLite conventions meet.
 *
 * With JPA gone there are no AttributeConverters, so these two pairs are the
 * whole translation layer -- and keeping them in one class means nothing
 * downstream needs a case-insensitive comparison or a timestamp parse.
 */
public final class DbValues {

    private DbValues() { }

    /** Java enum constants are uppercase; the database stores lowercase to match
     *  its CHECK constraints. */
    public static String toDb(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    public static <E extends Enum<E>> E toEnum(Class<E> type, String dbValue) {
        if (dbValue == null || dbValue.isBlank()) return null;
        return Enum.valueOf(type, dbValue.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Fixed-width ISO-8601 UTC.
     *
     * SQLite compares TEXT lexicographically, so ORDER BY occurred_at is only
     * correct if every value shares one offset AND one width. Truncating to
     * seconds guarantees the width: without it, 2026-08-02T02:40:00.123Z would
     * sort after 2026-08-02T02:40:01Z, because '.' precedes '1' in ASCII.
     */
    public static String toDb(Instant value) {
        if (value == null) return null;
        return DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.SECONDS));
    }

    public static Instant toInstant(String dbValue) {
        return dbValue == null || dbValue.isBlank() ? null : Instant.parse(dbValue.trim());
    }
}
