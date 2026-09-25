package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.dto.DashboardSnapshot;
import br.com.pimentech.controlemateriais.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcDashboardRepository implements DashboardRepository {

    private final DatabaseManager database;

    public JdbcDashboardRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public DashboardSnapshot snapshot(long obraId, LocalDate hoje) {
        try (Connection connection = database.getConnection()) {
            int andamento = count(connection, "SELECT COUNT(*) FROM pedidos WHERE obra_id = ? AND status NOT IN ('CONCLUIDO', 'CANCELADO')", obraId);
            int atrasados = count(connection, "SELECT COUNT(*) FROM pedidos WHERE obra_id = ? AND data_prevista_entrega < ? AND status NOT IN ('CONCLUIDO', 'CANCELADO')", obraId, hoje);
            int parciais = count(connection, "SELECT COUNT(*) FROM pedidos WHERE obra_id = ? AND status = 'ENTREGA_PARCIAL'", obraId);
            int pendencias = count(connection, "SELECT COUNT(*) FROM pedidos WHERE obra_id = ? AND status = 'COM_PENDENCIA'", obraId);
            int trocas = count(connection, """
                    SELECT COUNT(*) FROM trocas t JOIN pedido_itens pi ON pi.id = t.pedido_item_id
                    JOIN pedidos p ON p.id = pi.pedido_id
                    WHERE p.obra_id = ? AND t.status NOT IN ('RECEBIDA', 'CANCELADA')
                    """, obraId);
            int concluidos = count(connection, "SELECT COUNT(*) FROM pedidos WHERE obra_id = ? AND status = 'CONCLUIDO'", obraId);
            return new DashboardSnapshot(andamento, atrasados, parciais, pendencias, trocas, concluidos,
                    alerts(connection, obraId, hoje), upcoming(connection, obraId, hoje));
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível carregar o dashboard", exception);
        }
    }

    private List<String> alerts(Connection connection, long obraId, LocalDate hoje) throws SQLException {
        List<String> alerts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT numero, CAST(julianday(?) - julianday(data_prevista_entrega) AS INTEGER)
                FROM pedidos
                WHERE obra_id = ? AND data_prevista_entrega < ? AND status NOT IN ('CONCLUIDO', 'CANCELADO')
                ORDER BY data_prevista_entrega LIMIT 5
                """)) {
            statement.setString(1, hoje.toString());
            statement.setLong(2, obraId);
            statement.setString(3, hoje.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) alerts.add("Pedido " + result.getString(1) + " atrasado há " + result.getInt(2) + " dias");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.numero, COUNT(*) FROM pedido_itens pi JOIN pedidos p ON p.id = pi.pedido_id
                WHERE p.obra_id = ? AND pi.quantidade_pendente > 0 AND p.status NOT IN ('CONCLUIDO', 'CANCELADO')
                GROUP BY p.id, p.numero ORDER BY p.numero LIMIT 5
                """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) alerts.add("Pedido " + result.getString(1) + " possui " + result.getInt(2) + " material(is) pendente(s)");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT m.descricao FROM trocas t JOIN pedido_itens pi ON pi.id = t.pedido_item_id
                JOIN materiais m ON m.id = pi.material_id JOIN pedidos p ON p.id = pi.pedido_id
                WHERE p.obra_id = ? AND t.status NOT IN ('RECEBIDA', 'CANCELADA') LIMIT 5
                """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) alerts.add(result.getString(1) + " está em processo de troca");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT descricao FROM materiais m LEFT JOIN estoque e ON e.material_id = m.id AND e.obra_id = m.obra_id
                WHERE m.obra_id = ? AND m.ativo = 1 AND m.consumo_medio_diario > 0
                  AND COALESCE(e.quantidade_atual, 0) <= m.consumo_medio_diario * m.prazo_medio_entrega_dias
                ORDER BY descricao LIMIT 5
                """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) alerts.add(result.getString(1) + " atingiu o ponto de pedido");
            }
        }
        return alerts;
    }

    private List<DashboardSnapshot.ProximaEntrega> upcoming(Connection connection, long obraId, LocalDate hoje) throws SQLException {
        List<DashboardSnapshot.ProximaEntrega> deliveries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.numero, COALESCE(f.nome, 'A definir pelo suprimentos'), p.data_prevista_entrega,
                       CASE WHEN p.data_prevista_entrega < ? THEN 'ATRASADO' ELSE 'NO PRAZO' END
                FROM pedidos p LEFT JOIN fornecedores f ON f.id = p.fornecedor_id
                WHERE p.obra_id = ? AND p.data_prevista_entrega IS NOT NULL
                  AND p.status NOT IN ('CONCLUIDO', 'CANCELADO')
                ORDER BY p.data_prevista_entrega LIMIT 10
                """)) {
            statement.setString(1, hoje.toString());
            statement.setLong(2, obraId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) deliveries.add(new DashboardSnapshot.ProximaEntrega(
                        result.getString(1), result.getString(2), result.getString(3), result.getString(4)));
            }
        }
        return deliveries;
    }

    private int count(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i].toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
