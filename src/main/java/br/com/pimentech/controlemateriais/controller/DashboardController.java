package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.dto.DashboardSnapshot;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.DateFormats;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class DashboardController {

    private final ApplicationContext context;

    public DashboardController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(22);
        page.getStyleClass().add("dashboard-page");
        page.setPadding(new Insets(30));

        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Acompanhe os pedidos e as necessidades de materiais da obra.");
        subtitle.getStyleClass().add("page-subtitle");
        Obra obra = context.obraService().obraAtiva();
        DashboardSnapshot snapshot = obra == null
                ? new DashboardSnapshot(0, 0, 0, 0, 0, 0, java.util.List.of(), java.util.List.of())
                : context.dashboardService().carregar(obra.id());

        GridPane indicators = new GridPane();
        indicators.setHgap(14);
        indicators.setVgap(14);
        indicators.add(createIndicator("Pedidos em andamento", Integer.toString(snapshot.pedidosEmAndamento()), "indicator-blue"), 0, 0);
        indicators.add(createIndicator("Pedidos atrasados", Integer.toString(snapshot.pedidosAtrasados()), "indicator-red"), 1, 0);
        indicators.add(createIndicator("Entregas parciais", Integer.toString(snapshot.entregasParciais()), "indicator-yellow"), 2, 0);
        indicators.add(createIndicator("Pedidos com pendência", Integer.toString(snapshot.pedidosComPendencia()), "indicator-purple"), 3, 0);
        
        indicators.add(createIndicator("Pedidos concluídos", Integer.toString(snapshot.pedidosConcluidos()), "indicator-green"), 0, 1);
        indicators.add(createIndicator("Obra ativa", obra == null ? "-" : obra.codigo(), "indicator-neutral"), 1, 1);
        indicators.add(createIndicator("Banco de dados", "v" + context.databaseManager().getSchemaVersion(), "indicator-neutral"), 2, 1);
        for (int column = 0; column < 4; column++) GridPane.setHgrow(indicators.getChildren().get(column), Priority.ALWAYS);

        HBox lowerContent = new HBox(18, createAlertsPanel(snapshot), createDeliveriesPanel(snapshot));
        HBox.setHgrow(lowerContent.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(lowerContent.getChildren().get(1), Priority.ALWAYS);
        VBox.setVgrow(lowerContent, Priority.ALWAYS);

        page.getChildren().addAll(title, subtitle, indicators, lowerContent);
        return page;
    }

    private VBox createIndicator(String label, String value, String styleClass) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setMinWidth(170);
        card.getStyleClass().addAll("indicator-card", styleClass);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("indicator-value");
        Label caption = new Label(label);
        caption.getStyleClass().add("indicator-label");
        card.getChildren().addAll(valueLabel, caption);
        return card;
    }

    private VBox createAlertsPanel(DashboardSnapshot snapshot) {
        VBox panel = createPanel("Alertas");
        if (snapshot.alertas().isEmpty()) {
            panel.getChildren().add(new Label("Nenhum alerta para a obra ativa."));
        } else {
            snapshot.alertas().forEach(alert -> {
                Label item = new Label("⚠  " + alert);
                item.getStyleClass().add("alert-item");
                item.setWrapText(true);
                panel.getChildren().add(item);
            });
        }
        return panel;
    }

    private VBox createDeliveriesPanel(DashboardSnapshot snapshot) {
        VBox panel = createPanel("Próximas entregas");
        TableView<DashboardSnapshot.ProximaEntrega> table = new TableView<>();
        table.setPlaceholder(new Label("Nenhuma entrega prevista"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<DashboardSnapshot.ProximaEntrega, String> order = new TableColumn<>("Pedido");
        order.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().pedido()));
        TableColumn<DashboardSnapshot.ProximaEntrega, String> date = new TableColumn<>("Previsão");
        date.setCellValueFactory(cell -> new SimpleStringProperty(formatDashboardDate(cell.getValue().previsao())));
        TableColumn<DashboardSnapshot.ProximaEntrega, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        table.getColumns().addAll(order, date, status);
        table.setItems(FXCollections.observableArrayList(snapshot.proximasEntregas()));
        VBox.setVgrow(table, Priority.ALWAYS);
        panel.getChildren().add(table);
        return panel;
    }

    private VBox createPanel(String title) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(18));
        panel.getStyleClass().add("dashboard-panel");
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        panel.getChildren().add(heading);
        return panel;
    }

    private String formatDashboardDate(String value) {
        try {
            return DateFormats.format(java.time.LocalDate.parse(value));
        } catch (RuntimeException exception) {
            return value == null || value.isBlank() ? "-" : value;
        }
    }
}
