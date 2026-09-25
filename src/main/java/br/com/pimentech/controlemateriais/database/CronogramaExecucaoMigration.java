package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CronogramaExecucaoMigration implements Migration {
    @Override public int version() { return 12; }
    @Override public String description() { return "Proposta de referência e execução mensal do cronograma"; }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE cronograma_itens ADD COLUMN total_referencia REAL");
            statement.executeUpdate("ALTER TABLE cronograma_itens ADD COLUMN percentual_referencia REAL");
            statement.executeUpdate("""
                    CREATE TABLE cronograma_execucao_meses (
                        cronograma_item_id INTEGER NOT NULL REFERENCES cronograma_itens(id) ON DELETE CASCADE,
                        mes INTEGER NOT NULL CHECK (mes BETWEEN 1 AND 24),
                        valor REAL NOT NULL DEFAULT 0 CHECK (valor >= 0),
                        PRIMARY KEY (cronograma_item_id, mes)
                    )
                    """);
        }
    }
}
