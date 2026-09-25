package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.model.PedidoItem;
import br.com.pimentech.controlemateriais.model.PedidoStatus;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcPedidoRepository implements PedidoRepository {

    private static final String SELECT = """
            SELECT p.id, p.numero, p.obra_id, o.nome AS obra_nome, p.requisicao_id, p.fornecedor_id,
                   f.nome AS fornecedor_nome, p.data_pedido, p.data_prevista_entrega,
                   p.data_recebimento_completo, p.status, p.observacao, p.created_at, p.updated_at
            FROM pedidos p
            JOIN obras o ON o.id = p.obra_id
            LEFT JOIN fornecedores f ON f.id = p.fornecedor_id
            """;

    private final DatabaseManager database;

    public JdbcPedidoRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<Pedido> findByObraId(long obraId) {
        return query(SELECT + " WHERE p.obra_id = ? AND p.status <> 'CANCELADO' ORDER BY p.data_pedido DESC, p.id DESC", obraId);
    }

    @Override
    public Optional<Pedido> findById(long id) {
        return query(SELECT + " WHERE p.id = ?", id).stream().findFirst();
    }

    @Override
    public long insert(Pedido pedido) {
        return database.inTransaction(connection -> {
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pedidos (numero, obra_id, requisicao_id, fornecedor_id, data_pedido,
                        data_prevista_entrega, status, observacao, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, pedido.numero());
                statement.setLong(2, pedido.obraId());
                if (pedido.requisicaoId() == null) statement.setNull(3, java.sql.Types.INTEGER); else statement.setLong(3, pedido.requisicaoId());
                if (pedido.fornecedorId() == null) statement.setNull(4, java.sql.Types.INTEGER); else statement.setLong(4, pedido.fornecedorId());
                statement.setString(5, pedido.dataPedido().toString());
                if (pedido.dataPrevistaEntrega() == null) statement.setNull(6, java.sql.Types.VARCHAR); else statement.setString(6, pedido.dataPrevistaEntrega().toString());
                statement.setString(7, pedido.status().name());
                statement.setString(8, pedido.observacao());
                statement.setString(9, SqliteConverters.instant(pedido.createdAt()));
                statement.setString(10, SqliteConverters.instant(pedido.updatedAt()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("O pedido não retornou seu identificador");
                    }
                    id = keys.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pedido_itens (pedido_id, material_id, quantidade_solicitada, quantidade_comprada,
                        quantidade_recebida, quantidade_aceita, quantidade_pendente, unidade, custo_unitario, status)
                    VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?, 'PENDENTE')
                    """)) {
                for (PedidoItem item : pedido.itens()) {
                    statement.setLong(1, id);
                    statement.setLong(2, item.materialId());
                    statement.setDouble(3, item.quantidadeSolicitada());
                    statement.setDouble(4, item.quantidadeComprada());
                    statement.setDouble(5, item.quantidadeComprada());
                    statement.setString(6, item.unidade());
                    statement.setDouble(7, item.custoUnitario());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            if (pedido.requisicaoId() != null) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE requisicoes SET status = 'CONVERTIDA', updated_at = ? WHERE id = ?")) {
                    statement.setString(1, SqliteConverters.instant(java.time.Instant.now()));
                    statement.setLong(2, pedido.requisicaoId());
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO historico_pedidos (pedido_id, data_evento, tipo, descricao) VALUES (?, ?, 'CRIACAO', ?)") ) {
                statement.setLong(1, id);
                statement.setString(2, pedido.dataPedido().toString());
                statement.setString(3, "Pedido criado");
                statement.executeUpdate();
            }
            return id;
        });
    }

    @Override
    public int proximoNumero() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT numero FROM pedidos WHERE numero LIKE 'PED-%'")) {
            int maior = 0;
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String numero = result.getString(1);
                    if (numero == null || !numero.startsWith("PED-")) continue;
                    try {
                        maior = Math.max(maior, Integer.parseInt(numero.substring(4)));
                    } catch (NumberFormatException ignored) {
                        // Códigos antigos com data continuam válidos e são ignorados na sequência numérica.
                    }
                }
            }
            return maior + 1;
        } catch (SQLException exception) {
            throw new PersistenceException("NÃ£o foi possÃ­vel gerar o cÃ³digo do pedido", exception);
        }
    }

    @Override
    public void definirFornecedor(long pedidoId, long fornecedorId) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE pedidos SET fornecedor_id = ?, updated_at = ? WHERE id = ?")) {
                statement.setLong(1, fornecedorId);
                statement.setString(2, SqliteConverters.instant(java.time.Instant.now()));
                statement.setLong(3, pedidoId);
                if (statement.executeUpdate() == 0) throw new br.com.pimentech.controlemateriais.exception.ValidationException("Pedido não encontrado");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO historico_pedidos (pedido_id, data_evento, tipo, descricao) VALUES (?, ?, 'FORNECEDOR', 'Fornecedor definido pelo suprimentos')")) {
                statement.setLong(1, pedidoId);
                statement.setString(2, java.time.LocalDate.now().toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void atualizarStatus(long pedidoId, PedidoStatus status) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE pedidos SET status = ?, updated_at = ? WHERE id = ?")) {
                statement.setString(1, status.name());
                statement.setString(2, SqliteConverters.instant(java.time.Instant.now()));
                statement.setLong(3, pedidoId);
                if (statement.executeUpdate() == 0) throw new br.com.pimentech.controlemateriais.exception.ValidationException("Pedido não encontrado");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO historico_pedidos (pedido_id, data_evento, tipo, descricao) VALUES (?, ?, 'STATUS', ?)")) {
                statement.setLong(1, pedidoId);
                statement.setString(2, java.time.LocalDate.now().toString());
                statement.setString(3, "Status alterado para " + status.name());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void excluir(long pedidoId) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE pedidos SET status = 'CANCELADO', updated_at = ? WHERE id = ? AND status <> 'CANCELADO'")) {
                statement.setString(1, SqliteConverters.instant(java.time.Instant.now()));
                statement.setLong(2, pedidoId);
                if (statement.executeUpdate() == 0) throw new br.com.pimentech.controlemateriais.exception.ValidationException("Pedido não encontrado ou já excluído");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO historico_pedidos (pedido_id, data_evento, tipo, descricao) VALUES (?, ?, 'EXCLUSAO', 'Pedido excluído pelo usuário')")) {
                statement.setLong(1, pedidoId);
                statement.setString(2, java.time.LocalDate.now().toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private List<Pedido> query(String sql, Object... parameters) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Pedido> pedidos = new ArrayList<>();
                while (result.next()) {
                    pedidos.add(map(result, connection));
                }
                return pedidos;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar os pedidos", exception);
        }
    }

    private Pedido map(ResultSet result, Connection connection) throws SQLException {
        long pedidoId = result.getLong("id");
        List<PedidoItem> itens = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pi.id, pi.material_id, m.descricao, pi.unidade, pi.quantidade_solicitada,
                       pi.quantidade_comprada, pi.quantidade_recebida, pi.quantidade_aceita,
                       pi.quantidade_pendente, pi.custo_unitario, pi.status,
                       (SELECT GROUP_CONCAT(ei.motivo_recusa, ' | ')
                          FROM entrega_itens ei
                         WHERE ei.pedido_item_id = pi.id
                           AND ei.motivo_recusa IS NOT NULL
                           AND TRIM(ei.motivo_recusa) <> '') AS motivo_pendencia
                FROM pedido_itens pi JOIN materiais m ON m.id = pi.material_id
                WHERE pi.pedido_id = ? ORDER BY pi.id
                """)) {
            statement.setLong(1, pedidoId);
            try (ResultSet items = statement.executeQuery()) {
                while (items.next()) {
                    itens.add(new PedidoItem(items.getLong("id"), items.getLong("material_id"), items.getString("descricao"),
                            items.getString("unidade"), items.getDouble("quantidade_solicitada"), items.getDouble("quantidade_comprada"),
                            items.getDouble("quantidade_recebida"), items.getDouble("quantidade_aceita"), items.getDouble("quantidade_pendente"),
                            items.getDouble("custo_unitario"), items.getString("status"), items.getString("motivo_pendencia")));
                }
            }
        }
        return new Pedido(pedidoId, result.getString("numero"), result.getLong("obra_id"), result.getString("obra_nome"),
                result.getObject("requisicao_id") == null ? null : result.getLong("requisicao_id"), result.getObject("fornecedor_id") == null ? null : result.getLong("fornecedor_id"),
                result.getString("fornecedor_nome"), SqliteConverters.localDate(result, "data_pedido"),
                SqliteConverters.localDate(result, "data_prevista_entrega"), SqliteConverters.localDate(result, "data_recebimento_completo"),
                PedidoStatus.valueOf(result.getString("status")), result.getString("observacao"), itens,
                SqliteConverters.instant(result, "created_at"), SqliteConverters.instant(result, "updated_at"));
    }
}
