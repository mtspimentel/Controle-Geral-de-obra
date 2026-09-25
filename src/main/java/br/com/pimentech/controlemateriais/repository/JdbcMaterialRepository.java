package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.MaterialTipo;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcMaterialRepository implements MaterialRepository {

    private static final String SELECT = """
            SELECT m.id, m.obra_id, m.tipo, m.codigo, m.descricao, m.unidade, m.categoria, m.especificacao,
                   m.estoque_minimo, m.consumo_medio_diario, m.prazo_medio_entrega_dias, m.dias_locado, m.observacao,
                   m.ativo, COALESCE(e.quantidade_atual, 0) AS estoque_atual, m.created_at, m.updated_at
            FROM materiais m
            LEFT JOIN estoque e ON e.material_id = m.id AND e.obra_id = m.obra_id
            """;

    private final DatabaseManager database;

    public JdbcMaterialRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<Material> findById(long id) {
        return query(SELECT + " WHERE m.id = ?", id).stream().findFirst();
    }

    @Override
    public List<Material> findByObraId(long obraId) {
        return query(SELECT + " WHERE m.obra_id = ? AND m.ativo = 1 ORDER BY m.descricao", obraId);
    }

    @Override
    public List<Material> searchByObraId(long obraId, String search) {
        String term = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";
        return query(SELECT + " WHERE m.obra_id = ? AND m.ativo = 1 AND (LOWER(m.codigo) LIKE ? OR LOWER(m.descricao) LIKE ? OR LOWER(COALESCE(m.categoria, '')) LIKE ?) ORDER BY m.descricao", obraId, term, term, term);
    }

    @Override
    public long insert(Material material) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO materiais
                        (obra_id, tipo, codigo, descricao, unidade, categoria, especificacao, estoque_minimo,
                         consumo_medio_diario, prazo_medio_entrega_dias, dias_locado, observacao, ativo, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, material);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("O material foi salvo sem retornar seu identificador");
                    }
                    long id = keys.getLong(1);
                    try (PreparedStatement stock = connection.prepareStatement("INSERT INTO estoque (obra_id, material_id, quantidade_atual) VALUES (?, ?, ?)");) {
                        stock.setLong(1, material.obraId());
                        stock.setLong(2, id);
                        stock.setDouble(3, material.estoqueAtual());
                        stock.executeUpdate();
                    }
                    return id;
                }
            }
        });
    }

    @Override
    public void update(Material material) {
        if (material.id() == null) {
            throw new IllegalArgumentException("Material sem identificador");
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE materiais SET tipo = ?, codigo = ?, descricao = ?, unidade = ?, categoria = ?, especificacao = ?,
                        estoque_minimo = ?, consumo_medio_diario = ?, prazo_medio_entrega_dias = ?, dias_locado = ?, observacao = ?,
                        ativo = ?, updated_at = ? WHERE id = ?
                    """)) {
                statement.setString(1, material.tipo().name());
                statement.setString(2, material.codigo());
                statement.setString(3, material.descricao());
                statement.setString(4, material.unidade());
                statement.setString(5, material.categoria());
                statement.setString(6, material.especificacao());
                statement.setDouble(7, material.estoqueMinimo());
                statement.setDouble(8, material.consumoMedioDiario());
                statement.setInt(9, material.prazoMedioEntregaDias());
                statement.setInt(10, material.diasLocado());
                statement.setString(11, material.observacao());
                statement.setInt(12, material.ativo() ? 1 : 0);
                statement.setString(13, SqliteConverters.instant(material.updatedAt()));
                statement.setLong(14, material.id());
                statement.executeUpdate();
                try (PreparedStatement stock = connection.prepareStatement("UPDATE estoque SET quantidade_atual = ?, updated_at = ? WHERE material_id = ? AND obra_id = ?")) {
                    stock.setDouble(1, material.estoqueAtual());
                    stock.setString(2, SqliteConverters.instant(material.updatedAt()));
                    stock.setLong(3, material.id());
                    stock.setLong(4, material.obraId());
                    stock.executeUpdate();
                }
                return null;
            }
        });
    }

    private List<Material> query(String sql, Object... parameters) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Material> materials = new ArrayList<>();
                while (result.next()) {
                    materials.add(map(result));
                }
                return materials;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar os materiais", exception);
        }
    }

    private Material map(ResultSet result) throws SQLException {
        return new Material(
                result.getLong("id"),
                result.getLong("obra_id"),
                MaterialTipo.valueOf(result.getString("tipo")),
                result.getString("codigo"),
                result.getString("descricao"),
                result.getString("unidade"),
                result.getString("categoria"),
                result.getString("especificacao"),
                result.getDouble("estoque_minimo"),
                result.getDouble("consumo_medio_diario"),
                result.getInt("prazo_medio_entrega_dias"),
                result.getInt("dias_locado"),
                result.getString("observacao"),
                SqliteConverters.bool(result, "ativo"),
                result.getDouble("estoque_atual"),
                SqliteConverters.instant(result, "created_at"),
                SqliteConverters.instant(result, "updated_at")
        );
    }

    private void bind(PreparedStatement statement, Material material) throws SQLException {
        statement.setLong(1, material.obraId());
        statement.setString(2, material.tipo().name());
        statement.setString(3, material.codigo());
        statement.setString(4, material.descricao());
        statement.setString(5, material.unidade());
        statement.setString(6, material.categoria());
        statement.setString(7, material.especificacao());
        statement.setDouble(8, material.estoqueMinimo());
        statement.setDouble(9, material.consumoMedioDiario());
        statement.setInt(10, material.prazoMedioEntregaDias());
        statement.setInt(11, material.diasLocado());
        statement.setString(12, material.observacao());
        statement.setInt(13, material.ativo() ? 1 : 0);
        statement.setString(14, SqliteConverters.instant(material.createdAt()));
        statement.setString(15, SqliteConverters.instant(material.updatedAt()));
    }
}
