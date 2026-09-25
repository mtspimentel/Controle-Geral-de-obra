package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.EstoqueMovimentacao;
import br.com.pimentech.controlemateriais.model.TipoMovimentacaoEstoque;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcEstoqueRepository implements EstoqueRepository {

    private final DatabaseManager database;

    public JdbcEstoqueRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<EstoqueMovimentacao> findMovimentacoes(long obraId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT em.id, em.obra_id, em.material_id, m.codigo, m.descricao, m.unidade,
                            em.data_movimentacao, em.tipo, em.quantidade, em.responsavel,
                            em.retirante, em.servico, em.observacao, em.referencia
                     FROM estoque_movimentacoes em
                     JOIN materiais m ON m.id = em.material_id
                     WHERE em.obra_id = ?
                     ORDER BY em.data_movimentacao DESC, em.id DESC
                     """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<EstoqueMovimentacao> movimentacoes = new ArrayList<>();
                while (result.next()) {
                    movimentacoes.add(new EstoqueMovimentacao(
                            result.getLong("id"), result.getLong("obra_id"), result.getLong("material_id"),
                            result.getString("codigo"), result.getString("descricao"), result.getString("unidade"),
                            SqliteConverters.localDateTime(result, "data_movimentacao"),
                            TipoMovimentacaoEstoque.valueOf(result.getString("tipo")), result.getDouble("quantidade"),
                            result.getString("responsavel"), result.getString("retirante"), result.getString("servico"),
                            result.getString("observacao"), result.getString("referencia")));
                }
                return movimentacoes;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar o histórico do estoque", exception);
        }
    }

    @Override
    public void registrar(EstoqueMovimentacao movimentacao, double variacaoSaldo) {
        database.inTransaction(connection -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE estoque
                    SET quantidade_atual = quantidade_atual + ?, updated_at = ?
                    WHERE obra_id = ? AND material_id = ? AND quantidade_atual + ? >= 0
                    """)) {
                statement.setDouble(1, variacaoSaldo);
                statement.setString(2, java.time.Instant.now().toString());
                statement.setLong(3, movimentacao.obraId());
                statement.setLong(4, movimentacao.materialId());
                statement.setDouble(5, variacaoSaldo);
                updated = statement.executeUpdate();
            }
            if (updated != 1) {
                throw new ValidationException("Estoque insuficiente ou material não encontrado para esta obra.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO estoque_movimentacoes
                        (obra_id, material_id, data_movimentacao, tipo, quantidade, responsavel,
                         retirante, servico, observacao, referencia)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setLong(1, movimentacao.obraId());
                statement.setLong(2, movimentacao.materialId());
                statement.setString(3, movimentacao.data().toString());
                statement.setString(4, movimentacao.tipo().name());
                statement.setDouble(5, movimentacao.quantidade());
                statement.setString(6, movimentacao.responsavel());
                statement.setString(7, movimentacao.retirante());
                statement.setString(8, movimentacao.servico());
                statement.setString(9, movimentacao.observacao());
                statement.setString(10, movimentacao.referencia());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
