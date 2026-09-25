package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.*;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class JdbcFrentesRepository implements FrentesRepository {
    private final DatabaseManager database;
    public JdbcFrentesRepository(DatabaseManager database) { this.database = database; }

    @Override public List<DisciplinaObra> disciplinas(long obraId) {
        return query("SELECT * FROM disciplinas_obra WHERE obra_id = ? AND ativo = 1 ORDER BY nome", obraId,
                r -> new DisciplinaObra(r.getLong("id"), r.getLong("obra_id"), r.getString("nome")));
    }
    @Override public void criarDisciplinasPadrao(long obraId, List<String> nomes) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO disciplinas_obra (obra_id, nome) VALUES (?, ?)")) {
                for (String nome : nomes) {
                    statement.setLong(1, obraId); statement.setString(2, nome); statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }
    @Override public long criarDisciplina(long obraId, String nome) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO disciplinas_obra (obra_id, nome) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, obraId); statement.setString(2, nome); statement.executeUpdate(); return generatedId(statement);
            }
        });
    }
    @Override public void atualizarDetalhesServico(long obraId, long servicoId, long disciplinaId, String equipe,
                                                   String observacoes, List<String> novosElementos) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE servicos_area SET disciplina_id = ?, equipe_responsavel = ?, observacoes = ?,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ? AND obra_id = ? AND ativo = 1
                    """)) {
                statement.setLong(1, disciplinaId); statement.setString(2, equipe); statement.setString(3, observacoes);
                statement.setLong(4, servicoId); statement.setLong(5, obraId);
                if (statement.executeUpdate() != 1) throw new SQLException("Serviço não encontrado nesta obra.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO elementos_servico (obra_id, servico_id, codigo) VALUES (?, ?, ?)")) {
                for (String codigo : novosElementos) {
                    statement.setLong(1, obraId); statement.setLong(2, servicoId); statement.setString(3, codigo);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }
    @Override public List<VinculoFrente> vinculos(long obraId) {
        return query("SELECT * FROM vinculos_frente WHERE obra_id = ? ORDER BY id", obraId,
                r -> new VinculoFrente(r.getLong("id"), r.getLong("obra_id"), r.getLong("origem_servico_id"),
                        r.getLong("destino_servico_id"), r.getLong("area_id")));
    }
    @Override public List<TrechoVinculo> trechos(long obraId) {
        return query("""
                SELECT t.* FROM vinculo_trechos t JOIN vinculos_frente v ON v.id = t.vinculo_id
                WHERE v.obra_id = ? ORDER BY t.vinculo_id, t.codigo
                """, obraId, r -> new TrechoVinculo(r.getLong("id"), r.getLong("vinculo_id"), r.getString("codigo"),
                nullableLong(r, "origem_elemento_id"), nullableLong(r, "destino_elemento_id")));
    }
    @Override public List<LiberacaoTrecho> liberacoes(long obraId) {
        return query("""
                SELECT l.* FROM liberacoes_trecho l JOIN vinculo_trechos t ON t.id = l.trecho_id
                JOIN vinculos_frente v ON v.id = t.vinculo_id WHERE v.obra_id = ?
                ORDER BY l.data_liberacao, l.id
                """, obraId, r -> new LiberacaoTrecho(r.getLong("id"), r.getLong("trecho_id"),
                SqliteConverters.localDate(r, "data_liberacao"), "LIBERADO".equals(r.getString("situacao")),
                r.getString("responsavel"), r.getString("observacao")));
    }
    @Override public long criarVinculo(VinculoFrente vinculo, List<TrechoVinculo> trechos) {
        return database.inTransaction(connection -> {
            long id;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vinculos_frente (obra_id, origem_servico_id, destino_servico_id, area_id)
                    VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, vinculo.obraId()); statement.setLong(2, vinculo.origemServicoId());
                statement.setLong(3, vinculo.destinoServicoId()); statement.setLong(4, vinculo.areaId());
                statement.executeUpdate(); id = generatedId(statement);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vinculo_trechos (vinculo_id, codigo, origem_elemento_id, destino_elemento_id)
                    VALUES (?, ?, ?, ?)
                    """)) {
                for (TrechoVinculo trecho : trechos) {
                    statement.setLong(1, id); statement.setString(2, trecho.codigo());
                    setNullableLong(statement, 3, trecho.origemElementoId());
                    setNullableLong(statement, 4, trecho.destinoElementoId()); statement.addBatch();
                }
                statement.executeBatch();
            }
            return id;
        });
    }
    @Override public long registrarLiberacao(LiberacaoTrecho liberacao) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO liberacoes_trecho (trecho_id, data_liberacao, situacao, responsavel, observacao)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, liberacao.trechoId());
                statement.setString(2, SqliteConverters.date(liberacao.data()));
                statement.setString(3, liberacao.liberado() ? "LIBERADO" : "BLOQUEADO");
                statement.setString(4, liberacao.responsavel()); statement.setString(5, liberacao.observacao());
                statement.executeUpdate(); return generatedId(statement);
            }
        });
    }

    private <T> List<T> query(String sql, long obraId, Mapper<T> mapper) {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>(); while (result.next()) rows.add(mapper.map(result)); return rows;
            }
        } catch (SQLException ex) { throw new PersistenceException("Não foi possível consultar frentes da obra", ex); }
    }
    private static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("O registro não retornou identificador.");
            return keys.getLong(1);
        }
    }
    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column); return result.wasNull() ? null : value;
    }
    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setLong(index, value);
    }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet row) throws SQLException; }
}
