package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class MaterialTypeMigration implements Migration {

    @Override
    public int version() {
        return 5;
    }

    @Override
    public String description() {
        return "Tipos de cadastro e dias locados dos equipamentos";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE materiais ADD COLUMN tipo TEXT NOT NULL DEFAULT 'MATERIAL' CHECK (tipo IN ('MATERIAL', 'EQUIPAMENTO', 'FERRAMENTA', 'OUTRO'))");
            statement.executeUpdate("ALTER TABLE materiais ADD COLUMN dias_locado INTEGER NOT NULL DEFAULT 0 CHECK (dias_locado >= 0)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_materiais_obra_tipo ON materiais (obra_id, tipo, ativo)");
        }
    }
}
