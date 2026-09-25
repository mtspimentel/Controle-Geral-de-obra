package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.dto.EntregaInput;
import br.com.pimentech.controlemateriais.dto.EntregaItemInput;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class JdbcEntregaRepository implements EntregaRepository {

    private final DatabaseManager database;

    public JdbcEntregaRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public long registrar(EntregaInput input) {
        if (input.itens() == null || input.itens().isEmpty()) {
            throw new ValidationException("Informe pelo menos um item da entrega");
        }
        try {
            return database.inTransaction(connection -> register(connection, input));
        } catch (PersistenceException exception) {
            throw exception;
        }
    }

    private long register(Connection connection, EntregaInput input) throws SQLException {
        long entregaId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO entregas (pedido_id, data_prevista, data_recebimento, nota_fiscal, status, observacao)
                VALUES (?, ?, ?, ?, 'RECEBIDA', ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, input.pedidoId());
            if (input.dataPrevista() == null) statement.setNull(2, java.sql.Types.VARCHAR); else statement.setString(2, input.dataPrevista().toString());
            statement.setString(3, input.dataRecebimento().toString());
            statement.setString(4, input.notaFiscal());
            statement.setString(5, input.observacao());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("A entrega não retornou seu identificador");
                entregaId = keys.getLong(1);
            }
        }

        boolean hasRejected = false;
        for (EntregaItemInput item : input.itens()) {
            if (item.quantidadeRecebida() < 0 || item.quantidadeAceita() < 0) {
                throw new ValidationException("As quantidades da entrega não podem ser negativas");
            }
            ItemBalance balance = findBalance(connection, input.pedidoId(), item.pedidoItemId());
            if (item.quantidadeRecebida() > balance.pending()) {
                throw new ValidationException("A quantidade recebida excede o saldo pendente de um item");
            }
            double accepted = item.materialCorreto() ? Math.min(item.quantidadeAceita(), item.quantidadeRecebida()) : 0;
            if (accepted > balance.pending()) {
                throw new ValidationException("A quantidade aceita excede o saldo pendente de um item");
            }
            double rejected = Math.max(0, item.quantidadeRecebida() - accepted);
            if (rejected > 0) hasRejected = true;
            double newAccepted = balance.accepted() + accepted;
            double newPending = Math.max(0, balance.purchased() - newAccepted);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO entrega_itens (entrega_id, pedido_item_id, quantidade_esperada, quantidade_recebida,
                        quantidade_aceita, quantidade_recusada, material_correto, motivo_recusa)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setLong(1, entregaId);
                statement.setLong(2, item.pedidoItemId());
                statement.setDouble(3, balance.pending());
                statement.setDouble(4, item.quantidadeRecebida());
                statement.setDouble(5, accepted);
                statement.setDouble(6, rejected);
                statement.setInt(7, item.materialCorreto() ? 1 : 0);
                statement.setString(8, item.motivoRecusa());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE pedido_itens SET quantidade_recebida = ?, quantidade_aceita = ?, quantidade_pendente = ?,
                        status = ? WHERE id = ? AND pedido_id = ?
                    """)) {
                statement.setDouble(1, newAccepted);
                statement.setDouble(2, newAccepted);
                statement.setDouble(3, newPending);
                statement.setString(4, newPending == 0 ? "RECEBIDO" : "PENDENTE");
                statement.setLong(5, item.pedidoItemId());
                statement.setLong(6, input.pedidoId());
                statement.executeUpdate();
            }
            if (accepted > 0) {
                updateStock(connection, input.pedidoId(), item.pedidoItemId(), accepted);
            }
        }

        boolean hasPending = pendingItems(connection, input.pedidoId());
        String status = hasPending ? (hasRejected ? "COM_PENDENCIA" : "ENTREGA_PARCIAL") : "CONCLUIDO";
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE pedidos SET status = ?, data_recebimento_completo = ?, updated_at = ? WHERE id = ?
                """)) {
            statement.setString(1, status);
            if (hasPending) statement.setNull(2, java.sql.Types.VARCHAR); else statement.setString(2, input.dataRecebimento().toString());
            statement.setString(3, java.time.Instant.now().toString());
            statement.setLong(4, input.pedidoId());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE entregas SET status = ? WHERE id = ?")) {
            statement.setString(1, hasPending ? "PARCIAL" : "RECEBIDA");
            statement.setLong(2, entregaId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO historico_pedidos (pedido_id, data_evento, tipo, descricao) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, input.pedidoId());
            statement.setString(2, input.dataRecebimento().toString());
            statement.setString(3, hasRejected ? "PENDENCIA" : "ENTREGA");
            statement.setString(4, hasPending ? "Entrega parcial registrada" : "Entrega completa registrada");
            statement.executeUpdate();
        }
        return entregaId;
    }

    private boolean pendingItems(Connection connection, long pedidoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM pedido_itens WHERE pedido_id = ? AND quantidade_pendente > 0")) {
            statement.setLong(1, pedidoId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        }
    }

    private ItemBalance findBalance(Connection connection, long pedidoId, long pedidoItemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT quantidade_comprada, quantidade_aceita, quantidade_pendente FROM pedido_itens WHERE id = ? AND pedido_id = ?")) {
            statement.setLong(1, pedidoItemId);
            statement.setLong(2, pedidoId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ValidationException("Item de pedido não encontrado");
                return new ItemBalance(result.getDouble(1), result.getDouble(2), result.getDouble(3));
            }
        }
    }

    private void updateStock(Connection connection, long pedidoId, long pedidoItemId, double amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE estoque SET quantidade_atual = quantidade_atual + ?, updated_at = ?
                WHERE material_id = (SELECT material_id FROM pedido_itens WHERE id = ?)
                  AND obra_id = (SELECT obra_id FROM pedidos WHERE id = ?)
                """)) {
            statement.setDouble(1, amount);
            statement.setString(2, java.time.Instant.now().toString());
            statement.setLong(3, pedidoItemId);
            statement.setLong(4, pedidoId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO estoque_movimentacoes
                    (obra_id, material_id, data_movimentacao, tipo, quantidade, responsavel,
                     retirante, servico, observacao, referencia)
                SELECT p.obra_id, pi.material_id, ?, 'ENTRADA', ?, 'Recebimento', '',
                       'Entrega de pedido', 'Entrada aceita na entrega', 'PEDIDO-' || p.id
                FROM pedidos p
                JOIN pedido_itens pi ON pi.pedido_id = p.id
                WHERE p.id = ? AND pi.id = ?
                """)) {
            statement.setString(1, java.time.LocalDateTime.now().toString());
            statement.setDouble(2, amount);
            statement.setLong(3, pedidoId);
            statement.setLong(4, pedidoItemId);
            statement.executeUpdate();
        }
    }

    private record ItemBalance(double purchased, double accepted, double pending) {
    }
}
