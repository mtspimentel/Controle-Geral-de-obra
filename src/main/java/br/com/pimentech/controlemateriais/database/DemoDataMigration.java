package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class DemoDataMigration implements Migration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Dados iniciais da obra de demonstração";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        long obraId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO obras
                    (nome, codigo, endereco, responsavel, data_inicio, previsao_termino, status, ativo)
                VALUES (?, ?, ?, ?, ?, ?, 'ATIVA', 1)
                """)) {
            statement.setString(1, "Reforma Hospitalar Rio Claro");
            statement.setString(2, "OBR-001");
            statement.setString(3, "Rio Claro - SP");
            statement.setString(4, "Responsável da obra");
            statement.setString(5, "2026-09-01");
            statement.setString(6, "2027-06-30");
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM obras WHERE codigo = ?")) {
            statement.setString(1, "OBR-001");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Não foi possível localizar a obra inicial");
                }
                obraId = result.getLong(1);
            }
        }

        long fornecedorId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO fornecedores (nome, cnpj, telefone, email, contato, ativo)
                VALUES (?, ?, ?, ?, ?, 1)
                """)) {
            statement.setString(1, "ABC Materiais");
            statement.setString(2, "00.000.000/0001-00");
            statement.setString(3, "(19) 0000-0000");
            statement.setString(4, "contato@abcmateriais.local");
            statement.setString(5, "Equipe comercial");
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM fornecedores WHERE nome = ?")) {
            statement.setString(1, "ABC Materiais");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Não foi possível localizar o fornecedor inicial");
                }
                fornecedorId = result.getLong(1);
            }
        }

        insertMaterial(connection, obraId, "MAT-001", "Cimento CP-II", "SC", "Cimento", "CP-II", 140, 20, 7);
        insertMaterial(connection, obraId, "MAT-002", "Argamassa AC-II", "SC", "Argamassa", "AC-II", 75, 15, 5);
        insertMaterial(connection, obraId, "MAT-003", "Tubo PVC 100mm", "UN", "Tubos", "100mm", 50, 5, 10);
        if (fornecedorId <= 0) {
            throw new SQLException("Fornecedor inicial inválido");
        }
    }

    private void insertMaterial(Connection connection, long obraId, String codigo, String descricao,
                                String unidade, String categoria, String especificacao,
                                double estoque, double consumo, int prazo) throws SQLException {
        long materialId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO materiais
                    (obra_id, codigo, descricao, unidade, categoria, especificacao,
                     estoque_minimo, consumo_medio_diario, prazo_medio_entrega_dias, ativo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """)) {
            statement.setLong(1, obraId);
            statement.setString(2, codigo);
            statement.setString(3, descricao);
            statement.setString(4, unidade);
            statement.setString(5, categoria);
            statement.setString(6, especificacao);
            statement.setDouble(7, estoque);
            statement.setDouble(8, consumo);
            statement.setInt(9, prazo);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM materiais WHERE obra_id = ? AND codigo = ?")) {
            statement.setLong(1, obraId);
            statement.setString(2, codigo);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Não foi possível localizar o material inicial " + codigo);
                }
                materialId = result.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO estoque (obra_id, material_id, quantidade_atual)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, obraId);
            statement.setLong(2, materialId);
            statement.setDouble(3, estoque);
            statement.executeUpdate();
        }
    }
}
