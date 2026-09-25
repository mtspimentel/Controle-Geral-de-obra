package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.Fornecedor;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcFornecedorRepository implements FornecedorRepository {

    private static final String SELECT = "SELECT id, nome, cnpj, telefone, email, contato, observacao, ativo, created_at, updated_at FROM fornecedores";
    private final DatabaseManager database;

    public JdbcFornecedorRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<Fornecedor> findById(long id) {
        return query(SELECT + " WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<Fornecedor> findAll() {
        return query(SELECT + " ORDER BY ativo DESC, nome");
    }

    @Override
    public List<Fornecedor> search(String search) {
        String term = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";
        return query(SELECT + " WHERE ativo = 1 AND (LOWER(nome) LIKE ? OR LOWER(COALESCE(cnpj, '')) LIKE ? OR LOWER(COALESCE(contato, '')) LIKE ?) ORDER BY nome", term, term, term);
    }

    @Override
    public long insert(Fornecedor fornecedor) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO fornecedores (nome, cnpj, telefone, email, contato, observacao, ativo, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, fornecedor);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("O fornecedor foi salvo sem retornar seu identificador");
                    }
                    return keys.getLong(1);
                }
            }
        });
    }

    @Override
    public void update(Fornecedor fornecedor) {
        if (fornecedor.id() == null) {
            throw new IllegalArgumentException("Fornecedor sem identificador");
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE fornecedores SET nome = ?, cnpj = ?, telefone = ?, email = ?, contato = ?, observacao = ?,
                        ativo = ?, updated_at = ? WHERE id = ?
                    """)) {
                statement.setString(1, fornecedor.nome());
                statement.setString(2, fornecedor.cnpj());
                statement.setString(3, fornecedor.telefone());
                statement.setString(4, fornecedor.email());
                statement.setString(5, fornecedor.contato());
                statement.setString(6, fornecedor.observacao());
                statement.setInt(7, fornecedor.ativo() ? 1 : 0);
                statement.setString(8, SqliteConverters.instant(fornecedor.updatedAt()));
                statement.setLong(9, fornecedor.id());
                statement.executeUpdate();
                return null;
            }
        });
    }

    private List<Fornecedor> query(String sql, Object... parameters) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Fornecedor> fornecedores = new ArrayList<>();
                while (result.next()) {
                    fornecedores.add(map(result));
                }
                return fornecedores;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar os fornecedores", exception);
        }
    }

    private Fornecedor map(ResultSet result) throws SQLException {
        return new Fornecedor(
                result.getLong("id"),
                result.getString("nome"),
                result.getString("cnpj"),
                result.getString("telefone"),
                result.getString("email"),
                result.getString("contato"),
                result.getString("observacao"),
                SqliteConverters.bool(result, "ativo"),
                SqliteConverters.instant(result, "created_at"),
                SqliteConverters.instant(result, "updated_at")
        );
    }

    private void bind(PreparedStatement statement, Fornecedor fornecedor) throws SQLException {
        statement.setString(1, fornecedor.nome());
        statement.setString(2, fornecedor.cnpj());
        statement.setString(3, fornecedor.telefone());
        statement.setString(4, fornecedor.email());
        statement.setString(5, fornecedor.contato());
        statement.setString(6, fornecedor.observacao());
        statement.setInt(7, fornecedor.ativo() ? 1 : 0);
        statement.setString(8, SqliteConverters.instant(fornecedor.createdAt()));
        statement.setString(9, SqliteConverters.instant(fornecedor.updatedAt()));
    }
}
