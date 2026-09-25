package br.com.pimentech.controlemateriais.util;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateFormats {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DateFormats() {
    }

    public static String format(LocalDate date) {
        return date == null ? "-" : DISPLAY.format(date);
    }

    public static void configure(DatePicker picker) {
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : DISPLAY.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) return null;
                try {
                    return LocalDate.parse(text.trim(), DISPLAY);
                } catch (DateTimeParseException exception) {
                    return null;
                }
            }
        });
        picker.getEditor().setText(picker.getValue() == null ? "" : DISPLAY.format(picker.getValue()));
    }
}
