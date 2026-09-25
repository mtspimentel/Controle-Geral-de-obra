package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DiarioObraMigration implements Migration {

    @Override
    public int version() {
        return 7;
    }

    @Override
    public String description() {
        return "Diário de obra com atividades, efetivo, clima e ocorrências";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS diarios_obra (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        data_diario TEXT NOT NULL,
                        atividades TEXT NOT NULL,
                        efetivo TEXT NOT NULL,
                        clima TEXT NOT NULL,
                        observacoes TEXT NOT NULL,
                        intercorrencias TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        UNIQUE (obra_id, data_diario)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_diarios_obra_data ON diarios_obra (obra_id, data_diario)");
        }
    }
}
