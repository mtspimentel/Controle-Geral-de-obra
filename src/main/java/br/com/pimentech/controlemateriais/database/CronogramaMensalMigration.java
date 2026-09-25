package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CronogramaMensalMigration implements Migration {
    @Override
    public int version() {
        return 11;
    }

    @Override
    public String description() {
        return "Distribuição financeira mensal do cronograma";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cronograma_meses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        cronograma_item_id INTEGER NOT NULL,
                        mes INTEGER NOT NULL CHECK (mes BETWEEN 1 AND 24),
                        valor REAL NOT NULL DEFAULT 0 CHECK (valor >= 0),
                        FOREIGN KEY (cronograma_item_id) REFERENCES cronograma_itens (id) ON DELETE CASCADE,
                        UNIQUE (cronograma_item_id, mes)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cronograma_meses_item ON cronograma_meses (cronograma_item_id, mes)");
        }
    }
}
