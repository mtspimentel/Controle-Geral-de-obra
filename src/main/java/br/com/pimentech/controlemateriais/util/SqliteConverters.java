package br.com.pimentech.controlemateriais.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class SqliteConverters {

    private static final DateTimeFormatter SQLITE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SqliteConverters() {
    }

    public static LocalDate localDate(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    public static Instant instant(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return LocalDateTime.parse(value, SQLITE_TIMESTAMP).toInstant(ZoneOffset.UTC);
        }
    }

    public static LocalDateTime localDateTime(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return LocalDateTime.parse(value, SQLITE_TIMESTAMP);
        }
    }

    public static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    public static boolean bool(ResultSet result, String column) throws SQLException {
        return result.getInt(column) == 1;
    }
}
