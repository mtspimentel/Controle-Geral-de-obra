package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.ObraStatus;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcObraRepository implements ObraRepository {

    private static final String SELECT = """
            SELECT id, nome, codigo, endereco, responsavel, data_inicio, previsao_termino,
                   status, ativo, created_at, updated_at
            FROM obras
            """;

    private final DatabaseManager database;

    public JdbcObraRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<Obra> findById(long id) {
        return queryOne(SELECT + " WHERE id = ?", id);
    }

    @Override
    public Optional<Obra> findActive() {
        return queryOne(SELECT + " WHERE ativo = 1 ORDER BY id LIMIT 1");
    }

    @Override
    public List<Obra> findAll() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + " ORDER BY ativo DESC, nome")) {
            try (ResultSet result = statement.executeQuery()) {
                List<Obra> obras = new ArrayList<>();
                while (result.next()) {
                    obras.add(map(result));
                }
                return obras;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar as obras", exception);
        }
    }

    @Override
    public long insert(Obra obra) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO obras
                        (nome, codigo, endereco, responsavel, data_inicio, previsao_termino, status, ativo, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, obra);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("A obra foi salva sem retornar seu identificador");
                    }
                    return keys.getLong(1);
                }
            }
        });
    }

    @Override
    public void update(Obra obra) {
        if (obra.id() == null) {
            throw new IllegalArgumentException("Obra sem identificador");
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE obras SET nome = ?, codigo = ?, endereco = ?, responsavel = ?, data_inicio = ?,
                        previsao_termino = ?, status = ?, ativo = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                bindWithoutId(statement, obra);
                statement.setLong(10, obra.id());
                statement.executeUpdate();
                return null;
            }
        });
    }

    private Optional<Obra> queryOne(String sql, Object... parameters) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar a obra", exception);
        }
    }

    private Obra map(ResultSet result) throws SQLException {
        return new Obra(
                result.getLong("id"),
                result.getString("nome"),
                result.getString("codigo"),
                result.getString("endereco"),
                result.getString("responsavel"),
                SqliteConverters.localDate(result, "data_inicio"),
                SqliteConverters.localDate(result, "previsao_termino"),
                ObraStatus.valueOf(result.getString("status")),
                SqliteConverters.bool(result, "ativo"),
                SqliteConverters.instant(result, "created_at"),
                SqliteConverters.instant(result, "updated_at")
        );
    }

    private void bind(PreparedStatement statement, Obra obra) throws SQLException {
        bindWithoutId(statement, obra);
    }

    private void bindWithoutId(PreparedStatement statement, Obra obra) throws SQLException {
        statement.setString(1, obra.nome());
        statement.setString(2, obra.codigo());
        statement.setString(3, obra.endereco());
        statement.setString(4, obra.responsavel());
        statement.setString(5, SqliteConverters.date(obra.dataInicio()));
        statement.setString(6, SqliteConverters.date(obra.previsaoTermino()));
        statement.setString(7, obra.status().name());
        statement.setInt(8, obra.ativo() ? 1 : 0);
        statement.setString(9, SqliteConverters.instant(obra.createdAt()));
        statement.setString(10, SqliteConverters.instant(obra.updatedAt()));
    }
}
