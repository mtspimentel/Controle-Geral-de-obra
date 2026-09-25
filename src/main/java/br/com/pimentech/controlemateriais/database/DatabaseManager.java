package br.com.pimentech.controlemateriais.database;

import br.com.pimentech.controlemateriais.exception.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class DatabaseManager implements AutoCloseable {

    private final Path rootDirectory;
    private final Path databasePath;
    private final List<Migration> migrations = List.of(new SchemaMigration(), new DemoDataMigration(), new OptionalSupplierMigration(), new PedidoStatusMigration(), new MaterialTypeMigration(), new AlmoxarifadoMigration(), new DiarioObraMigration(), new DiarioObraEquipamentosMigration(), new EquipmentRentalMigration(), new PlanejamentoMigration(), new CronogramaMensalMigration(), new CronogramaExecucaoMigration(), new CronogramaProducaoMigration(), new PlanejamentoDiarioMigration(), new FrentesObraMigration(), new FrentesEntreAreasMigration());

    public DatabaseManager() {
        this(Path.of(System.getProperty("user.dir")));
    }

    public DatabaseManager(Path rootDirectory) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        this.databasePath = this.rootDirectory.resolve("data").resolve("obra.db");
    }

    public void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
            Files.createDirectories(rootDirectory.resolve("backup"));
            Files.createDirectories(rootDirectory.resolve("exports"));
            Files.createDirectories(rootDirectory.resolve("logs"));
        } catch (IOException exception) {
            throw new PersistenceException("Não foi possível criar as pastas da aplicação", exception);
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            ensureSchemaVersionTable(connection);
            int currentVersion = currentVersion(connection);
            for (Migration migration : migrations) {
                if (migration.version() <= currentVersion) {
                    continue;
                }
                if (migration.requiresForeignKeysOff()) {
                    connection.commit();
                    connection.setAutoCommit(true);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA foreign_keys = OFF");
                    }
                    migration.apply(connection);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA foreign_keys = ON");
                    }
                    connection.setAutoCommit(false);
                } else {
                    migration.apply(connection);
                }
                setVersion(connection, migration.version());
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível inicializar o banco de dados local", exception);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    public <T> T inTransaction(TransactionWork<T> work) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Falha durante a transação", exception);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível concluir a transação", exception);
        }
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public int getSchemaVersion() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar a versão do banco", exception);
        }
    }

    private void ensureSchemaVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_version (version) SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void setVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE schema_version SET version = " + version);
        }
    }

    @Override
    public void close() {
        // As conexões são abertas por operação e fechadas automaticamente.
    }
}
