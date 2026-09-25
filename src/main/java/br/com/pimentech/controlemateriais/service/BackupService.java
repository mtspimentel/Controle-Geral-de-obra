package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.SQLException;
import java.sql.Statement;

public final class BackupService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final DatabaseManager database;

    public BackupService(DatabaseManager database) {
        this.database = database;
    }

    public Path criarBackup(Path destino) {
        try {
            Path target = destino.toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            checkpoint();
            Files.copy(database.getDatabasePath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | SQLException exception) {
            throw new PersistenceException("Não foi possível criar o backup do banco", exception);
        }
    }

    public Path criarBackupAutomatico() {
        Path file = database.getRootDirectory().resolve("backup")
                .resolve("backup_" + FILE_DATE.format(LocalDateTime.now()) + ".db");
        return criarBackup(file);
    }

    public void restaurar(Path origem) {
        try {
            Path source = origem.toAbsolutePath().normalize();
            if (!Files.isRegularFile(source)) throw new IOException("Arquivo de backup não encontrado");
            if (source.equals(database.getDatabasePath().toAbsolutePath().normalize())) {
                throw new IOException("Escolha um arquivo de backup diferente do banco atual");
            }
            checkpoint();
            Files.copy(source, database.getDatabasePath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(Path.of(database.getDatabasePath() + "-wal"));
            Files.deleteIfExists(Path.of(database.getDatabasePath() + "-shm"));
            database.initialize();
        } catch (IOException | SQLException exception) {
            throw new PersistenceException("Não foi possível restaurar o backup", exception);
        }
    }

    private void checkpoint() throws SQLException {
        try (var connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }
}
