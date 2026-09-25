package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class AlmoxarifadoMigration implements Migration {

    @Override
    public int version() {
        return 6;
    }

    @Override
    public String description() {
        return "Histórico de movimentações e retiradas do almoxarifado";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS estoque_movimentacoes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        material_id INTEGER NOT NULL,
                        data_movimentacao TEXT NOT NULL,
                        tipo TEXT NOT NULL CHECK (tipo IN ('ENTRADA', 'SAIDA', 'RETIRADA', 'DEVOLUCAO')),
                        quantidade REAL NOT NULL CHECK (quantidade > 0),
                        responsavel TEXT NOT NULL,
                        retirante TEXT,
                        servico TEXT,
                        observacao TEXT,
                        referencia TEXT,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        FOREIGN KEY (material_id) REFERENCES materiais (id) ON DELETE RESTRICT
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_estoque_movimentacoes_obra_data ON estoque_movimentacoes (obra_id, data_movimentacao)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_estoque_movimentacoes_referencia ON estoque_movimentacoes (referencia)");
        }
    }
}
