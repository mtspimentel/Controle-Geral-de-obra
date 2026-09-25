package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class OptionalSupplierMigration implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Fornecedor definido posteriormente pelo suprimentos";
    }

    @Override
    public boolean requiresForeignKeysOff() {
        return true;
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE pedidos_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        numero TEXT NOT NULL UNIQUE,
                        obra_id INTEGER NOT NULL,
                        requisicao_id INTEGER,
                        fornecedor_id INTEGER,
                        data_pedido TEXT NOT NULL,
                        data_prevista_entrega TEXT,
                        data_recebimento_completo TEXT,
                        status TEXT NOT NULL DEFAULT 'COMPRADO'
                            CHECK (status IN ('SOLICITADO', 'COTADO', 'EM_COTACAO', 'APROVADO', 'COMPRADO', 'AGUARDANDO_ENTREGA', 'ENTREGA_PARCIAL', 'COM_PENDENCIA', 'EM_TROCA', 'ENTREGA_COMPLETA', 'RECEBIDO', 'CONCLUIDO', 'CANCELADO')),
                        observacao TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        FOREIGN KEY (requisicao_id) REFERENCES requisicoes (id) ON DELETE SET NULL,
                        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id) ON DELETE RESTRICT
                    )
                    """);
            statement.executeUpdate("INSERT INTO pedidos_new SELECT * FROM pedidos");
            statement.executeUpdate("DROP TABLE pedidos");
            statement.executeUpdate("ALTER TABLE pedidos_new RENAME TO pedidos");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pedidos_obra_status ON pedidos (obra_id, status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pedidos_fornecedor ON pedidos (fornecedor_id, status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pedidos_previsao ON pedidos (data_prevista_entrega)");
        }
    }
}
