package br.com.pimentech.controlemateriais.database;

import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.Obra;
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
import br.com.pimentech.controlemateriais.repository.JdbcMaterialRepository;
import br.com.pimentech.controlemateriais.repository.JdbcObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPedidoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcRequisicaoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcTrocaRepository;
import br.com.pimentech.controlemateriais.service.EntregaService;
import br.com.pimentech.controlemateriais.service.TrocaService;
import br.com.pimentech.controlemateriais.service.BackupService;
import br.com.pimentech.controlemateriais.service.FolhaPedidoService;
import br.com.pimentech.controlemateriais.service.MaterialService;
import br.com.pimentech.controlemateriais.service.ObraService;
import br.com.pimentech.controlemateriais.service.PedidoService;
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

class DatabaseManagerIntegrationTest {

    @Test
    void criaBancoAplicaMigrationsESalvaDadosEntreExecucoes(@TempDir Path temporaryDirectory) {
        DatabaseManager firstDatabase = new DatabaseManager(temporaryDirectory);
        firstDatabase.initialize();

        assertTrue(Files.exists(temporaryDirectory.resolve("data").resolve("obra.db")));
        assertEquals(8, firstDatabase.getSchemaVersion());

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
}
