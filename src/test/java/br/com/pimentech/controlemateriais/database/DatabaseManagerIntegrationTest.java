package br.com.pimentech.controlemateriais.database;

import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.MaterialTipo;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.EfetivoAlocado;
import br.com.pimentech.controlemateriais.model.Prioridade;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.model.RequisicaoItem;
import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.model.PedidoItem;
import br.com.pimentech.controlemateriais.dto.EntregaInput;
import br.com.pimentech.controlemateriais.dto.EntregaItemInput;
import br.com.pimentech.controlemateriais.dto.TrocaInput;
import br.com.pimentech.controlemateriais.repository.JdbcFornecedorRepository;
import br.com.pimentech.controlemateriais.repository.JdbcEntregaRepository;
import br.com.pimentech.controlemateriais.repository.JdbcDiarioObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcEstoqueRepository;
import br.com.pimentech.controlemateriais.repository.JdbcMaterialRepository;
import br.com.pimentech.controlemateriais.repository.JdbcObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPedidoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcRequisicaoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcTrocaRepository;
import br.com.pimentech.controlemateriais.service.EntregaService;
import br.com.pimentech.controlemateriais.service.DiarioObraService;
import br.com.pimentech.controlemateriais.service.AlmoxarifadoService;
import br.com.pimentech.controlemateriais.service.TrocaService;
import br.com.pimentech.controlemateriais.service.BackupService;
import br.com.pimentech.controlemateriais.service.FolhaPedidoService;
import br.com.pimentech.controlemateriais.service.MaterialService;
import br.com.pimentech.controlemateriais.service.ObraService;
import br.com.pimentech.controlemateriais.service.PedidoService;
import br.com.pimentech.controlemateriais.service.PlanejamentoService;
import br.com.pimentech.controlemateriais.service.RequisicaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseManagerIntegrationTest {

    @Test
    void criaBancoAplicaMigrationsESalvaDadosEntreExecucoes(@TempDir Path temporaryDirectory) {
        DatabaseManager firstDatabase = new DatabaseManager(temporaryDirectory);
        firstDatabase.initialize();

        assertTrue(Files.exists(temporaryDirectory.resolve("data").resolve("obra.db")));
        assertEquals(16, firstDatabase.getSchemaVersion());

        JdbcObraRepository obraRepository = new JdbcObraRepository(firstDatabase);
        ObraService obraService = new ObraService(obraRepository);
        Obra obra = obraService.criar("Obra de teste", "TEST-001", "Rua A", "Engenharia",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 30), null);

        JdbcMaterialRepository materialRepository = new JdbcMaterialRepository(firstDatabase);
        MaterialService materialService = new MaterialService(materialRepository);
        materialService.criar(obra.id(), "MAT-TEST", "Material persistido", "UN", "Teste", "", 10, 2, 5, 25, "");

        DatabaseManager secondDatabase = new DatabaseManager(temporaryDirectory);
        secondDatabase.initialize();
        Obra persistedWork = new JdbcObraRepository(secondDatabase).findById(obra.id()).orElseThrow();
        Material persistedMaterial = new JdbcMaterialRepository(secondDatabase)
                .findByObraId(obra.id()).stream()
                .filter(item -> item.codigo().equals("MAT-TEST"))
                .findFirst()
                .orElseThrow();

        assertEquals("Obra de teste", persistedWork.nome());
        assertEquals("Material persistido", persistedMaterial.descricao());
        assertEquals(25, persistedMaterial.estoqueAtual());
    }

    @Test
    void criaRequisicaoEConverteParaPedidoDentroDoFluxoPersistido(@TempDir Path temporaryDirectory) {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        Material material = new JdbcMaterialRepository(database).findByObraId(obra.id()).getFirst();
        var fornecedor = new JdbcFornecedorRepository(database).findAll().getFirst();

        RequisicaoService requisicaoService = new RequisicaoService(new JdbcRequisicaoRepository(database));
        Requisicao requisicao = requisicaoService.criar(obra.id(), obra.nome(), "Engenharia", LocalDate.now(),
                Prioridade.ALTA, "Compra urgente", java.util.List.of(
                        new RequisicaoItem(null, material.id(), material.descricao(), material.unidade(), 40, null)));

        PedidoService pedidoService = new PedidoService(new JdbcPedidoRepository(database));
        Pedido pedido = pedidoService.criarAPartirDaRequisicao(requisicao, fornecedor.id(), fornecedor.nome(),
                LocalDate.now(), LocalDate.now().plusDays(7), "Pedido de teste");

        Requisicao persistedRequest = new JdbcRequisicaoRepository(database).findById(requisicao.id()).orElseThrow();
        Pedido persistedOrder = new JdbcPedidoRepository(database).findById(pedido.id()).orElseThrow();
        assertEquals("CONVERTIDA", persistedRequest.status().name());
        assertEquals("SOLICITADO", persistedOrder.status().name());
        assertEquals(40, persistedOrder.itens().getFirst().quantidadeComprada());
    }

    @Test
    void registraEntregaParcialRecusaTrocaEConcluiPedido(@TempDir Path temporaryDirectory) throws Exception {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        Material material = new JdbcMaterialRepository(database).findByObraId(obra.id()).getFirst();
        var fornecedor = new JdbcFornecedorRepository(database).findAll().getFirst();

        Requisicao requisicao = new RequisicaoService(new JdbcRequisicaoRepository(database)).criar(
                obra.id(), obra.nome(), "Engenharia", LocalDate.of(2026, 9, 1), Prioridade.ALTA,
                "Fluxo de entrega", java.util.List.of(new RequisicaoItem(null, material.id(), material.descricao(), material.unidade(), 100, null)));
        Pedido pedido = new PedidoService(new JdbcPedidoRepository(database)).criarAPartirDaRequisicao(
                requisicao, fornecedor.id(), fornecedor.nome(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), "Pedido teste entrega");

        EntregaService entregaService = new EntregaService(new JdbcEntregaRepository(database));
        PedidoItem item = pedido.itens().getFirst();
        entregaService.registrar(new EntregaInput(pedido.id(), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 7),
                "NF-1", null, java.util.List.of(new EntregaItemInput(item.id(), 40, 40, true, null))));
        Pedido parcial = new JdbcPedidoRepository(database).findById(pedido.id()).orElseThrow();
        assertEquals("ENTREGA_PARCIAL", parcial.status().name());
        assertEquals(60, parcial.itens().getFirst().quantidadePendente());

        entregaService.registrar(new EntregaInput(pedido.id(), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10),
                "NF-2", null, java.util.List.of(new EntregaItemInput(item.id(), 60, 0, false, "Material danificado"))));
        Pedido comPendencia = new JdbcPedidoRepository(database).findById(pedido.id()).orElseThrow();
        assertEquals("COM_PENDENCIA", comPendencia.status().name());
        assertEquals(60, comPendencia.itens().getFirst().quantidadePendente());

        TrocaService trocaService = new TrocaService(new JdbcTrocaRepository(database));
        trocaService.solicitar(new TrocaInput(item.id(), fornecedor.id(), LocalDate.of(2026, 9, 10), 60,
                "Material danificado", LocalDate.of(2026, 9, 14), null));
        Pedido emTroca = new JdbcPedidoRepository(database).findById(pedido.id()).orElseThrow();
        assertEquals("EM_TROCA", emTroca.status().name());

        long trocaId;
        try (var connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM trocas WHERE pedido_item_id = ?")) {
            statement.setLong(1, item.id());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                trocaId = result.getLong(1);
            }
        }
        trocaService.receber(trocaId, LocalDate.of(2026, 9, 14), "Substituição recebida");

        Pedido concluido = new JdbcPedidoRepository(database).findById(pedido.id()).orElseThrow();
        assertEquals("CONCLUIDO", concluido.status().name());
        assertEquals(100, concluido.itens().getFirst().quantidadeAceita());
        assertEquals(0, concluido.itens().getFirst().quantidadePendente());
        assertEquals(LocalDate.of(2026, 9, 14), concluido.dataRecebimentoCompleto());
    }

    @Test
    void criaErestauraBackupDoBancoLocal(@TempDir Path temporaryDirectory) {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        BackupService backupService = new BackupService(database);
        Path backup = temporaryDirectory.resolve("backup").resolve("obra.db");
        backupService.criarBackup(backup);

        ObraService obraService = new ObraService(new JdbcObraRepository(database));
        obraService.criar("Obra temporária", "TEMP-001", "Rua B", "Equipe", LocalDate.now(), LocalDate.now().plusMonths(1), null);
        assertEquals(2, obraService.listar().size());

        backupService.restaurar(backup);
        assertEquals(1, new ObraService(new JdbcObraRepository(database)).listar().size());
    }

    @Test
    void criaPedidoSemFornecedorEGeraFolhaParaSuprimentos(@TempDir Path temporaryDirectory) throws Exception {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        Material material = new JdbcMaterialRepository(database).findByObraId(obra.id()).getFirst();
        Requisicao requisicao = new RequisicaoService(new JdbcRequisicaoRepository(database)).criar(
                obra.id(), obra.nome(), "Engenharia", LocalDate.now(), Prioridade.NORMAL, null,
                java.util.List.of(new RequisicaoItem(null, material.id(), material.descricao(), material.unidade(), 12, null)));
        Pedido pedido = new PedidoService(new JdbcPedidoRepository(database)).criarAPartirDaRequisicao(
                requisicao, null, null, LocalDate.now(), LocalDate.now().plusDays(5), "Enviar ao suprimentos");

        assertEquals(null, pedido.fornecedorId());
        Path sheet = new FolhaPedidoService(temporaryDirectory).gerar(pedido);
        assertTrue(Files.exists(sheet));
        assertTrue(Files.readString(sheet).contains("Engenheiro"));
    }

    @Test
    void geraFolhaDaRequisicaoComModeloDaIntegralECodigoDosMateriais(@TempDir Path temporaryDirectory) throws Exception {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        Material material = new JdbcMaterialRepository(database).findByObraId(obra.id()).getFirst();
        Requisicao requisicao = new RequisicaoService(new JdbcRequisicaoRepository(database)).criar(
                obra.id(), obra.nome(), "Andre", LocalDate.of(2026, 9, 25), Prioridade.NORMAL,
                "Material para a frente de serviço", java.util.List.of(new RequisicaoItem(null, material.id(),
                        material.codigo(), material.descricao(), material.unidade(), 3, null)));

        Path sheet = new FolhaPedidoService(temporaryDirectory).gerar(requisicao);
        String html = Files.readString(sheet);
        assertTrue(html.contains("INTEGRAL"));
        assertTrue(html.contains("CONSTRUTORA"));
        assertTrue(html.contains(material.codigo()));
        assertTrue(html.contains("QUANT. SOLICITADA"));
        assertTrue(html.contains("AUTORIZADO POR"));
    }

    @Test
    void vinculaEquipamentoAEmpresaLocatariaEDevolvePeloMesmoCodigo(@TempDir Path temporaryDirectory) {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        JdbcMaterialRepository materialRepository = new JdbcMaterialRepository(database);
        Material equipment = new MaterialService(materialRepository).criar(
                obra.id(), MaterialTipo.EQUIPAMENTO, "EQ-TEST", "Martelete de teste", "UN", "Ferramentas", "",
                0, 0, 0, 0, 0, "");
        AlmoxarifadoService almoxarifado = new AlmoxarifadoService(new JdbcEstoqueRepository(database), materialRepository);

        almoxarifado.registrarEntrada(obra.id(), equipment.id(), 1, "Recebimento", "NF-TESTE");
        almoxarifado.registrarRetirada(obra.id(), equipment.id(), 1, "João da Silva", "Obra A",
                "Almoxarife", "Empresa Locatária", "");

        var pending = almoxarifado.listarRetiradasPendentes(obra.id());
        assertEquals(1, pending.size());
        assertEquals("EQ-TEST", pending.getFirst().codigoMaterial());
        assertEquals("Empresa Locatária", pending.getFirst().empresaLocataria());

        almoxarifado.registrarDevolucao(obra.id(), pending.getFirst().referencia(), 1, "Conferente", "");
        assertTrue(almoxarifado.listarRetiradasPendentes(obra.id()).isEmpty());
        assertTrue(almoxarifado.listarMovimentacoes(obra.id()).stream()
                .filter(item -> item.tipo().name().equals("DEVOLUCAO"))
                .allMatch(item -> "Empresa Locatária".equals(item.empresaLocataria())));
    }

    @Test
    void persistePlanejamentoCalculaAvancoEAtualizaComMedicao(@TempDir Path temporaryDirectory) {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        PlanejamentoService planejamento = new PlanejamentoService(new br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository(database));

        var etapa = planejamento.salvarCronograma(obra.id(), null, 1, "1.1", "Fundação", "M2", 100,
                50, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 0, null,
                java.util.List.of(1000d, 500d, 0d, 250d));
        planejamento.salvarOrcamento(obra.id(), null, "1.1", "Fundação", "M2", 100, 25.50, null);
        planejamento.salvarMedicao(obra.id(), null, etapa.id(), null, "MED-001", LocalDate.of(2026, 9, 25),
                "Fundação executada", 25, 25, 637.50, null);

        var resumo = planejamento.resumo(obra.id(), LocalDate.of(2026, 9, 25));
        assertEquals(25, planejamento.listarCronograma(obra.id()).getFirst().percentualExecutado());
        assertEquals(1750, planejamento.listarCronograma(obra.id()).getFirst().totalPrevisto());
        assertEquals(1000, planejamento.listarCronograma(obra.id()).getFirst().valorMensal(1));
        assertEquals(0, planejamento.listarCronograma(obra.id()).getFirst().valorMensal(3));
        assertEquals(250, planejamento.listarCronograma(obra.id()).getFirst().valorMensal(4));
        assertEquals(25, resumo.percentualExecutado());
        assertEquals(2550, resumo.orcamentoTotal());
        assertEquals(637.50, resumo.valorMedido());
        assertEquals(1000d / 1750d * 100d,
                planejamento.resumo(obra.id(), LocalDate.of(2026, 9, 25), obra.dataInicio()).percentualPlanejado(), 0.001);

        DatabaseManager reopened = new DatabaseManager(temporaryDirectory);
        reopened.initialize();
        var persisted = new br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository(reopened)
                .listarCronograma(obra.id()).getFirst();
        assertEquals(1000, persisted.valorMensal(1));
        assertEquals(250, persisted.valorMensal(4));
    }

    @Test
    void importaPropostaDoPdfEPersisteExecucaoSemAlterarPrevisto(@TempDir Path temporaryDirectory) {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        PlanejamentoService planejamento = new PlanejamentoService(
                new br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository(database));

        var etapaAntiga = planejamento.salvarCronograma(obra.id(), null, 1, "1.0", "Serviço antigo", "UN", 1,
                0, null, null, 0, null, java.util.List.of());
        planejamento.salvarExecucao(obra.id(), etapaAntiga.id(), 3, 1_250.00);

        planejamento.importarPropostaRioClaro(obra.id());
        var itens = planejamento.listarCronograma(obra.id());
        assertEquals(27, itens.size());
        assertEquals(14_369_168.31, itens.stream().mapToDouble(item -> item.totalPrevisto()).sum(), 0.001);
        assertEquals(177_545.02, itens.getFirst().valorMensal(1), 0.001);
        assertEquals(189_440.85, itens.getFirst().totalPrevisto(), 0.001);
        assertEquals(etapaAntiga.id(), itens.getFirst().id());
        assertEquals(1_250.00, itens.getFirst().valorExecutado(3), 0.001);
        assertTrue(!itens.getFirst().metaFisicaDefinida());
        planejamento.salvarExecucao(obra.id(), itens.getFirst().id(), 1, 150_000.00);
        planejamento.salvarExecucao(obra.id(), itens.getFirst().id(), 2, 2_000.00);
        planejamento.importarPropostaRioClaro(obra.id());

        DatabaseManager reopened = new DatabaseManager(temporaryDirectory);
        reopened.initialize();
        var persisted = new br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository(reopened)
                .listarCronograma(obra.id());
        assertEquals(27, persisted.size());
        assertEquals(177_545.02, persisted.getFirst().valorMensal(1), 0.001);
        assertEquals(150_000.00, persisted.getFirst().valorExecutado(1), 0.001);
        assertEquals(2_000.00, persisted.getFirst().valorExecutado(2), 0.001);
        assertEquals(153_250.00, persisted.getFirst().totalExecutado(), 0.001);
    }

    @Test
    void vinculaEfetivoDoRdoATarefaECalculaProdutividade(@TempDir Path temporaryDirectory) {
        DatabaseManager database = new DatabaseManager(temporaryDirectory);
        database.initialize();
        Obra obra = new JdbcObraRepository(database).findActive().orElseThrow();
        DiarioObraService diarios = new DiarioObraService(new JdbcDiarioObraRepository(database));
        PlanejamentoService planejamento = new PlanejamentoService(
                new br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository(database), diarios);
        var rdo = diarios.salvar(obra.id(), null, LocalDate.of(2026, 9, 25), "Execução de alvenaria",
                "3 x Pedreiro; 2 x Servente", "", "", "", "");
        var tarefa = planejamento.salvarCronograma(obra.id(), null, 1, "10.1", "Alvenaria", "m²", 100,
                0, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 30), 0, null);
        var outra = planejamento.salvarCronograma(obra.id(), null, 2, "10.2", "Contrapiso", "m²", 80,
                0, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 30), 0, null);

        var production = planejamento.salvarProducao(obra.id(), null, tarefa.id(), rdo.id(), 30, "Parede A",
                java.util.List.of(new EfetivoAlocado("Pedreiro", 2, 8), new EfetivoAlocado("Servente", 1, 8)));
        assertEquals(3, production.pessoas());
        assertEquals(24, production.horasHomem(), 0.001);
        assertEquals(10, production.produtividadePorPessoaDia(), 0.001);
        assertEquals(1.25, production.produtividadePorHoraHomem(), 0.001);
        assertEquals(30, PlanejamentoService.progressoFisico(tarefa, planejamento.listarProducao(obra.id())), 0.001);

        assertThrows(br.com.pimentech.controlemateriais.exception.ValidationException.class,
                () -> planejamento.salvarProducao(obra.id(), null, outra.id(), rdo.id(), 10, null,
                        java.util.List.of(new EfetivoAlocado("Pedreiro", 2, 8))));
        var second = planejamento.salvarProducao(obra.id(), null, outra.id(), rdo.id(), 10, null,
                java.util.List.of(new EfetivoAlocado("Pedreiro", 1, 4)));
        assertEquals(2, planejamento.listarProducao(obra.id()).size());
        assertEquals(0.5, second.pessoasDia(), 0.001);
        assertEquals(20, second.produtividadePorPessoaDia(), 0.001);
        assertEquals(21.25, planejamento.resumoTarefas(obra.id(), LocalDate.of(2026, 9, 25), obra.dataInicio())
                .percentualExecutado(), 0.001);

        DatabaseManager reopened = new DatabaseManager(temporaryDirectory);
        reopened.initialize();
        PlanejamentoService reloaded = new PlanejamentoService(
                new br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository(reopened),
                new DiarioObraService(new JdbcDiarioObraRepository(reopened)));
        assertEquals(2, reloaded.listarProducao(obra.id()).size());
        reloaded.excluirProducao(obra.id(), second.id());
        assertEquals(1, reloaded.listarProducao(obra.id()).size());
        DiarioObraService reopenedDiaries = new DiarioObraService(new JdbcDiarioObraRepository(reopened));
        reopenedDiaries.salvar(obra.id(), rdo.id(), LocalDate.of(2026, 9, 26), "Execução de alvenaria",
                "3 x Pedreiro; 2 x Servente", "", "", "", "");
        assertEquals(LocalDate.of(2026, 9, 26), reloaded.listarProducao(obra.id()).getFirst().data());
        assertThrows(br.com.pimentech.controlemateriais.exception.ValidationException.class,
                () -> reopenedDiaries.salvar(obra.id(), rdo.id(), LocalDate.of(2026, 9, 26), "Execução de alvenaria",
                        "1 x Pedreiro; 2 x Servente", "", "", "", ""));
    }
}
