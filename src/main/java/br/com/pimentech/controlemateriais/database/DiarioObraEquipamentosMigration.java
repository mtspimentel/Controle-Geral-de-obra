package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DiarioObraEquipamentosMigration implements Migration {

    @Override
    public int version() {
        return 8;
    }

    @Override
    public String description() {
        return "Equipamentos e ferramentas do diário de obra";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE diarios_obra ADD COLUMN equipamentos TEXT NOT NULL DEFAULT ''");
        }
    }
}
