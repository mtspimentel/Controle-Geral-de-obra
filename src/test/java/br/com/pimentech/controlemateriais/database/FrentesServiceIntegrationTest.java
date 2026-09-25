package br.com.pimentech.controlemateriais.database;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.exception.PersistenceException;
import br.com.pimentech.controlemateriais.model.*;
import br.com.pimentech.controlemateriais.repository.JdbcFrentesRepository;
import br.com.pimentech.controlemateriais.repository.JdbcMaterialRepository;
import br.com.pimentech.controlemateriais.repository.JdbcObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoDiarioRepository;
import br.com.pimentech.controlemateriais.service.FrentesService;
import br.com.pimentech.controlemateriais.service.PlanejamentoDiarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrentesServiceIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Test
    void atualizaBancoAnteriorPreservandoVinculos(@TempDir Path dir) throws Exception {
        DatabaseManager db = new DatabaseManager(dir); db.initialize();
        long obra = new JdbcObraRepository(db).findActive().orElseThrow().id();
        PlanejamentoDiarioService diario = new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(db), new JdbcMaterialRepository(db));
        FrentesService frentes = new FrentesService(new JdbcFrentesRepository(db), diario);
        AreaObra areaA = diario.salvarArea(obra, null, null, TipoAreaObra.SETOR, "Setor A");
        AreaObra areaB = diario.salvarArea(obra, null, null, TipoAreaObra.SETOR, "Setor B");
        ServicoArea origem = diario.salvarServico(obra, null, areaA.id(), "Origem", "un", 1, DAY, DAY, null);
        ServicoArea mesmoLocal = diario.salvarServico(obra, null, areaA.id(), "Mesmo local", "un", 1, DAY, DAY, null);
        ServicoArea outroLocal = diario.salvarServico(obra, null, areaB.id(), "Outro local", "un", 1, DAY, DAY, null);
        VinculoFrente anterior = frentes.criarVinculo(obra, origem.id(), mesmoLocal.id(), List.of("Área inteira do destino"));
        try (Connection connection = db.getConnection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("DROP TRIGGER ck_vinculo_escopo");
            sql.executeUpdate("""
                    CREATE TRIGGER ck_vinculo_escopo BEFORE INSERT ON vinculos_frente
                    WHEN NOT EXISTS (SELECT 1 FROM servicos_area o JOIN servicos_area d
                        ON d.area_id = o.area_id AND d.obra_id = o.obra_id
                        WHERE o.id = NEW.origem_servico_id AND d.id = NEW.destino_servico_id
                        AND o.obra_id = NEW.obra_id AND o.area_id = NEW.area_id)
                    BEGIN SELECT RAISE(ABORT, 'Vínculo deve usar serviços da mesma área e obra'); END
                    """);
            sql.executeUpdate("UPDATE schema_version SET version = 15");
        }
        db.initialize();
        assertEquals(16, db.getSchemaVersion());
        assertEquals(anterior.id(), frentes.vinculos(obra).getFirst().id());
        assertEquals(1, frentes.trechos(obra).size());
        frentes.criarVinculo(obra, origem.id(), outroLocal.id(), List.of("Área inteira do destino"));
        assertEquals(2, frentes.vinculos(obra).size());
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, outroLocal.id(), DAY));
    }

    @Test
    void vinculaServicosSemElementosEmAreasDiferentes(@TempDir Path dir) {
        DatabaseManager db = new DatabaseManager(dir); db.initialize();
        long obra = new JdbcObraRepository(db).findActive().orElseThrow().id();
        PlanejamentoDiarioService diario = new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(db), new JdbcMaterialRepository(db));
        FrentesService frentes = new FrentesService(new JdbcFrentesRepository(db), diario);
        AreaObra areaA = diario.salvarArea(obra, null, null, TipoAreaObra.SETOR, "Viga baldrame");
        AreaObra areaB = diario.salvarArea(obra, null, null, TipoAreaObra.SETOR, "Banheiros");
        ServicoArea origem = diario.salvarServico(obra, null, areaA.id(), "Instalação de esgoto", "m", 20,
                DAY, DAY.plusDays(2), null);
        ServicoArea destino = diario.salvarServico(obra, null, areaB.id(), "Alvenaria", "m²", 35,
                DAY, DAY.plusDays(3), null);
        assertNull(origem.disciplinaId());
        assertNull(destino.disciplinaId());
        assertEquals(List.of("Área inteira do destino"), frentes.trechosDisponiveis(obra, origem.id(), destino.id()));
        frentes.criarVinculo(obra, origem.id(), destino.id(), frentes.trechosDisponiveis(obra, origem.id(), destino.id()));
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, destino.id(), DAY));
        assertEquals(areaA.id(), frentes.vinculos(obra).getFirst().areaId());
        assertEquals(destino.id(), frentes.vinculos(obra).getFirst().destinoServicoId());
    }

    @Test
    void p41LiberadaNaoLiberaP43NemConclusaoDeProducaoLiberaFrente(@TempDir Path dir) {
        DatabaseManager db = new DatabaseManager(dir); db.initialize();
        long obra = new JdbcObraRepository(db).findActive().orElseThrow().id();
        PlanejamentoDiarioService diario = new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(db), new JdbcMaterialRepository(db));
        FrentesService frentes = new FrentesService(new JdbcFrentesRepository(db), diario);
        AreaObra local = diario.salvarArea(obra, null, null, TipoAreaObra.OUTRA, "Área externa");
        AreaObra fundacao = diario.salvarArea(obra, null, local.id(), TipoAreaObra.SETOR, "Fundação");
        long disciplina = frentes.disciplinas(obra).stream().filter(d -> d.nome().equals("Fundação")).findFirst().orElseThrow().id();
        ServicoArea cravacao = frentes.salvarServico(obra, null, fundacao.id(), disciplina,
                "Cravação das estacas pré-moldadas P41 e P43", "un", 2, DAY, DAY.plusDays(2), 1d,
                "Equipe de estacas", "Inspecionar cada estaca", List.of("P41", "P43"));
        ServicoArea proxima = frentes.salvarServico(obra, null, fundacao.id(), disciplina,
                "Etapa seguinte das estacas P41 e P43", "un", 2, DAY.plusDays(1), DAY.plusDays(5), null,
                "Equipe seguinte", null, List.of("P41", "P43"));
        assertThrows(ValidationException.class, () -> frentes.criarVinculo(obra, cravacao.id(), proxima.id(), List.of("P41")));
        VinculoFrente link = frentes.criarVinculo(obra, cravacao.id(), proxima.id(), List.of("P41", "P43"));
        assertThrows(ValidationException.class, () -> frentes.criarVinculo(obra, proxima.id(), cravacao.id(), List.of("P41", "P43")));
        List<TrechoVinculo> scopes = frentes.trechos(obra);
        long p41 = scopes.stream().filter(s -> s.vinculoId() == link.id() && s.codigo().equals("P41")).findFirst().orElseThrow().id();
        long p43 = scopes.stream().filter(s -> s.vinculoId() == link.id() && s.codigo().equals("P43")).findFirst().orElseThrow().id();
        long e41 = diario.listarElementos(obra).stream().filter(e -> e.servicoId() == cravacao.id() && e.codigo().equals("P41")).findFirst().orElseThrow().id();
        long e43 = diario.listarElementos(obra).stream().filter(e -> e.servicoId() == cravacao.id() && e.codigo().equals("P43")).findFirst().orElseThrow().id();
        var launch = diario.salvarDia(obra, null, cravacao.id(), DAY, 1, 3, 8d,
                "Equipe de estacas", "P41 executada", null, null, null, List.of(e41));
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, proxima.id(), DAY));
        assertEquals(List.of(e41), diario.listarElementosProducao(obra).stream()
                .filter(e -> e.producaoId() == launch.producao().id()).map(ElementoProducao::elementoId).toList());
        diario.salvarDia(obra, null, cravacao.id(), DAY.plusDays(1), 1, 3, 8d,
                "Equipe de estacas", "P43 executada", null, null, null, List.of(e43));
        assertEquals(SituacaoServico.CONCLUIDO, diario.visao(obra, DAY.plusDays(1)).servicos().stream()
                .filter(r -> r.servico().id().equals(cravacao.id())).findFirst().orElseThrow().situacao());
        assertNull(diario.visao(obra, DAY.plusDays(1)).servicos().stream()
                .filter(r -> r.servico().id().equals(cravacao.id())).findFirst().orElseThrow()
                .comparacaoAnteriorPessoaPercentual());
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, proxima.id(), DAY.plusDays(1)));
        frentes.registrarLiberacao(obra, p41, DAY.plusDays(1), true, "Engenheira", "P41 inspecionada");
        var partial = frentes.resumo(obra, DAY.plusDays(1)).getFirst();
        assertEquals(SituacaoFrente.PARCIAL, partial.situacao());
        assertTrue(partial.trechos().stream().filter(s -> s.trecho().id() == p41).findFirst().orElseThrow().liberado());
        assertFalse(partial.trechos().stream().filter(s -> s.trecho().id() == p43).findFirst().orElseThrow().liberado());
        assertEquals(SituacaoFrente.PARCIAL, frentes.estadoEntrada(obra, proxima.id(), DAY.plusDays(1)));
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, proxima.id(), DAY));

        var corrected = diario.salvarDia(obra, launch.producao().id(), cravacao.id(), DAY, 0.5, 3, 8d,
                "Equipe de estacas", "Quantidade corrigida", null, null, null, List.of(e41));
        assertEquals(1.5, corrected.acumulado(), 0.000001);
        assertEquals(2, diario.listarProducoes(obra).size());
        assertEquals(SituacaoFrente.PARCIAL, frentes.estadoEntrada(obra, proxima.id(), DAY.plusDays(1)));
        frentes.registrarLiberacao(obra, p41, DAY.plusDays(2), false, "Engenheira", "Correção pendente");
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, proxima.id(), DAY.plusDays(2)));
        frentes.registrarLiberacao(obra, p41, DAY.plusDays(3), true, "Engenheira", "Correção concluída");
        frentes.registrarLiberacao(obra, p43, DAY.plusDays(3), true, "Engenheira", "P43 inspecionada");
        assertEquals(SituacaoFrente.LIBERADO, frentes.estadoEntrada(obra, proxima.id(), DAY.plusDays(3)));
        assertEquals(4, frentes.historico(obra).size());
        DatabaseManager reopened = new DatabaseManager(dir); reopened.initialize();
        PlanejamentoDiarioService persistedDiario = new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(reopened), new JdbcMaterialRepository(reopened));
        assertEquals(SituacaoFrente.LIBERADO,
                new FrentesService(new JdbcFrentesRepository(reopened), persistedDiario).estadoEntrada(obra, proxima.id(), DAY.plusDays(3)));
    }

    @Test
    void mostraDestinoDeOutraAreaSemLiberarLocalNaoVinculado(@TempDir Path dir) {
        DatabaseManager db = new DatabaseManager(dir); db.initialize();
        long obra = new JdbcObraRepository(db).findActive().orElseThrow().id();
        PlanejamentoDiarioService diario = new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(db), new JdbcMaterialRepository(db));
        FrentesService frentes = new FrentesService(new JdbcFrentesRepository(db), diario);
        long disciplina = frentes.disciplinas(obra).getFirst().id();
        AreaObra sala1 = diario.salvarArea(obra, null, null, TipoAreaObra.AMBIENTE, "Sala 01");
        AreaObra sala2 = diario.salvarArea(obra, null, null, TipoAreaObra.AMBIENTE, "Sala 02");
        AreaObra sala3 = diario.salvarArea(obra, null, null, TipoAreaObra.AMBIENTE, "Sala 03");
        ServicoArea origem = frentes.salvarServico(obra, null, sala1.id(), disciplina, "Alvenaria", "m²", 35,
                DAY, DAY.plusDays(2), null, null, null, List.of("Parede 01"));
        ServicoArea outroLocal = frentes.salvarServico(obra, null, sala2.id(), disciplina, "Elétrica", "m", 15,
                DAY, DAY.plusDays(3), null, null, null, List.of("Parede 01"));
        ServicoArea semVinculo = frentes.salvarServico(obra, null, sala3.id(), disciplina, "Hidráulica", "m", 12,
                DAY, DAY.plusDays(3), null, null, null, List.of("Parede 01"));
        assertTrue(frentes.destinosPossiveis(obra, origem.id()).stream().anyMatch(s -> s.id().equals(outroLocal.id())));
        assertEquals(2, frentes.destinosPossiveis(obra, origem.id()).size());
        VinculoFrente criado = frentes.criarVinculo(obra, origem.id(), outroLocal.id(), List.of("Parede 01"));
        assertEquals(sala1.id(), criado.areaId());
        assertEquals(SituacaoFrente.BLOQUEADO, frentes.estadoEntrada(obra, outroLocal.id(), DAY));
        assertNull(frentes.estadoEntrada(obra, semVinculo.id(), DAY));
        long trecho = frentes.trechos(obra).getFirst().id();
        frentes.registrarLiberacao(obra, trecho, DAY, true, "Responsável", "Sala 02 vistoriada");
        assertEquals(SituacaoFrente.LIBERADO, frentes.estadoEntrada(obra, outroLocal.id(), DAY));
        assertNull(frentes.estadoEntrada(obra, semVinculo.id(), DAY));
        long wrongElement = diario.listarElementos(obra).stream().filter(e -> e.servicoId() == outroLocal.id()).findFirst().orElseThrow().id();
        assertThrows(ValidationException.class, () -> diario.salvarDia(obra, null, origem.id(), DAY, 3, 2, null,
                null, null, null, null, null, List.of(wrongElement)));
        assertThrows(PersistenceException.class, () -> new JdbcPlanejamentoDiarioRepository(db).salvarDia(
                new ProducaoDiaria(null, obra, origem.id(), DAY, 3, 2, null, null, null), null, List.of(wrongElement)));
        assertThrows(PersistenceException.class, () -> new JdbcFrentesRepository(db).criarVinculo(
                new VinculoFrente(null, obra, origem.id(), semVinculo.id(), sala2.id()),
                List.of(new TrechoVinculo(null, 0, "Parede 01", null, null))));
        assertTrue(diario.listarProducoes(obra).isEmpty());
        assertEquals(1, frentes.vinculos(obra).size());
    }
}
