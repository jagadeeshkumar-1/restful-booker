package com.nextbillion.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generic test-data utility — service-agnostic helpers used across any microservice suite.
 * <p>
 * Rules for adding methods here:
 *   - No imports from any {@code service.*} package.
 *   - No RestAssured or domain-model references.
 *   - Helpers must be reusable for any future microservice test suite.
 * </p>
 */
public final class TestDataUtil {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TestDataUtil() {}

    /** Returns a unique string suffix based on current timestamp — useful for unique test data. */
    public static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis());
    }

    /** Appends a unique timestamp suffix to the given base string. */
    public static String unique(String base) {
        return base + "_" + uniqueSuffix();
    }

    /** Returns today's date as yyyy-MM-dd. */
    public static String today() {
        return LocalDate.now().format(DATE_FORMAT);
    }

    /** Returns a date offset by {@code daysFromToday} days, formatted as yyyy-MM-dd. */
    public static String dateFromToday(int daysFromToday) {
        return LocalDate.now().plusDays(daysFromToday).format(DATE_FORMAT);
    }

    /** Returns a fixed future date string as yyyy-MM-dd for a given year/month/day. */
    public static String date(int year, int month, int day) {
        return LocalDate.of(year, month, day).format(DATE_FORMAT);
    }

    /** Generates a random alphabetic string of given length. */
    public static String randomAlpha(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + (int) (Math.random() * 26)));
        }
        return sb.toString();
    }

    /** Generates a random integer between min (inclusive) and max (inclusive). */
    public static int randomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}
