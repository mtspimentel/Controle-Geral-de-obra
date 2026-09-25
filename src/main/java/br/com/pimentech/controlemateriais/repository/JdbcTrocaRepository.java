package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.dto.TrocaInput;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class JdbcTrocaRepository implements TrocaRepository {

    private final DatabaseManager database;

    public JdbcTrocaRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public long solicitar(TrocaInput input) {
        return database.inTransaction(connection -> {
            validateInput(input);
            long pedidoId = findPedidoId(connection, input.pedidoItemId());
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO trocas (pedido_item_id, fornecedor_id, data_solicitacao, quantidade, motivo, status, previsao_troca, observacao)
                    VALUES (?, ?, ?, ?, ?, 'SOLICITADA', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, input.pedidoItemId());
                statement.setLong(2, input.fornecedorId());
                statement.setString(3, input.dataSolicitacao().toString());
                statement.setDouble(4, input.quantidade());
                statement.setString(5, input.motivo());
                if (input.previsaoTroca() == null) statement.setNull(6, java.sql.Types.VARCHAR); else statement.setString(6, input.previsaoTroca().toString());
                statement.setString(7, input.observacao());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("A troca não retornou seu identificador");
                    id = keys.getLong(1);
                }
            }
            updateOrderAndHistory(connection, pedidoId, "Troca solicitada");
            return id;
        });
    }

    @Override
    public void receber(long trocaId, java.time.LocalDate dataRecebimento, String observacao) {
        database.inTransaction(connection -> {
            long pedidoId;
            long pedidoItemId;
            double quantity;
            try (PreparedStatement statement = connection.prepareStatement("SELECT pedido_item_id, quantidade, status FROM trocas WHERE id = ?")) {
                statement.setLong(1, trocaId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new ValidationException("Troca não encontrada");
                    if ("RECEBIDA".equals(result.getString("status")) || "CANCELADA".equals(result.getString("status"))) {
                        throw new ValidationException("A troca não pode ser recebida neste status");
                    }
                    pedidoItemId = result.getLong("pedido_item_id");
                    quantity = result.getDouble("quantidade");
                }
            }
            pedidoId = findPedidoId(connection, pedidoItemId);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE trocas SET status = 'RECEBIDA', data_recebimento = ?, observacao = COALESCE(?, observacao) WHERE id = ?")) {
                statement.setString(1, dataRecebimento.toString());
                statement.setString(2, observacao);
                statement.setLong(3, trocaId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE pedido_itens SET quantidade_recebida = quantidade_recebida + ?, quantidade_aceita = quantidade_aceita + ?,
                        quantidade_pendente = MAX(0, quantidade_comprada - quantidade_aceita - ?), status = CASE WHEN quantidade_comprada - quantidade_aceita - ? <= 0 THEN 'RECEBIDO' ELSE status END
                    WHERE id = ?
                    """)) {
                statement.setDouble(1, quantity);
                statement.setDouble(2, quantity);
                statement.setDouble(3, quantity);
                statement.setDouble(4, quantity);
                statement.setLong(5, pedidoItemId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE estoque SET quantidade_atual = quantidade_atual + ?, updated_at = ?
                    WHERE material_id = (SELECT material_id FROM pedido_itens WHERE id = ?)
                      AND obra_id = (SELECT obra_id FROM pedidos WHERE id = ?)
                    """)) {
                statement.setDouble(1, quantity);
                statement.setString(2, java.time.Instant.now().toString());
                statement.setLong(3, pedidoItemId);
                statement.setLong(4, pedidoId);
                statement.executeUpdate();
            }
            String status = pendingItems(connection, pedidoId) == 0 ? "CONCLUIDO" : "ENTREGA_PARCIAL";
            try (PreparedStatement statement = connection.prepareStatement("UPDATE pedidos SET status = ?, data_recebimento_completo = CASE WHEN ? = 'CONCLUIDO' THEN ? ELSE data_recebimento_completo END, updated_at = ? WHERE id = ?")) {
                statement.setString(1, status);
                statement.setString(2, status);
                statement.setString(3, dataRecebimento.toString());
                statement.setString(4, java.time.Instant.now().toString());
                statement.setLong(5, pedidoId);
                statement.executeUpdate();
            }
            updateHistory(connection, pedidoId, dataRecebimento, "Troca recebida");
            return null;
        });
    }

    private void validateInput(TrocaInput input) {
        if (input.quantidade() <= 0) throw new ValidationException("A quantidade da troca deve ser maior que zero");
        if (input.motivo() == null || input.motivo().isBlank()) throw new ValidationException("Informe o motivo da troca");
        if (input.dataSolicitacao() == null) throw new ValidationException("Informe a data da solicitação");
    }

    private long findPedidoId(Connection connection, long pedidoItemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pedido_id FROM pedido_itens WHERE id = ?")) {
            statement.setLong(1, pedidoItemId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ValidationException("Item de pedido não encontrado");
                return result.getLong(1);
            }
        }
    }

    private int pendingItems(Connection connection, long pedidoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM pedido_itens WHERE pedido_id = ? AND quantidade_pendente > 0")) {
            statement.setLong(1, pedidoId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private void updateOrderAndHistory(Connection connection, long pedidoId, String description) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE pedidos SET status = 'EM_TROCA', updated_at = ? WHERE id = ?")) {
            statement.setString(1, java.time.Instant.now().toString());
            statement.setLong(2, pedidoId);
            statement.executeUpdate();
        }
        updateHistory(connection, pedidoId, java.time.LocalDate.now(), description);
    }

    private void updateHistory(Connection connection, long pedidoId, java.time.LocalDate date, String description) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO historico_pedidos (pedido_id, data_evento, tipo, descricao) VALUES (?, ?, 'TROCA', ?)")) {
            statement.setLong(1, pedidoId);
            statement.setString(2, date.toString());
            statement.setString(3, description);
            statement.executeUpdate();
        }
    }
}
