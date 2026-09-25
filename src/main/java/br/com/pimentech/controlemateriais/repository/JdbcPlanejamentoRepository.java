package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.CronogramaItem;
import br.com.pimentech.controlemateriais.model.EfetivoAlocado;
import br.com.pimentech.controlemateriais.model.Medicao;
import br.com.pimentech.controlemateriais.model.ProducaoTarefa;
import br.com.pimentech.controlemateriais.model.OrcamentoItem;
import br.com.pimentech.controlemateriais.util.SqliteConverters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JdbcPlanejamentoRepository implements PlanejamentoRepository {
    private final DatabaseManager database;

    public JdbcPlanejamentoRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<CronogramaItem> listarCronograma(long obraId) {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM cronograma_itens WHERE obra_id = ? AND ativo = 1 ORDER BY ordem, id")) {
            statement.setLong(1, obraId);
            List<CronogramaItem> items = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) items.add(mapCronograma(result, List.of()));
            }
            if (items.isEmpty()) return items;
            Map<Long, List<Double>> monthly = new HashMap<>();
            Map<Long, List<Double>> executed = new HashMap<>();
            String placeholders = String.join(",", java.util.Collections.nCopies(items.size(), "?"));
            try (PreparedStatement months = connection.prepareStatement("SELECT cronograma_item_id, mes, valor FROM cronograma_meses WHERE cronograma_item_id IN (" + placeholders + ") ORDER BY mes")) {
                for (int index = 0; index < items.size(); index++) months.setLong(index + 1, items.get(index).id());
                try (ResultSet result = months.executeQuery()) {
                    while (result.next()) {
                        List<Double> values = monthly.computeIfAbsent(result.getLong("cronograma_item_id"), key -> new ArrayList<>(java.util.Collections.nCopies(CronogramaItem.MESES, 0d)));
                        values.set(result.getInt("mes") - 1, monthValue(result));
                    }
                }
            }
            try (PreparedStatement months = connection.prepareStatement("SELECT cronograma_item_id, mes, valor FROM cronograma_execucao_meses WHERE cronograma_item_id IN (" + placeholders + ")")) {
                for (int index = 0; index < items.size(); index++) months.setLong(index + 1, items.get(index).id());
                try (ResultSet result = months.executeQuery()) {
                    while (result.next()) {
                        List<Double> values = executed.computeIfAbsent(result.getLong("cronograma_item_id"), key -> new ArrayList<>(java.util.Collections.nCopies(CronogramaItem.MESES, 0d)));
                        values.set(result.getInt("mes") - 1, result.getDouble("valor"));
                    }
                }
            }
            return items.stream().map(item -> withMonthly(item, monthly.get(item.id()), executed.get(item.id()))).toList();
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar o cronograma", exception);
        }
    }

    @Override
    public long inserirCronograma(CronogramaItem item) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO cronograma_itens
                        (obra_id, ordem, codigo, descricao, unidade, quantidade, peso_percentual, inicio_previsto,
                         fim_previsto, percentual_executado, observacao, ativo, created_at, updated_at, meta_fisica_definida)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bindCronograma(statement, item);
                statement.executeUpdate();
                long id = generatedId(statement, "A etapa do cronograma não retornou seu identificador");
                saveMonthly(connection, id, item.valoresMensais());
                return id;
            }
        });
    }

    @Override
    public void atualizarCronograma(CronogramaItem item) {
        database.inTransaction(connection -> {
            List<Double> previous = new ArrayList<>(java.util.Collections.nCopies(CronogramaItem.MESES, 0d));
            try (PreparedStatement check = connection.prepareStatement("SELECT mes, valor FROM cronograma_meses WHERE cronograma_item_id = ?")) {
                check.setLong(1, item.id());
                try (ResultSet result = check.executeQuery()) {
                    while (result.next()) previous.set(result.getInt("mes") - 1, result.getDouble("valor"));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE cronograma_itens SET ordem = ?, codigo = ?, descricao = ?, unidade = ?, quantidade = ?,
                        peso_percentual = ?, inicio_previsto = ?, fim_previsto = ?, percentual_executado = ?,
                        observacao = ?, updated_at = ?, meta_fisica_definida = ? WHERE id = ? AND obra_id = ?
                    """)) {
                statement.setInt(1, item.ordem()); statement.setString(2, item.codigo()); statement.setString(3, item.descricao());
                statement.setString(4, item.unidade()); statement.setDouble(5, item.quantidade()); statement.setDouble(6, item.pesoPercentual());
                statement.setString(7, SqliteConverters.date(item.inicioPrevisto())); statement.setString(8, SqliteConverters.date(item.fimPrevisto()));
                statement.setDouble(9, item.percentualExecutado()); statement.setString(10, item.observacao());
                statement.setString(11, SqliteConverters.instant(item.updatedAt())); statement.setInt(12, item.metaFisicaDefinida() ? 1 : 0);
                statement.setLong(13, item.id()); statement.setLong(14, item.obraId());
                statement.executeUpdate();
                saveMonthly(connection, item.id(), item.valoresMensais());
                if (!previous.equals(item.valoresMensais())) {
                    try (PreparedStatement clear = connection.prepareStatement("UPDATE cronograma_itens SET total_referencia = NULL, percentual_referencia = NULL WHERE id = ? AND obra_id = ?")) {
                        clear.setLong(1, item.id()); clear.setLong(2, item.obraId()); clear.executeUpdate();
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void importarProposta(long obraId, List<CronogramaItem> itens) {
        database.inTransaction(connection -> {
            for (CronogramaItem item : itens) {
                Long id = null;
                try (PreparedStatement lookup = connection.prepareStatement("SELECT id FROM cronograma_itens WHERE obra_id = ? AND codigo = ? AND ativo = 1 ORDER BY id LIMIT 1")) {
                    lookup.setLong(1, obraId); lookup.setString(2, item.codigo());
                    try (ResultSet result = lookup.executeQuery()) { if (result.next()) id = result.getLong(1); }
                }
                if (id == null) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO cronograma_itens (obra_id, ordem, codigo, descricao, unidade, quantidade,
                                peso_percentual, percentual_executado, ativo, created_at, updated_at,
                                total_referencia, percentual_referencia)
                            VALUES (?, ?, ?, ?, 'UN', 0, ?, 0, 1, datetime('now'), datetime('now'), ?, ?)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        insert.setLong(1, obraId); insert.setInt(2, item.ordem()); insert.setString(3, item.codigo());
                        insert.setString(4, item.descricao()); insert.setDouble(5, item.percentualReferencia());
                        insert.setDouble(6, item.totalReferencia()); insert.setDouble(7, item.percentualReferencia());
                        insert.executeUpdate();
                        id = generatedId(insert, "Não foi possível importar o item do cronograma");
                    }
                    saveMonthly(connection, id, item.valoresMensais());
                } else {
                    boolean hasPlan;
                    try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM cronograma_meses WHERE cronograma_item_id = ? AND valor > 0")) {
                        check.setLong(1, id);
                        try (ResultSet result = check.executeQuery()) { hasPlan = result.next() && result.getInt(1) > 0; }
                    }
                    if (hasPlan) continue;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE cronograma_itens SET ordem = ?, descricao = ?, peso_percentual = ?,
                                total_referencia = ?, percentual_referencia = ?,
                                meta_fisica_definida = CASE WHEN quantidade = 1 AND upper(unidade) = 'UN' THEN 0 ELSE meta_fisica_definida END,
                                updated_at = datetime('now')
                            WHERE id = ? AND obra_id = ?
                            """)) {
                        update.setInt(1, item.ordem()); update.setString(2, item.descricao());
                        update.setDouble(3, item.percentualReferencia()); update.setDouble(4, item.totalReferencia());
                        update.setDouble(5, item.percentualReferencia()); update.setLong(6, id); update.setLong(7, obraId);
                        update.executeUpdate();
                    }
                    saveMonthly(connection, id, item.valoresMensais());
                }
            }
            return null;
        });
    }

    @Override
    public void salvarExecucao(long obraId, long itemId, int mes, double valor) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO cronograma_execucao_meses (cronograma_item_id, mes, valor)
                    SELECT id, ?, ? FROM cronograma_itens WHERE id = ? AND obra_id = ? AND ativo = 1
                    ON CONFLICT (cronograma_item_id, mes) DO UPDATE SET valor = excluded.valor
                    """)) {
                statement.setInt(1, mes); statement.setDouble(2, valor); statement.setLong(3, itemId); statement.setLong(4, obraId);
                if (statement.executeUpdate() == 0) throw new SQLException("Serviço do cronograma não encontrado na obra ativa.");
            }
            return null;
        });
    }

    @Override
    public List<ProducaoTarefa> listarProducao(long obraId) {
        try (Connection connection = database.getConnection()) {
            Map<Long, List<EfetivoAlocado>> people = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT a.producao_id, a.funcao, a.pessoas, a.horas_por_pessoa
                    FROM cronograma_efetivo_alocacao a
                    JOIN cronograma_producao p ON p.id = a.producao_id
                    WHERE p.obra_id = ?
                    """)) {
                statement.setLong(1, obraId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) people.computeIfAbsent(result.getLong("producao_id"), key -> new ArrayList<>())
                            .add(new EfetivoAlocado(result.getString("funcao"), result.getInt("pessoas"), result.getDouble("horas_por_pessoa")));
                }
            }
            List<ProducaoTarefa> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM cronograma_producao WHERE obra_id = ? ORDER BY data_producao DESC, id DESC")) {
                statement.setLong(1, obraId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long id = result.getLong("id");
                        rows.add(new ProducaoTarefa(id, result.getLong("obra_id"), result.getLong("cronograma_item_id"),
                                result.getLong("diario_obra_id"), SqliteConverters.localDate(result, "data_producao"),
                                result.getDouble("quantidade_executada"), result.getString("observacao"),
                                people.getOrDefault(id, List.of())));
                    }
                }
            }
            return rows;
        } catch (SQLException exception) { throw new PersistenceException("Não foi possível consultar a produção das tarefas", exception); }
    }

    @Override
    public long salvarProducao(ProducaoTarefa production) {
        return database.inTransaction(connection -> {
            long id;
            if (production.id() == null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO cronograma_producao (obra_id, cronograma_item_id, diario_obra_id, data_producao,
                            quantidade_executada, observacao) VALUES (?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    bindProduction(statement, production);
                    statement.executeUpdate();
                    id = generatedId(statement, "A produção não retornou seu identificador");
                }
            } else {
                id = production.id();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE cronograma_producao SET cronograma_item_id = ?, diario_obra_id = ?, data_producao = ?,
                            quantidade_executada = ?, observacao = ? WHERE id = ? AND obra_id = ?
                        """)) {
                    statement.setLong(1, production.cronogramaItemId()); statement.setLong(2, production.diarioObraId());
                    statement.setString(3, production.data().toString()); statement.setDouble(4, production.quantidadeExecutada());
                    statement.setString(5, production.observacao()); statement.setLong(6, id); statement.setLong(7, production.obraId());
                    if (statement.executeUpdate() == 0) throw new SQLException("Lançamento de produção não encontrado nesta obra.");
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM cronograma_efetivo_alocacao WHERE producao_id = ?")) {
                    delete.setLong(1, id); delete.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO cronograma_efetivo_alocacao (producao_id, funcao, pessoas, horas_por_pessoa) VALUES (?, ?, ?, ?)")) {
                for (EfetivoAlocado person : production.efetivo()) {
                    statement.setLong(1, id); statement.setString(2, person.funcao()); statement.setInt(3, person.pessoas());
                    statement.setDouble(4, person.horasPorPessoa()); statement.addBatch();
                }
                statement.executeBatch();
            }
            return id;
        });
    }

    @Override
    public void excluirProducao(long obraId, long producaoId) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM cronograma_producao WHERE id = ? AND obra_id = ?")) {
                statement.setLong(1, producaoId); statement.setLong(2, obraId); statement.executeUpdate();
            }
            return null;
        });
    }

    private void bindProduction(PreparedStatement statement, ProducaoTarefa production) throws SQLException {
        statement.setLong(1, production.obraId()); statement.setLong(2, production.cronogramaItemId());
        statement.setLong(3, production.diarioObraId()); statement.setString(4, production.data().toString());
        statement.setDouble(5, production.quantidadeExecutada()); statement.setString(6, production.observacao());
    }

    @Override
    public List<OrcamentoItem> listarOrcamento(long obraId) {
        return query("SELECT * FROM orcamento_itens WHERE obra_id = ? AND ativo = 1 ORDER BY codigo, id", obraId,
                this::mapOrcamento);
    }

    @Override
    public long inserirOrcamento(OrcamentoItem item) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO orcamento_itens
                        (obra_id, codigo, descricao, unidade, quantidade, valor_unitario, observacao, ativo, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bindOrcamento(statement, item);
                statement.executeUpdate();
                return generatedId(statement, "O item do orçamento não retornou seu identificador");
            }
        });
    }

    @Override
    public void atualizarOrcamento(OrcamentoItem item) {
        inTransaction("""
                UPDATE orcamento_itens SET codigo = ?, descricao = ?, unidade = ?, quantidade = ?, valor_unitario = ?,
                    observacao = ?, updated_at = ? WHERE id = ? AND obra_id = ?
                """, statement -> {
            statement.setString(1, item.codigo());
            statement.setString(2, item.descricao());
            statement.setString(3, item.unidade());
            statement.setDouble(4, item.quantidade());
            statement.setDouble(5, item.valorUnitario());
            statement.setString(6, item.observacao());
            statement.setString(7, SqliteConverters.instant(item.updatedAt()));
            statement.setLong(8, item.id());
            statement.setLong(9, item.obraId());
        });
    }

    @Override
    public List<Medicao> listarMedicoes(long obraId) {
        return query("SELECT * FROM medicoes_obra WHERE obra_id = ? ORDER BY data_medicao DESC, id DESC", obraId,
                this::mapMedicao);
    }

    @Override
    public long inserirMedicao(Medicao medicao) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medicoes_obra
                        (obra_id, cronograma_item_id, orcamento_item_id, numero, data_medicao, descricao, quantidade,
                         percentual_fisico, valor_medido, observacao, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bindMedicao(statement, medicao);
                statement.executeUpdate();
                return generatedId(statement, "A medição não retornou seu identificador");
            }
        });
    }

    @Override
    public void atualizarMedicao(Medicao medicao) {
        inTransaction("""
                UPDATE medicoes_obra SET cronograma_item_id = ?, orcamento_item_id = ?, numero = ?, data_medicao = ?,
                    descricao = ?, quantidade = ?, percentual_fisico = ?, valor_medido = ?, observacao = ?, updated_at = ?
                WHERE id = ? AND obra_id = ?
                """, statement -> {
            setNullableLong(statement, 1, medicao.cronogramaItemId());
            setNullableLong(statement, 2, medicao.orcamentoItemId());
            statement.setString(3, medicao.numero());
            statement.setString(4, SqliteConverters.date(medicao.data()));
            statement.setString(5, medicao.descricao());
            statement.setDouble(6, medicao.quantidade());
            statement.setDouble(7, medicao.percentualFisico());
            statement.setDouble(8, medicao.valorMedido());
            statement.setString(9, medicao.observacao());
            statement.setString(10, SqliteConverters.instant(medicao.updatedAt()));
            statement.setLong(11, medicao.id());
            statement.setLong(12, medicao.obraId());
        });
    }

    private <T> List<T> query(String sql, long obraId, Mapper<T> mapper) {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, obraId);
            try (ResultSet result = statement.executeQuery()) {
                List<T> values = new ArrayList<>();
                while (result.next()) values.add(mapper.map(result));
                return values;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Não foi possível consultar os dados do planejamento", exception);
        }
    }

    private CronogramaItem mapCronograma(ResultSet result, List<Double> monthly) throws SQLException {
        return new CronogramaItem(result.getLong("id"), result.getLong("obra_id"), result.getInt("ordem"),
                result.getString("codigo"), result.getString("descricao"), result.getString("unidade"),
                result.getDouble("quantidade"), result.getDouble("peso_percentual"),
                SqliteConverters.localDate(result, "inicio_previsto"), SqliteConverters.localDate(result, "fim_previsto"),
                result.getDouble("percentual_executado"), result.getString("observacao"), SqliteConverters.bool(result, "ativo"),
                SqliteConverters.instant(result, "created_at"), SqliteConverters.instant(result, "updated_at"), monthly,
                List.of(), nullableDouble(result, "total_referencia"), nullableDouble(result, "percentual_referencia"),
                result.getInt("meta_fisica_definida") == 1);
    }

    private CronogramaItem withMonthly(CronogramaItem item, List<Double> monthly, List<Double> executed) {
        return new CronogramaItem(item.id(), item.obraId(), item.ordem(), item.codigo(), item.descricao(), item.unidade(), item.quantidade(),
                item.pesoPercentual(), item.inicioPrevisto(), item.fimPrevisto(), item.percentualExecutado(), item.observacao(), item.ativo(),
                item.createdAt(), item.updatedAt(), monthly == null ? List.of() : monthly,
                executed == null ? List.of() : executed, item.totalReferencia(), item.percentualReferencia(), item.metaFisicaDefinida());
    }

    private Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }

    private double monthValue(ResultSet result) throws SQLException {
        return result.getDouble("valor");
    }

    private void saveMonthly(Connection connection, long cronogramaId, List<Double> monthly) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM cronograma_meses WHERE cronograma_item_id = ?")) {
            delete.setLong(1, cronogramaId); delete.executeUpdate();
        }
        if (monthly == null || monthly.isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO cronograma_meses (cronograma_item_id, mes, valor) VALUES (?, ?, ?)")) {
            for (int index = 0; index < Math.min(CronogramaItem.MESES, monthly.size()); index++) {
                double value = monthly.get(index); if (value == 0) continue;
                insert.setLong(1, cronogramaId); insert.setInt(2, index + 1); insert.setDouble(3, value); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private OrcamentoItem mapOrcamento(ResultSet result) throws SQLException {
        return new OrcamentoItem(result.getLong("id"), result.getLong("obra_id"), result.getString("codigo"),
                result.getString("descricao"), result.getString("unidade"), result.getDouble("quantidade"),
                result.getDouble("valor_unitario"), result.getString("observacao"), SqliteConverters.bool(result, "ativo"),
                SqliteConverters.instant(result, "created_at"), SqliteConverters.instant(result, "updated_at"));
    }

    private Medicao mapMedicao(ResultSet result) throws SQLException {
        return new Medicao(result.getLong("id"), result.getLong("obra_id"), nullableLong(result, "cronograma_item_id"),
                nullableLong(result, "orcamento_item_id"), result.getString("numero"), SqliteConverters.localDate(result, "data_medicao"),
                result.getString("descricao"), result.getDouble("quantidade"), result.getDouble("percentual_fisico"),
                result.getDouble("valor_medido"), result.getString("observacao"), SqliteConverters.instant(result, "created_at"),
                SqliteConverters.instant(result, "updated_at"));
    }

    private void bindCronograma(PreparedStatement statement, CronogramaItem item) throws SQLException {
        statement.setLong(1, item.obraId()); statement.setInt(2, item.ordem()); statement.setString(3, item.codigo());
        statement.setString(4, item.descricao()); statement.setString(5, item.unidade()); statement.setDouble(6, item.quantidade());
        statement.setDouble(7, item.pesoPercentual()); statement.setString(8, SqliteConverters.date(item.inicioPrevisto()));
        statement.setString(9, SqliteConverters.date(item.fimPrevisto())); statement.setDouble(10, item.percentualExecutado());
        statement.setString(11, item.observacao()); statement.setString(12, SqliteConverters.instant(item.createdAt()));
        statement.setString(13, SqliteConverters.instant(item.updatedAt())); statement.setInt(14, item.metaFisicaDefinida() ? 1 : 0);
    }

    private void bindOrcamento(PreparedStatement statement, OrcamentoItem item) throws SQLException {
        statement.setLong(1, item.obraId()); statement.setString(2, item.codigo()); statement.setString(3, item.descricao());
        statement.setString(4, item.unidade()); statement.setDouble(5, item.quantidade()); statement.setDouble(6, item.valorUnitario());
        statement.setString(7, item.observacao()); statement.setString(8, SqliteConverters.instant(item.createdAt()));
        statement.setString(9, SqliteConverters.instant(item.updatedAt()));
    }

    private void bindMedicao(PreparedStatement statement, Medicao medicao) throws SQLException {
        statement.setLong(1, medicao.obraId()); setNullableLong(statement, 2, medicao.cronogramaItemId());
        setNullableLong(statement, 3, medicao.orcamentoItemId()); statement.setString(4, medicao.numero());
        statement.setString(5, SqliteConverters.date(medicao.data())); statement.setString(6, medicao.descricao());
        statement.setDouble(7, medicao.quantidade()); statement.setDouble(8, medicao.percentualFisico());
        statement.setDouble(9, medicao.valorMedido()); statement.setString(10, medicao.observacao());
        statement.setString(11, SqliteConverters.instant(medicao.createdAt())); statement.setString(12, SqliteConverters.instant(medicao.updatedAt()));
    }

    private void inTransaction(String sql, StatementBinder binder) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private long generatedId(PreparedStatement statement, String message) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException(message);
            return keys.getLong(1);
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.INTEGER); else statement.setLong(index, value);
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    @FunctionalInterface private interface Mapper<T> { T map(ResultSet result) throws SQLException; }
    @FunctionalInterface private interface StatementBinder { void bind(PreparedStatement statement) throws SQLException; }
}
