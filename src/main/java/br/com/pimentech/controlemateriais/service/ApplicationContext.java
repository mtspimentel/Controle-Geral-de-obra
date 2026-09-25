package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.database.DatabaseManager;
import br.com.pimentech.controlemateriais.repository.FornecedorRepository;
import br.com.pimentech.controlemateriais.repository.JdbcFornecedorRepository;
import br.com.pimentech.controlemateriais.repository.DashboardRepository;
import br.com.pimentech.controlemateriais.repository.JdbcDashboardRepository;
import br.com.pimentech.controlemateriais.repository.JdbcEntregaRepository;
import br.com.pimentech.controlemateriais.repository.JdbcMaterialRepository;
import br.com.pimentech.controlemateriais.repository.JdbcObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPedidoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcRequisicaoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcTrocaRepository;
import br.com.pimentech.controlemateriais.repository.JdbcReportRepository;
import br.com.pimentech.controlemateriais.repository.JdbcAcompanhamentoRepository;
import br.com.pimentech.controlemateriais.repository.EstoqueRepository;
import br.com.pimentech.controlemateriais.repository.JdbcEstoqueRepository;
import br.com.pimentech.controlemateriais.repository.DiarioObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcDiarioObraRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoRepository;
import br.com.pimentech.controlemateriais.repository.JdbcPlanejamentoDiarioRepository;
import br.com.pimentech.controlemateriais.repository.JdbcFrentesRepository;
import br.com.pimentech.controlemateriais.repository.EntregaRepository;
import br.com.pimentech.controlemateriais.repository.MaterialRepository;
import br.com.pimentech.controlemateriais.repository.ObraRepository;
import br.com.pimentech.controlemateriais.repository.PedidoRepository;
import br.com.pimentech.controlemateriais.repository.RequisicaoRepository;
import br.com.pimentech.controlemateriais.repository.TrocaRepository;

public final class ApplicationContext implements AutoCloseable {

    private final DatabaseManager databaseManager;
    private final ObraRepository obraRepository;
    private final MaterialRepository materialRepository;
    private final FornecedorRepository fornecedorRepository;
    private final ObraService obraService;
    private final MaterialService materialService;
    private final FornecedorService fornecedorService;
    private final RequisicaoRepository requisicaoRepository;
    private final PedidoRepository pedidoRepository;
    private final RequisicaoService requisicaoService;
    private final PedidoService pedidoService;
    private final EntregaRepository entregaRepository;
    private final TrocaRepository trocaRepository;
    private final EntregaService entregaService;
    private final TrocaService trocaService;
    private final DashboardService dashboardService;
    private final BackupService backupService;
    private final ReportService reportService;
    private final AcompanhamentoService acompanhamentoService;
    private final FolhaPedidoService folhaPedidoService;
    private final AlmoxarifadoService almoxarifadoService;
    private final DiarioObraService diarioObraService;
    private final PlanejamentoService planejamentoService;
    private final PlanejamentoDiarioService planejamentoDiarioService;
    private final FrentesService frentesService;

    public ApplicationContext() {
        databaseManager = new DatabaseManager();
        databaseManager.initialize();
        obraRepository = new JdbcObraRepository(databaseManager);
        materialRepository = new JdbcMaterialRepository(databaseManager);
        fornecedorRepository = new JdbcFornecedorRepository(databaseManager);
        requisicaoRepository = new JdbcRequisicaoRepository(databaseManager);
        pedidoRepository = new JdbcPedidoRepository(databaseManager);
        entregaRepository = new JdbcEntregaRepository(databaseManager);
        trocaRepository = new JdbcTrocaRepository(databaseManager);
        DashboardRepository dashboardRepository = new JdbcDashboardRepository(databaseManager);
        obraService = new ObraService(obraRepository);
        materialService = new MaterialService(materialRepository);
        fornecedorService = new FornecedorService(fornecedorRepository);
        requisicaoService = new RequisicaoService(requisicaoRepository);
        pedidoService = new PedidoService(pedidoRepository);
        entregaService = new EntregaService(entregaRepository);
        trocaService = new TrocaService(trocaRepository);
        dashboardService = new DashboardService(dashboardRepository);
        backupService = new BackupService(databaseManager);
        reportService = new ReportService(new JdbcReportRepository(databaseManager));
        acompanhamentoService = new AcompanhamentoService(new JdbcAcompanhamentoRepository(databaseManager));
        folhaPedidoService = new FolhaPedidoService(databaseManager.getRootDirectory());
        EstoqueRepository estoqueRepository = new JdbcEstoqueRepository(databaseManager);
        almoxarifadoService = new AlmoxarifadoService(estoqueRepository, materialRepository);
        DiarioObraRepository diarioRepository = new JdbcDiarioObraRepository(databaseManager);
        diarioObraService = new DiarioObraService(diarioRepository);
        planejamentoService = new PlanejamentoService(new JdbcPlanejamentoRepository(databaseManager), diarioObraService);
        planejamentoDiarioService = new PlanejamentoDiarioService(new JdbcPlanejamentoDiarioRepository(databaseManager), materialRepository);
        frentesService = new FrentesService(new JdbcFrentesRepository(databaseManager), planejamentoDiarioService);
    }

    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    public ObraRepository obraRepository() {
        return obraRepository;
    }

    public MaterialRepository materialRepository() {
        return materialRepository;
    }

    public FornecedorRepository fornecedorRepository() {
        return fornecedorRepository;
    }

    public ObraService obraService() {
        return obraService;
    }

    public MaterialService materialService() {
        return materialService;
    }

    public FornecedorService fornecedorService() {
        return fornecedorService;
    }

    public RequisicaoRepository requisicaoRepository() {
        return requisicaoRepository;
    }

    public PedidoRepository pedidoRepository() {
        return pedidoRepository;
    }

    public RequisicaoService requisicaoService() {
        return requisicaoService;
    }

    public PedidoService pedidoService() {
        return pedidoService;
    }

    public EntregaService entregaService() {
        return entregaService;
    }

    public TrocaService trocaService() {
        return trocaService;
    }

    public DashboardService dashboardService() {
        return dashboardService;
    }

    public BackupService backupService() {
        return backupService;
    }

    public ReportService reportService() {
        return reportService;
    }

    public AcompanhamentoService acompanhamentoService() {
        return acompanhamentoService;
    }

    public FolhaPedidoService folhaPedidoService() {
        return folhaPedidoService;
    }

    public AlmoxarifadoService almoxarifadoService() {
        return almoxarifadoService;
    }

    public DiarioObraService diarioObraService() {
        return diarioObraService;
    }

    public PlanejamentoService planejamentoService() {
        return planejamentoService;
    }

    public PlanejamentoDiarioService planejamentoDiarioService() {
        return planejamentoDiarioService;
    }

    public FrentesService frentesService() {
        return frentesService;
    }

    @Override
    public void close() {
        databaseManager.close();
    }
}
