package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.dto.EntregaResumo;
import br.com.pimentech.controlemateriais.dto.TrocaResumo;
import br.com.pimentech.controlemateriais.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcAcompanhamentoRepository implements AcompanhamentoRepository {

    private final DatabaseManager database;

    public JdbcAcompanhamentoRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<EntregaResumo> entregasPorObra(long obraId) {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT e.id, p.numero, f.nome, e.data_recebimento, e.nota_fiscal, e.status, COUNT(ei.id)
                FROM entregas e JOIN pedidos p ON p.id = e.pedido_id LEFT JOIN fornecedores f ON f.id = p.fornecedor_id
                LEFT JOIN entrega_itens ei ON ei.entrega_id = e.id
                WHERE p.obra_id = ? GROUP BY e.id, p.numero, f.nome ORDER BY e.data_recebimento DESC, e.id DESC
                """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<EntregaResumo> rows = new ArrayList<>();
                while (result.next()) rows.add(new EntregaResumo(result.getLong(1), result.getString(2), result.getString(3) == null ? "A definir pelo suprimentos" : result.getString(3),
                        result.getString(4), result.getString(5), result.getString(6), result.getInt(7)));
                return rows;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar as entregas", exception);
        }
    }

    @Override
    public List<TrocaResumo> trocasPorObra(long obraId) {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT t.id, p.numero, m.descricao, f.nome, t.quantidade, t.data_solicitacao,
                       t.previsao_troca, t.data_recebimento, t.status, t.motivo
                FROM trocas t JOIN pedido_itens pi ON pi.id = t.pedido_item_id JOIN pedidos p ON p.id = pi.pedido_id
                JOIN materiais m ON m.id = pi.material_id JOIN fornecedores f ON f.id = t.fornecedor_id
                WHERE p.obra_id = ? ORDER BY t.data_solicitacao DESC, t.id DESC
                """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<TrocaResumo> rows = new ArrayList<>();
                while (result.next()) rows.add(new TrocaResumo(result.getLong(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getDouble(5), result.getString(6), result.getString(7),
                        result.getString(8), result.getString(9), result.getString(10)));
                return rows;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar as trocas", exception);
        }
    }
}
