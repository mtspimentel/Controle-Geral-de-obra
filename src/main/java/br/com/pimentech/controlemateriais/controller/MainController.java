package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MainController {

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<String, Button> navigationButtons = new LinkedHashMap<>();
    private final ApplicationContext context;
    private final DashboardController dashboardController;
    private final AlmoxarifadoController almoxarifadoController;
    private final ObrasController obrasController;
    private final RequisicoesController requisicoesController;
    private final PedidosController pedidosController;
    private final EntregasController entregasController;
    private final FornecedoresController fornecedoresController;
    private final PlanejamentoController planejamentoController;
    private final DiarioObraController diarioObraController;
    private final RelatoriosController relatoriosController;
    private final ConfiguracoesController configuracoesController;

    public MainController(ApplicationContext context) {
        this.context = context;
        this.dashboardController = new DashboardController(context);
        this.almoxarifadoController = new AlmoxarifadoController(context);
        this.obrasController = new ObrasController(context);
        this.requisicoesController = new RequisicoesController(context);
        this.pedidosController = new PedidosController(context);
        this.entregasController = new EntregasController(context);
        this.fornecedoresController = new FornecedoresController(context);
        this.planejamentoController = new PlanejamentoController(context);
        this.diarioObraController = new DiarioObraController(context);
        this.relatoriosController = new RelatoriosController(context, null);
        this.configuracoesController = new ConfiguracoesController(context, null);
    }

    public Parent createView() {
        root.getStyleClass().add("app-root");
        root.setLeft(createSidebar());
        root.setCenter(createContentArea());
        showDashboard();
        return root;
    }

    private Node createSidebar() {
        VBox sidebar = new VBox(14);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(24, 16, 20, 16));
        sidebar.setPrefWidth(248);

        Label brand = new Label("CONTROLE\nDE MATERIAIS");
        brand.getStyleClass().add("brand");
        brand.setLineSpacing(3);

        Label workLabel = new Label("OBRA ATIVA");
        workLabel.getStyleClass().add("sidebar-caption");

        Label workName = new Label(activeWorkText());
        workName.getStyleClass().add("active-work");
        workName.setLineSpacing(2);

        VBox workBox = new VBox(5, workLabel, workName);
        workBox.getStyleClass().add("work-box");

        VBox menu = new VBox(5);
        String[] items = {"Dashboard", "Obras", "Requisicoes", "Pedidos"};
        for (String item : items) {
            if (item.equals("Fornecedores") || item.equals("Entregas") || item.startsWith("Relat")) continue;
            Button button = createNavigationButton(item);
            navigationButtons.put(item, button);
            menu.getChildren().add(button);
        }
        addNavigationButton(menu, "Controle");
        addNavigationButton(menu, "Planejamento");
        addNavigationButton(menu, "Diário de obra");
        addNavigationButton(menu, "Entregas");
        addNavigationButton(menu, "Fornecedores");
        addNavigationButton(menu, "Relatorios");
        addNavigationButton(menu, "Configuracoes");

        Separator separator = new Separator();
        separator.getStyleClass().add("sidebar-separator");

        Label version = new Label("Versão 0.2.0 • Offline");
        version.getStyleClass().add("sidebar-version");
        VBox.setVgrow(menu, Priority.ALWAYS);
        sidebar.getChildren().addAll(brand, workBox, separator, menu, version);
        return sidebar;
    }

    private Button createNavigationButton(String title) {
        Button button = new Button(title);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("navigation-button");
        button.setTooltip(new Tooltip("Abrir " + title));
        button.setOnAction(event -> selectSection(title));
        return button;
    }

    private void addNavigationButton(VBox menu, String title) {
        Button button = createNavigationButton(title);
        navigationButtons.put(title, button);
        menu.getChildren().add(button);
    }

    private Node createContentArea() {
        VBox area = new VBox(0);
        area.getStyleClass().add("content-area");

        HBox topbar = new HBox();
        topbar.setAlignment(Pos.CENTER_RIGHT);
        topbar.setPadding(new Insets(18, 30, 18, 30));
        topbar.getStyleClass().add("topbar");

        Label status = new Label("●  Banco local disponível");
        status.getStyleClass().add("database-status");
        topbar.getChildren().add(status);

        content.getStyleClass().add("content");
        VBox.setVgrow(content, Priority.ALWAYS);
        area.getChildren().addAll(topbar, content);
        return area;
    }

    private void selectSection(String section) {
        navigationButtons.values().forEach(button -> button.getStyleClass().remove("navigation-button-active"));
        Button selected = navigationButtons.get(section);
        if (selected != null) {
            selected.getStyleClass().add("navigation-button-active");
        }

        if ("Dashboard".equals(section)) {
            showDashboard();
            return;
        }

        if ("Obras".equals(section)) {
            content.getChildren().setAll(obrasController.createView());
            return;
        }

        if ("Requisicoes".equals(section)) {
            content.getChildren().setAll(requisicoesController.createView());
            return;
        }

        if ("Pedidos".equals(section)) {
            content.getChildren().setAll(pedidosController.createView());
            return;
        }

        if ("Controle".equals(section)) {
            content.getChildren().setAll(almoxarifadoController.createView());
            return;
        }

        if ("Planejamento".equals(section)) {
            content.getChildren().setAll(planejamentoController.createView());
            return;
        }

        if ("Diário de obra".equals(section)) {
            content.getChildren().setAll(diarioObraController.createView());
            return;
        }

        if ("Entregas".equals(section)) {
            content.getChildren().setAll(entregasController.createView());
            return;
        }

        if ("Fornecedores".equals(section)) {
            content.getChildren().setAll(fornecedoresController.createView());
            return;
        }

        if ("Relatorios".equals(section)) {
            content.getChildren().setAll(relatoriosController.createView());
            return;
        }

        if ("Configuracoes".equals(section)) {
            content.getChildren().setAll(configuracoesController.createView());
            return;
        }

        if ("ConfiguraÃ§Ãµes".equals(section)) {
            content.getChildren().setAll(configuracoesController.createView());
            return;
        }

        content.getChildren().setAll(createComingSoonView(section));
    }

    private void showDashboard() {
        Button dashboard = navigationButtons.get("Dashboard");
        if (dashboard != null && !dashboard.getStyleClass().contains("navigation-button-active")) {
            dashboard.getStyleClass().add("navigation-button-active");
        }
        content.getChildren().setAll(dashboardController.createView());
    }

    private Node createComingSoonView(String section) {
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getStyleClass().add("placeholder-view");

        Label title = new Label(section);
        title.getStyleClass().add("page-title");
        Label message = new Label("Esta tela será implementada nas próximas fases do sistema.");
        message.getStyleClass().add("placeholder-message");
        placeholder.getChildren().addAll(title, message);
        return placeholder;
    }

    private String activeWorkText() {
        Obra obra = context.obraService().obraAtiva();
        return obra == null ? "Nenhuma obra ativa" : obra.nome() + "\n" + obra.codigo();
    }
}
