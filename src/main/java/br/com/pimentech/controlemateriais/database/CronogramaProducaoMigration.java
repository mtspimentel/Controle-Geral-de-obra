package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CronogramaProducaoMigration implements Migration {
    @Override public int version() { return 13; }
    @Override public String description() { return "Efetivo e produção física por tarefa e dia"; }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE cronograma_itens ADD COLUMN meta_fisica_definida INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("UPDATE cronograma_itens SET meta_fisica_definida = 1 WHERE total_referencia IS NULL AND quantidade > 0");
            statement.executeUpdate("""
                    CREATE TABLE cronograma_producao (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        cronograma_item_id INTEGER NOT NULL REFERENCES cronograma_itens(id) ON DELETE CASCADE,
                        diario_obra_id INTEGER NOT NULL REFERENCES diarios_obra(id) ON DELETE RESTRICT,
                        data_producao TEXT NOT NULL,
                        quantidade_executada REAL NOT NULL CHECK (quantidade_executada >= 0),
                        observacao TEXT,
                        UNIQUE (cronograma_item_id, diario_obra_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE cronograma_efetivo_alocacao (
                        producao_id INTEGER NOT NULL REFERENCES cronograma_producao(id) ON DELETE CASCADE,
                        funcao TEXT NOT NULL,
                        pessoas INTEGER NOT NULL CHECK (pessoas > 0),
                        horas_por_pessoa REAL NOT NULL CHECK (horas_por_pessoa > 0 AND horas_por_pessoa <= 24),
                        PRIMARY KEY (producao_id, funcao)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX idx_cronograma_producao_obra_data ON cronograma_producao(obra_id, data_producao)");
            statement.executeUpdate("CREATE INDEX idx_cronograma_producao_tarefa ON cronograma_producao(cronograma_item_id)");
            statement.executeUpdate("""
                    CREATE TRIGGER cronograma_producao_rdo_data
                    AFTER UPDATE OF data_diario ON diarios_obra
                    BEGIN
                        UPDATE cronograma_producao SET data_producao = NEW.data_diario
                        WHERE diario_obra_id = NEW.id;
                    END
                    """);
            statement.executeUpdate("""
                    CREATE TRIGGER cronograma_producao_rdo_efetivo
                    BEFORE UPDATE OF efetivo ON diarios_obra
                    WHEN OLD.efetivo <> NEW.efetivo AND EXISTS (
                        SELECT 1 FROM cronograma_producao WHERE diario_obra_id = OLD.id
                    )
                    BEGIN
                        SELECT RAISE(ABORT, 'O efetivo deste RDO está vinculado ao cronograma. Edite ou exclua os apontamentos antes de alterá-lo.');
                    END
                    """);
        }
    }
}
