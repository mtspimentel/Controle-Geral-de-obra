package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class EquipmentRentalMigration implements Migration {

    @Override
    public int version() {
        return 9;
    }

    @Override
    public String description() {
        return "Empresa locatária nas retiradas de equipamentos";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE estoque_movimentacoes ADD COLUMN empresa_locataria TEXT");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_estoque_movimentacoes_empresa ON estoque_movimentacoes (empresa_locataria)");
        }
    }
}
