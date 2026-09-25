package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.Prioridade;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.model.RequisicaoItem;
import br.com.pimentech.controlemateriais.model.RequisicaoStatus;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcRequisicaoRepository implements RequisicaoRepository {

    private static final String SELECT = """
            SELECT r.id, r.numero, r.obra_id, o.nome AS obra_nome, r.solicitante, r.data_requisicao,
                   r.prioridade, r.status, r.observacao, r.created_at, r.updated_at
            FROM requisicoes r
            JOIN obras o ON o.id = r.obra_id
            """;

    private final DatabaseManager database;

    public JdbcRequisicaoRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<Requisicao> findByObraId(long obraId) {
        return query(SELECT + " WHERE r.obra_id = ? ORDER BY r.data_requisicao DESC, r.id DESC", obraId);
    }

    @Override
    public Optional<Requisicao> findById(long id) {
        return query(SELECT + " WHERE r.id = ?", id).stream().findFirst();
    }

    @Override
    public long insert(Requisicao requisicao) {
        return database.inTransaction(connection -> {
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO requisicoes (numero, obra_id, solicitante, data_requisicao, prioridade, status, observacao, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, requisicao.numero());
                statement.setLong(2, requisicao.obraId());
                statement.setString(3, requisicao.solicitante());
                statement.setString(4, requisicao.dataRequisicao().toString());
                statement.setString(5, requisicao.prioridade().name());
                statement.setString(6, requisicao.status().name());
                statement.setString(7, requisicao.observacao());
                statement.setString(8, SqliteConverters.instant(requisicao.createdAt()));
                statement.setString(9, SqliteConverters.instant(requisicao.updatedAt()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("A requisição não retornou seu identificador");
                    }
                    id = keys.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO requisicao_itens (requisicao_id, material_id, quantidade, unidade, observacao)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (RequisicaoItem item : requisicao.itens()) {
                    statement.setLong(1, id);
                    statement.setLong(2, item.materialId());
                    statement.setDouble(3, item.quantidade());
                    statement.setString(4, item.unidade());
                    statement.setString(5, item.observacao());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return id;
        });
    }

    @Override
    public void update(Requisicao requisicao) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE requisicoes SET solicitante = ?, data_requisicao = ?, prioridade = ?, observacao = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, requisicao.solicitante());
                statement.setString(2, requisicao.dataRequisicao().toString());
                statement.setString(3, requisicao.prioridade().name());
                statement.setString(4, requisicao.observacao());
                statement.setString(5, SqliteConverters.instant(requisicao.updatedAt()));
                statement.setLong(6, requisicao.id());
                if (statement.executeUpdate() == 0) throw new br.com.pimentech.controlemateriais.exception.ValidationException("Requisição não encontrada");
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM requisicao_itens WHERE requisicao_id = ?")) {
                delete.setLong(1, requisicao.id());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO requisicao_itens (requisicao_id, material_id, quantidade, unidade, observacao) VALUES (?, ?, ?, ?, ?)")) {
                for (RequisicaoItem item : requisicao.itens()) {
                    insert.setLong(1, requisicao.id());
                    insert.setLong(2, item.materialId());
                    insert.setDouble(3, item.quantidade());
                    insert.setString(4, item.unidade());
                    insert.setString(5, item.observacao());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
    }

    private List<Requisicao> query(String sql, Object... parameters) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Requisicao> requisicoes = new ArrayList<>();
                while (result.next()) {
                    requisicoes.add(map(result, connection));
                }
                return requisicoes;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar as requisições", exception);
        }
    }

    private Requisicao map(ResultSet result, Connection connection) throws SQLException {
        long requisicaoId = result.getLong("id");
        List<RequisicaoItem> itens = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ri.id, ri.material_id, m.codigo, m.descricao, ri.unidade, ri.quantidade, ri.observacao
                FROM requisicao_itens ri JOIN materiais m ON m.id = ri.material_id
                WHERE ri.requisicao_id = ? ORDER BY ri.id
                """)) {
            statement.setLong(1, requisicaoId);
            try (ResultSet items = statement.executeQuery()) {
                while (items.next()) {
                    itens.add(new RequisicaoItem(items.getLong("id"), items.getLong("material_id"),
                            items.getString("codigo"), items.getString("descricao"), items.getString("unidade"), items.getDouble("quantidade"),
                            items.getString("observacao")));
                }
            }
        }
        return new Requisicao(requisicaoId, result.getString("numero"), result.getLong("obra_id"),
                result.getString("obra_nome"), result.getString("solicitante"), SqliteConverters.localDate(result, "data_requisicao"),
                Prioridade.valueOf(result.getString("prioridade")), RequisicaoStatus.valueOf(result.getString("status")),
                result.getString("observacao"), itens, SqliteConverters.instant(result, "created_at"),
                SqliteConverters.instant(result, "updated_at"));
    }
}
