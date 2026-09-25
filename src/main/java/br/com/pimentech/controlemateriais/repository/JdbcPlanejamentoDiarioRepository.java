package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.AreaObra;
import br.com.pimentech.controlemateriais.model.ElementoProducao;
import br.com.pimentech.controlemateriais.model.ElementoServico;
import br.com.pimentech.controlemateriais.model.OcorrenciaServico;
import br.com.pimentech.controlemateriais.model.ProducaoDiaria;
import br.com.pimentech.controlemateriais.model.ServicoArea;
import br.com.pimentech.controlemateriais.model.TipoAreaObra;
import br.com.pimentech.controlemateriais.model.TipoOcorrenciaServico;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcPlanejamentoDiarioRepository implements PlanejamentoDiarioRepository {
    private final DatabaseManager database;

    public JdbcPlanejamentoDiarioRepository(DatabaseManager database) { this.database = database; }

    @Override
    public List<AreaObra> listarAreas(long obraId) {
        return query("SELECT * FROM areas_obra WHERE obra_id = ? AND ativo = 1 ORDER BY tipo, nome, id", obraId,
                row -> new AreaObra(row.getLong("id"), row.getLong("obra_id"), nullableLong(row, "area_pai_id"),
                        TipoAreaObra.valueOf(row.getString("tipo")), row.getString("nome"), row.getInt("ativo") == 1));
    }

    @Override
    public long salvarArea(AreaObra area) {
        return database.inTransaction(connection -> {
            if (area.id() == null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO areas_obra (obra_id, area_pai_id, tipo, nome) VALUES (?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, area.obraId()); setNullableLong(statement, 2, area.areaPaiId());
                    statement.setString(3, area.tipo().name()); statement.setString(4, area.nome());
                    statement.executeUpdate(); return generatedId(statement);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE areas_obra SET area_pai_id = ?, tipo = ?, nome = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND obra_id = ? AND ativo = 1
                    """)) {
                setNullableLong(statement, 1, area.areaPaiId()); statement.setString(2, area.tipo().name());
                statement.setString(3, area.nome()); statement.setLong(4, area.id()); statement.setLong(5, area.obraId());
                requireUpdated(statement);
                return area.id();
            }
        });
    }

    @Override
    public List<ServicoArea> listarServicos(long obraId) {
        return query("SELECT * FROM servicos_area WHERE obra_id = ? AND ativo = 1 ORDER BY area_id, descricao, id", obraId,
                row -> new ServicoArea(row.getLong("id"), row.getLong("obra_id"), row.getLong("area_id"),
                        row.getString("descricao"), row.getString("unidade"), row.getDouble("quantidade_prevista"),
                        SqliteConverters.localDate(row, "inicio_previsto"), SqliteConverters.localDate(row, "fim_previsto"),
                        nullableDouble(row, "meta_diaria"), row.getInt("ativo") == 1,
                        nullableLong(row, "disciplina_id"), row.getString("equipe_responsavel"), row.getString("observacoes")));
    }

    @Override
    public long salvarServico(ServicoArea servico) {
        return database.inTransaction(connection -> {
            if (servico.id() == null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO servicos_area (obra_id, area_id, descricao, unidade, quantidade_prevista,
                            inicio_previsto, fim_previsto, meta_diaria) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    bindServico(statement, servico, false);
                    statement.executeUpdate(); return generatedId(statement);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE servicos_area SET area_id = ?, descricao = ?, unidade = ?, quantidade_prevista = ?,
                        inicio_previsto = ?, fim_previsto = ?, meta_diaria = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND obra_id = ? AND ativo = 1
                    """)) {
                bindServico(statement, servico, true);
                requireUpdated(statement);
                return servico.id();
            }
        });
    }

    @Override
    public List<ProducaoDiaria> listarProducoes(long obraId) {
        return query("SELECT * FROM producao_diaria_servico WHERE obra_id = ? ORDER BY data_producao DESC, id DESC", obraId,
                row -> new ProducaoDiaria(row.getLong("id"), row.getLong("obra_id"), row.getLong("servico_id"),
                        SqliteConverters.localDate(row, "data_producao"), row.getDouble("quantidade"),
                        row.getInt("trabalhadores"), nullableDouble(row, "horas_por_trabalhador"),
                        row.getString("equipe"), row.getString("observacao")));
    }

    @Override public List<ElementoServico> listarElementos(long obraId) {
        return query("SELECT * FROM elementos_servico WHERE obra_id = ? AND ativo = 1 ORDER BY servico_id, codigo", obraId,
                row -> new ElementoServico(row.getLong("id"), row.getLong("obra_id"), row.getLong("servico_id"), row.getString("codigo")));
    }

    @Override public List<ElementoProducao> listarElementosProducao(long obraId) {
        return query("""
                SELECT pe.producao_id, pe.elemento_id FROM producao_elementos pe
                JOIN producao_diaria_servico p ON p.id = pe.producao_id WHERE p.obra_id = ?
                """, obraId, row -> new ElementoProducao(row.getLong("producao_id"), row.getLong("elemento_id")));
    }

    @Override
    public List<OcorrenciaServico> listarOcorrencias(long obraId) {
        return query("SELECT * FROM ocorrencias_servico WHERE obra_id = ? ORDER BY data_ocorrencia DESC, id DESC", obraId,
                row -> new OcorrenciaServico(row.getLong("id"), row.getLong("obra_id"), row.getLong("servico_id"),
                        SqliteConverters.localDate(row, "data_ocorrencia"), TipoOcorrenciaServico.valueOf(row.getString("tipo")),
                        row.getString("descricao"), nullableLong(row, "material_id"), SqliteConverters.localDate(row, "resolvida_em")));
    }

    @Override
    public long salvarDia(ProducaoDiaria producao, OcorrenciaServico ocorrencia, List<Long> elementos) {
        return database.inTransaction(connection -> {
            long id = saveProduction(connection, producao);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM producao_elementos WHERE producao_id = ?")) {
                delete.setLong(1, id); delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO producao_elementos(producao_id, elemento_id) VALUES (?, ?)")) {
                for (Long elementoId : elementos) {
                    insert.setLong(1, id); insert.setLong(2, elementoId); insert.addBatch();
                }
                insert.executeBatch();
            }
            if (ocorrencia != null) saveOccurrence(connection, ocorrencia);
            return id;
        });
    }

    @Override
    public long salvarOcorrencia(OcorrenciaServico ocorrencia) {
        return database.inTransaction(connection -> saveOccurrence(connection, ocorrencia));
    }

    @Override
    public void resolverOcorrencia(long obraId, long ocorrenciaId, LocalDate dataResolucao) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE ocorrencias_servico SET resolvida_em = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND obra_id = ?
                    """)) {
                statement.setString(1, SqliteConverters.date(dataResolucao)); statement.setLong(2, ocorrenciaId);
                statement.setLong(3, obraId); requireUpdated(statement);
            }
            return null;
        });
    }

    private long saveProduction(Connection connection, ProducaoDiaria production) throws SQLException {
        if (production.id() == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO producao_diaria_servico (obra_id, servico_id, data_producao, quantidade,
                        trabalhadores, horas_por_trabalhador, equipe, observacao) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, production.obraId()); statement.setLong(2, production.servicoId());
                statement.setString(3, SqliteConverters.date(production.data())); statement.setDouble(4, production.quantidade());
                statement.setInt(5, production.trabalhadores()); setNullableDouble(statement, 6, production.horasPorTrabalhador());
                statement.setString(7, production.equipe()); statement.setString(8, production.observacao());
                statement.executeUpdate(); return generatedId(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE producao_diaria_servico SET servico_id = ?, data_producao = ?, quantidade = ?, trabalhadores = ?,
                    horas_por_trabalhador = ?, equipe = ?, observacao = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND obra_id = ?
                """)) {
            statement.setLong(1, production.servicoId()); statement.setString(2, SqliteConverters.date(production.data()));
            statement.setDouble(3, production.quantidade()); statement.setInt(4, production.trabalhadores());
            setNullableDouble(statement, 5, production.horasPorTrabalhador()); statement.setString(6, production.equipe());
            statement.setString(7, production.observacao()); statement.setLong(8, production.id()); statement.setLong(9, production.obraId());
            requireUpdated(statement);
            return production.id();
        }
    }

    private long saveOccurrence(Connection connection, OcorrenciaServico occurrence) throws SQLException {
        if (occurrence.id() == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ocorrencias_servico (obra_id, servico_id, data_ocorrencia, tipo, descricao, material_id, resolvida_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, occurrence.obraId()); statement.setLong(2, occurrence.servicoId());
                statement.setString(3, SqliteConverters.date(occurrence.data())); statement.setString(4, occurrence.tipo().name());
                statement.setString(5, occurrence.descricao()); setNullableLong(statement, 6, occurrence.materialId());
                statement.setString(7, SqliteConverters.date(occurrence.resolvidaEm()));
                statement.executeUpdate(); return generatedId(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE ocorrencias_servico SET servico_id = ?, data_ocorrencia = ?, tipo = ?, descricao = ?, material_id = ?,
                    resolvida_em = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND obra_id = ?
                """)) {
            statement.setLong(1, occurrence.servicoId()); statement.setString(2, SqliteConverters.date(occurrence.data()));
            statement.setString(3, occurrence.tipo().name()); statement.setString(4, occurrence.descricao());
            setNullableLong(statement, 5, occurrence.materialId()); statement.setString(6, SqliteConverters.date(occurrence.resolvidaEm()));
            statement.setLong(7, occurrence.id()); statement.setLong(8, occurrence.obraId()); requireUpdated(statement);
            return occurrence.id();
        }
    }

    private void bindServico(PreparedStatement statement, ServicoArea service, boolean update) throws SQLException {
        int index = 1;
        if (!update) statement.setLong(index++, service.obraId());
        statement.setLong(index++, service.areaId()); statement.setString(index++, service.descricao());
        statement.setString(index++, service.unidade()); statement.setDouble(index++, service.quantidadePrevista());
        statement.setString(index++, SqliteConverters.date(service.inicioPrevisto()));
        statement.setString(index++, SqliteConverters.date(service.fimPrevisto()));
        setNullableDouble(statement, index++, service.metaDiaria());
        if (update) { statement.setLong(index++, service.id()); statement.setLong(index, service.obraId()); }
    }

    private <T> List<T> query(String sql, long obraId, Mapper<T> mapper) {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return rows;
            }
        } catch (SQLException exception) { throw new PersistenceException("Não foi possível consultar o planejamento diário", exception); }
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("O registro salvo não retornou seu identificador.");
            return keys.getLong(1);
        }
    }
    private void requireUpdated(PreparedStatement statement) throws SQLException {
        if (statement.executeUpdate() == 0) throw new SQLException("Registro não encontrado na obra ativa.");
    }
    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setLong(index, value);
    }
    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) statement.setNull(index, Types.REAL); else statement.setDouble(index, value);
    }
    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column); return result.wasNull() ? null : value;
    }
    private static Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column); return result.wasNull() ? null : value;
    }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet row) throws SQLException; }
}
