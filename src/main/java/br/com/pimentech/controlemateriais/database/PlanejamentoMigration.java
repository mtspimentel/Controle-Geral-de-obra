package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class PlanejamentoMigration implements Migration {

    @Override
    public int version() {
        return 10;
    }

    @Override
    public String description() {
        return "Cronograma, orçamento e medições da obra";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cronograma_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        ordem INTEGER NOT NULL DEFAULT 0,
                        codigo TEXT,
                        descricao TEXT NOT NULL,
                        unidade TEXT NOT NULL DEFAULT 'UN',
                        quantidade REAL NOT NULL DEFAULT 1 CHECK (quantidade >= 0),
                        peso_percentual REAL NOT NULL DEFAULT 0 CHECK (peso_percentual >= 0 AND peso_percentual <= 100),
                        inicio_previsto TEXT,
                        fim_previsto TEXT,
                        percentual_executado REAL NOT NULL DEFAULT 0 CHECK (percentual_executado >= 0 AND percentual_executado <= 100),
                        observacao TEXT,
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0, 1)),
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS orcamento_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        codigo TEXT,
                        descricao TEXT NOT NULL,
                        unidade TEXT NOT NULL DEFAULT 'UN',
                        quantidade REAL NOT NULL DEFAULT 1 CHECK (quantidade >= 0),
                        valor_unitario REAL NOT NULL DEFAULT 0 CHECK (valor_unitario >= 0),
                        observacao TEXT,
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0, 1)),
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS medicoes_obra (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        cronograma_item_id INTEGER,
                        orcamento_item_id INTEGER,
                        numero TEXT,
                        data_medicao TEXT NOT NULL,
                        descricao TEXT NOT NULL,
                        quantidade REAL NOT NULL DEFAULT 0 CHECK (quantidade >= 0),
                        percentual_fisico REAL NOT NULL DEFAULT 0 CHECK (percentual_fisico >= 0 AND percentual_fisico <= 100),
                        valor_medido REAL NOT NULL DEFAULT 0 CHECK (valor_medido >= 0),
                        observacao TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        FOREIGN KEY (cronograma_item_id) REFERENCES cronograma_itens (id) ON DELETE SET NULL,
                        FOREIGN KEY (orcamento_item_id) REFERENCES orcamento_itens (id) ON DELETE SET NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cronograma_obra_ordem ON cronograma_itens (obra_id, ativo, ordem, id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_orcamento_obra ON orcamento_itens (obra_id, ativo, id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_medicoes_obra_data ON medicoes_obra (obra_id, data_medicao, id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_medicoes_cronograma ON medicoes_obra (cronograma_item_id)");
        }
    }
}
