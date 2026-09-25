package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Permite dependências entre serviços da mesma obra em locais distintos, sem alterar vínculos salvos. */
public final class FrentesEntreAreasMigration implements Migration {
    @Override public int version() { return 16; }
    @Override public String description() { return "Dependências entre áreas da mesma obra"; }

    @Override public void apply(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.executeUpdate("DROP TRIGGER ck_vinculo_escopo");
            sql.executeUpdate("""
                    CREATE TRIGGER ck_vinculo_escopo BEFORE INSERT ON vinculos_frente
                    WHEN NOT EXISTS (
                        SELECT 1 FROM servicos_area origem JOIN servicos_area destino
                        ON destino.obra_id = origem.obra_id
                        WHERE origem.id = NEW.origem_servico_id AND destino.id = NEW.destino_servico_id
                        AND origem.obra_id = NEW.obra_id AND origem.area_id = NEW.area_id
                        AND origem.ativo = 1 AND destino.ativo = 1
                    ) BEGIN SELECT RAISE(ABORT, 'Origem e destino devem pertencer à mesma obra e à área informada para a origem'); END
                    """);
        }
    }
}
