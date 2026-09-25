package br.com.pimentech.controlemateriais.database;

import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.*;
import br.com.pimentech.controlemateriais.repository.JdbcMaterialRepository;
import br.com.pimentech.controlemateriais.repository.JdbcObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoDiarioRepository;
import br.com.pimentech.controlemateriais.service.MaterialService;
import br.com.pimentech.controlemateriais.service.ObraService;
import br.com.pimentech.controlemateriais.service.PlanejamentoDiarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanejamentoDiarioIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Test
    void atualizaBancoAnteriorSemPerderMaterial(@TempDir Path directory) throws Exception {
        DatabaseManager database = open(directory);
        long obraId = obraId(database);
        Material material = new MaterialService(new JdbcMaterialRepository(database)).criar(obraId,
                "PRESERVADO", "Material anterior", "un", "Teste", "", 0, 0, 0, 0, "");
        // Reconstitui em diretório temporário o estado do esquema imediatamente anterior.
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TRIGGER ck_disciplina_servico_obra");
            statement.executeUpdate("DROP TRIGGER ck_area_servico_vinculado");
            statement.executeUpdate("DROP TABLE liberacoes_trecho");
            statement.executeUpdate("DROP TABLE vinculo_trechos");
            statement.executeUpdate("DROP TABLE vinculos_frente");
            statement.executeUpdate("DROP TABLE producao_elementos");
            statement.executeUpdate("DROP TABLE elementos_servico");
            statement.executeUpdate("ALTER TABLE servicos_area DROP COLUMN disciplina_id");
            statement.executeUpdate("ALTER TABLE servicos_area DROP COLUMN equipe_responsavel");
            statement.executeUpdate("ALTER TABLE servicos_area DROP COLUMN observacoes");
            statement.executeUpdate("DROP TABLE disciplinas_obra");
            statement.executeUpdate("UPDATE schema_version SET version = 14");
        }
        DatabaseManager upgraded = open(directory);
        assertEquals(16, upgraded.getSchemaVersion());
        assertEquals("Material anterior", new JdbcMaterialRepository(upgraded).findById(material.id()).orElseThrow().descricao());
        assertTrue(service(upgraded).listarAreas(obraId).isEmpty());
    }

    @Test
    void salvaCorrigeEReabreSemDuplicarAcumulado(@TempDir Path directory) {
        DatabaseManager database = open(directory);
        long obraId = obraId(database);
        PlanejamentoDiarioService service = service(database);
        AreaObra area = service.salvarArea(obraId, null, null, TipoAreaObra.BLOCO, "Bloco A");
        ServicoArea item = service.salvarServico(obraId, null, area.id(), "Alvenaria", "m²", 20,
                DAY, DAY.plusDays(3), 8d);
        var first = service.salvarDia(obraId, null, item.id(), DAY, 10.125, 2, 8d, "Equipe 1", null, null, null, null);
        service.salvarDia(obraId, null, item.id(), DAY.plusDays(1), 7.5, 3, null, "Equipe 2", null, null, null, null);
        var correction = service.salvarDia(obraId, first.producao().id(), item.id(), DAY, 4.125, 2, 8d,
                "Equipe 1", "Conferido", null, null, null);
        assertEquals(11.625, correction.acumulado(), 0.000001);
        assertEquals(2, service.listarProducoes(obraId).size());
        assertThrows(ValidationException.class, () -> service.salvarDia(obraId, null, item.id(), DAY, 1, 1, null, null, null, null, null, null));
        assertThrows(ValidationException.class, () -> service.salvarDia(obraId, null, item.id(), DAY.plusDays(2), -1, 1, null, null, null, null, null, null));

        DatabaseManager reopened = open(directory);
        assertEquals(16, reopened.getSchemaVersion());
        var row = service(reopened).visao(obraId, DAY.plusDays(1)).servicos().getFirst();
        assertEquals(7.5, row.feitoNoDia(), 0.000001);
        assertEquals(11.625, row.acumulado(), 0.000001);
        assertEquals(8.375, row.saldo(), 0.000001);
        assertEquals(58.125, row.percentual(), 0.000001);
        assertEquals(SituacaoServico.EM_ANDAMENTO, row.situacao());
        assertEquals("Alvenaria", service(reopened).listarServicos(obraId).getFirst().descricao());
    }

    @Test
    void calculaProdutividadeComparacoesEAlertasSemInferirCausa(@TempDir Path directory) {
        DatabaseManager database = open(directory);
        long obraId = obraId(database);
        PlanejamentoDiarioService service = service(database);
        AreaObra area = service.salvarArea(obraId, null, null, TipoAreaObra.SETOR, "Setor 1");
        ServicoArea item = service.salvarServico(obraId, null, area.id(), "Concretagem", "m³", 100,
                DAY, DAY.plusDays(1), 10d);
        assertTrue(service.visao(obraId, DAY).servicos().getFirst().semLancamento());
        assertFalse(service.visao(obraId, DAY).servicos().getFirst().semAvancoRegistrado());
        service.salvarDia(obraId, null, item.id(), DAY, 12, 3, 8d, null, null, null, null, null);
        ResumoServicoDia first = service.visao(obraId, DAY).servicos().getFirst();
        assertEquals(4, first.produtividadePessoaDia(), 0.000001);
        assertEquals(0.5, first.produtividadeHomemHora(), 0.000001);
        assertEquals(20, first.comparacaoMetaPercentual(), 0.000001);
        assertNull(first.comparacaoAnteriorPessoaPercentual());
        service.salvarDia(obraId, null, item.id(), DAY.plusDays(1), 16, 4, 8d, null, null, null, null, null);
        ResumoServicoDia second = service.visao(obraId, DAY.plusDays(1)).servicos().getFirst();
        assertEquals(0, second.comparacaoAnteriorPessoaPercentual(), 0.000001);
        assertEquals(0, second.comparacaoAnteriorHoraPercentual(), 0.000001);
        assertEquals(60, second.comparacaoMetaPercentual(), 0.000001);
        assertFalse(second.atrasado());
        ResumoServicoDia late = service.visao(obraId, DAY.plusDays(2)).servicos().getFirst();
        assertTrue(late.atrasado());
        assertFalse(late.semAvancoRegistrado());
        service.salvarOcorrencia(obraId, null, item.id(), DAY.plusDays(2), TipoOcorrenciaServico.PARALISACAO,
                "Paralisação informada pelo encarregado", null, null);
        assertEquals(SituacaoServico.PARALISADO, service.visao(obraId, DAY.plusDays(2)).servicos().getFirst().situacao());
        long occurrenceId = service.listarOcorrencias(obraId).getFirst().id();
        service.resolverOcorrencia(obraId, occurrenceId, DAY.plusDays(3));
        assertEquals(SituacaoServico.EM_ANDAMENTO, service.visao(obraId, DAY.plusDays(3)).servicos().getFirst().situacao());
        var over = service.salvarDia(obraId, null, item.id(), DAY.plusDays(3), 80, 5, null, null, null, null, null, null);
        assertTrue(over.ultrapassouPrevisto());
        ResumoServicoDia completed = service.visao(obraId, DAY.plusDays(3)).servicos().getFirst();
        assertEquals(SituacaoServico.CONCLUIDO, completed.situacao());
        assertTrue(completed.ultrapassouPrevisto());
        assertFalse(completed.atrasado());
        assertNull(completed.produtividadeHomemHora());
        assertNull(completed.comparacaoAnteriorHoraPercentual());
    }

    @Test
    void vinculaSomenteMaterialDaObraETransacaoReverteFalha(@TempDir Path directory) {
        DatabaseManager database = open(directory);
        long obraId = obraId(database);
        PlanejamentoDiarioService service = service(database);
        AreaObra area = service.salvarArea(obraId, null, null, TipoAreaObra.AMBIENTE, "Sala 1");
        ServicoArea item = service.salvarServico(obraId, null, area.id(), "Piso", "m²", 30, null, null, null);
        MaterialService materialService = new MaterialService(new JdbcMaterialRepository(database));
        Material material = materialService.criar(obraId, "PISO-01", "Argamassa", "sc", "Acabamento", "", 0, 0, 0, 0, "");
        Obra another = new ObraService(new JdbcObraRepository(database)).criar("Outra obra", "OUT-001", "Rua B", "Equipe",
                DAY, DAY.plusMonths(1), null);
        Material other = materialService.criar(another.id(), "PISO-02", "Cimento", "sc", "Acabamento", "", 0, 0, 0, 0, "");
        assertThrows(ValidationException.class, () -> service.salvarOcorrencia(obraId, null, item.id(), DAY,
                TipoOcorrenciaServico.FALTA_MATERIAL, "Faltaram sacos", other.id(), null));
        service.salvarOcorrencia(obraId, null, item.id(), DAY, TipoOcorrenciaServico.FALTA_MATERIAL,
                "Faltaram sacos", material.id(), null);
        assertEquals(material.id(), service.listarOcorrencias(obraId).getFirst().materialId());
        assertEquals("Faltaram sacos", service.visao(obraId, DAY).pendencias().getFirst().descricao().split(": ")[1]);

        JdbcPlanejamentoDiarioRepository repository = new JdbcPlanejamentoDiarioRepository(database);
        ProducaoDiaria production = new ProducaoDiaria(null, obraId, item.id(), DAY, 3, 1, null, null, null);
        OcorrenciaServico broken = new OcorrenciaServico(null, obraId, item.id(), DAY,
                TipoOcorrenciaServico.FALTA_MATERIAL, "Teste de atomicidade", Long.MAX_VALUE, null);
        assertThrows(PersistenceException.class, () -> repository.salvarDia(production, broken));
        assertTrue(repository.listarProducoes(obraId).isEmpty());
        assertFalse(service.visao(obraId, DAY.plusDays(10)).servicos().getFirst().atrasado());
        assertFalse(service.visao(obraId, DAY).servicos().getFirst().semLancamento());
        service.salvarDia(obraId, null, item.id(), DAY, 0, 0, null, null, "Frente sem execução", null, null, null);
        assertTrue(service.visao(obraId, DAY).servicos().getFirst().semAvancoRegistrado());
    }

    private static DatabaseManager open(Path directory) {
        DatabaseManager database = new DatabaseManager(directory);
        database.initialize();
        return database;
    }
    private static long obraId(DatabaseManager database) { return new JdbcObraRepository(database).findActive().orElseThrow().id(); }
    private static PlanejamentoDiarioService service(DatabaseManager database) {
        return new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(database), new JdbcMaterialRepository(database));
    }
}
