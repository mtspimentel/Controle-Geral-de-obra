package br.com.pimentech.controlemateriais.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public final class ApplicationLogger {

    private ApplicationLogger() {
    }

    public static void error(String context, Throwable exception) {
        try {
            Path log = Path.of(System.getProperty("user.dir"), "logs", "application.log");
            Files.createDirectories(log.getParent());
            StringWriter stack = new StringWriter();
            exception.printStackTrace(new PrintWriter(stack));
            String entry = "%s | %s | %s%n%s%n".formatted(LocalDateTime.now(), context,
                    exception.getMessage(), stack);
            Files.writeString(log, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // A falha no log não deve impedir a mensagem amigável da interface.
        }
    }
}
