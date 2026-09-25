package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class PlanejamentoController {

    private final ApplicationContext context;
    private final TableView<PlanningRow> table = new TableView<>();

    public PlanejamentoController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Planejamento de obra");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null
                ? "Cadastre ou ative uma obra para iniciar o cronograma"
                : "O cronograma de acompanhamento será organizado aqui para: " + obra.nome());
        subtitle.getStyleClass().add("page-subtitle");

        VBox emptyState = new VBox(10);
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        emptyState.getStyleClass().add("dashboard-panel");
        emptyState.setPadding(new Insets(34));
        Label heading = new Label("Cronograma de obra");
        heading.getStyleClass().add("panel-title");
        Label message = new Label("Esta área está reservada para o cronograma e o acompanhamento das etapas da obra.");
        message.getStyleClass().add("placeholder-message");
        message.setWrapText(true);
        emptyState.getChildren().addAll(heading, message);
        VBox.setVgrow(emptyState, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, emptyState);
        return page;
    }

    private void configureTable() {
        if (!table.getColumns().isEmpty()) return;
        table.setPlaceholder(new Label("Nenhum material cadastrado para a obra ativa"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<PlanningRow, String> material = column("Material", row -> row.material().descricao());
        TableColumn<PlanningRow, String> stock = column("Estoque", row -> number(row.material().estoqueAtual()));
        TableColumn<PlanningRow, String> consumption = column("Consumo/dia", row -> number(row.material().consumoMedioDiario()));
        TableColumn<PlanningRow, String> lead = column("Prazo", row -> row.material().prazoMedioEntregaDias() + " dias");
        TableColumn<PlanningRow, String> reorder = column("Ponto de pedido", row -> number(row.reorderPoint()));
        TableColumn<PlanningRow, String> days = column("Dias restantes", row -> row.daysRemaining());
        TableColumn<PlanningRow, String> situation = column("Situação", PlanningRow::situation);
        situation.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                if (empty) getStyleClass().removeAll("planning-alert", "planning-ok");
                else {
                    getStyleClass().removeAll("planning-alert", "planning-ok");
                    getStyleClass().add(value.equals("OK") ? "planning-ok" : "planning-alert");
                }
            }
        });
        table.getColumns().addAll(material, stock, consumption, lead, reorder, days, situation);
    }

    private void load() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            table.setItems(FXCollections.observableArrayList());
            return;
        }
        try {
            table.setItems(FXCollections.observableArrayList(context.materialService().listar(obra.id()).stream().map(PlanningRow::new).toList()));
        } catch (RuntimeException exception) {
            UiAlerts.error("Planejamento", exception.getMessage() == null ? "Não foi possível carregar o planejamento." : exception.getMessage());
        }
    }

    private <T> TableColumn<PlanningRow, String> column(String title, java.util.function.Function<PlanningRow, String> value) {
        TableColumn<PlanningRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }

    private record PlanningRow(Material material) {
        private double reorderPoint() {
            return material.consumoMedioDiario() * material.prazoMedioEntregaDias();
        }

        private String daysRemaining() {
            if (material.consumoMedioDiario() <= 0) return "-";
            double days = material.estoqueAtual() / material.consumoMedioDiario();
            return days == Math.rint(days) ? Long.toString((long) days) : String.format("%.1f", days);
        }

        private String situation() {
            if (material.estoqueAtual() > reorderPoint()) return "OK";
            double days = material.consumoMedioDiario() <= 0 ? Double.POSITIVE_INFINITY : material.estoqueAtual() / material.consumoMedioDiario();
            return days <= material.prazoMedioEntregaDias() ? "PEDIDO IMEDIATO" : "COMPRAR";
        }
    }
}
