package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.DiarioObra;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcDiarioObraRepository implements DiarioObraRepository {

    private static final String SELECT = """
            SELECT id, obra_id, data_diario, atividades, efetivo, equipamentos, clima, observacoes,
                   intercorrencias, created_at, updated_at
            FROM diarios_obra
            """;

    private final DatabaseManager database;

    public JdbcDiarioObraRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<DiarioObra> findByObraId(long obraId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE obra_id = ? ORDER BY data_diario DESC, id DESC")) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<DiarioObra> diarios = new ArrayList<>();
                while (result.next()) diarios.add(map(result));
                return diarios;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar o diário de obra", exception);
        }
    }

    @Override
    public Optional<DiarioObra> findByObraIdAndData(long obraId, String data) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE obra_id = ? AND data_diario = ?")) {
            statement.setLong(1, obraId);
            statement.setString(2, data);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível localizar o diário da data", exception);
        }
    }

    @Override
    public boolean hasLinkedProduction(long diarioId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM cronograma_producao WHERE diario_obra_id = ?)")) {
            statement.setLong(1, diarioId);
            try (ResultSet result = statement.executeQuery()) { return result.next() && result.getInt(1) == 1; }
        } catch (SQLException exception) { throw new PersistenceException("Não foi possível conferir o efetivo vinculado ao cronograma", exception); }
    }

    @Override
    public long insert(DiarioObra diario) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO diarios_obra
                        (obra_id, data_diario, atividades, efetivo, equipamentos, clima, observacoes, intercorrencias, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, diario);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("O diário não retornou seu identificador");
                    return keys.getLong(1);
                }
            }
        });
    }

    @Override
    public void update(DiarioObra diario) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE diarios_obra SET data_diario = ?, atividades = ?, efetivo = ?, equipamentos = ?, clima = ?,
                        observacoes = ?, intercorrencias = ?, updated_at = ?
                    WHERE id = ? AND obra_id = ?
                    """)) {
                statement.setString(1, diario.data().toString());
                statement.setString(2, diario.atividades());
                statement.setString(3, diario.efetivo());
                statement.setString(4, diario.equipamentos());
                statement.setString(5, diario.clima());
                statement.setString(6, diario.observacoes());
                statement.setString(7, diario.intercorrencias());
                statement.setString(8, SqliteConverters.instant(diario.updatedAt()));
                statement.setLong(9, diario.id());
                statement.setLong(10, diario.obraId());
                statement.executeUpdate();
                return null;
            }
        });
    }

    private DiarioObra map(ResultSet result) throws SQLException {
        return new DiarioObra(
                result.getLong("id"), result.getLong("obra_id"),
                SqliteConverters.localDate(result, "data_diario"), result.getString("atividades"),
                result.getString("efetivo"), result.getString("equipamentos"), result.getString("clima"), result.getString("observacoes"),
                result.getString("intercorrencias"), SqliteConverters.instant(result, "created_at"),
                SqliteConverters.instant(result, "updated_at"));
    }

    private void bind(PreparedStatement statement, DiarioObra diario) throws SQLException {
        statement.setLong(1, diario.obraId());
        statement.setString(2, diario.data().toString());
        statement.setString(3, diario.atividades());
        statement.setString(4, diario.efetivo());
        statement.setString(5, diario.equipamentos());
        statement.setString(6, diario.clima());
        statement.setString(7, diario.observacoes());
        statement.setString(8, diario.intercorrencias());
        statement.setString(9, SqliteConverters.instant(diario.createdAt()));
        statement.setString(10, SqliteConverters.instant(diario.updatedAt()));
    }
}
