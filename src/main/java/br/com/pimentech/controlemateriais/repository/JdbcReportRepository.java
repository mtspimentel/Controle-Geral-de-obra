package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public final class JdbcReportRepository implements ReportRepository {

    private final DatabaseManager database;

    public JdbcReportRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public void exportPedidos(long obraId, LocalDate inicio, LocalDate fim, Path destino) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.numero, f.nome, p.data_pedido, p.data_prevista_entrega,
                            p.data_recebimento_completo, p.status, m.descricao,
                            pi.quantidade_comprada, pi.quantidade_aceita, pi.quantidade_pendente
                     FROM pedidos p LEFT JOIN fornecedores f ON f.id = p.fornecedor_id
                     JOIN pedido_itens pi ON pi.pedido_id = p.id
                     JOIN materiais m ON m.id = pi.material_id
                     WHERE p.obra_id = ? AND p.data_pedido BETWEEN ? AND ?
                     ORDER BY p.data_pedido, p.numero, m.descricao
                     """)) {
            statement.setLong(1, obraId);
            statement.setString(2, inicio.toString());
            statement.setString(3, fim.toString());
            try (ResultSet result = statement.executeQuery(); BufferedWriter writer = writer(destino)) {
                writer.write("Pedido;Fornecedor;Data pedido;Previsão;Recebimento completo;Status;Material;Comprado;Aceito;Pendente");
                writer.newLine();
                while (result.next()) {
                    writeRow(writer, result.getString(1), result.getString(2), result.getString(3), result.getString(4),
                            result.getString(5), result.getString(6), result.getString(7), result.getString(8),
                            result.getString(9), result.getString(10));
                }
            }
        } catch (SQLException | IOException exception) {
            throw new PersistenceException("Não foi possível exportar os pedidos", exception);
        }
    }

    @Override
    public void exportPlanejamento(long obraId, Path destino) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT m.codigo, m.descricao, m.unidade, COALESCE(e.quantidade_atual, 0),
                            m.consumo_medio_diario, m.prazo_medio_entrega_dias
                     FROM materiais m LEFT JOIN estoque e ON e.material_id = m.id AND e.obra_id = m.obra_id
                     WHERE m.obra_id = ? AND m.ativo = 1 ORDER BY m.descricao
                     """)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery(); BufferedWriter writer = writer(destino)) {
                writer.write("Código;Material;Unidade;Estoque;Consumo/dia;Prazo (dias);Ponto de pedido;Situação");
                writer.newLine();
                while (result.next()) {
                    double stock = result.getDouble(4);
                    double consumption = result.getDouble(5);
                    int lead = result.getInt(6);
                    double point = consumption * lead;
                    double days = consumption <= 0 ? Double.POSITIVE_INFINITY : stock / consumption;
                    String situation = stock > point ? "OK" : (days <= lead ? "PEDIDO IMEDIATO" : "COMPRAR");
                    writeRow(writer, result.getString(1), result.getString(2), result.getString(3), number(stock),
                            number(consumption), Integer.toString(lead), number(point), situation);
                }
            }
        } catch (SQLException | IOException exception) {
            throw new PersistenceException("Não foi possível exportar o planejamento", exception);
        }
    }

    private BufferedWriter writer(Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        return Files.newBufferedWriter(target, StandardCharsets.UTF_8);
    }

    private void writeRow(BufferedWriter writer, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(';');
            writer.write(csv(values[i]));
        }
        writer.newLine();
    }

    private String csv(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }
}
