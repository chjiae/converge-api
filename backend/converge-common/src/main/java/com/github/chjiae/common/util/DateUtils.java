package com.github.chjiae.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Common date/time utility methods.
 */
public final class DateUtils {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateUtils() {}

    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMAT);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FORMAT);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }
}
