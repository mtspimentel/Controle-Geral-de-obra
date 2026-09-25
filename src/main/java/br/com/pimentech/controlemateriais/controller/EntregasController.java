package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.dto.EntregaResumo;
import br.com.pimentech.controlemateriais.dto.TrocaResumo;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class EntregasController {

    private final ApplicationContext context;

    public EntregasController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Entregas e trocas");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null ? "Nenhuma obra ativa" : "Acompanhamento da obra: " + obra.nome());
        subtitle.getStyleClass().add("page-subtitle");
        TabPane tabs = new TabPane();
        Tab deliveries = new Tab("Entregas", deliveryTable(obra));
        Tab exchanges = new Tab("Trocas", exchangeTable(obra));
        deliveries.setClosable(false);
        exchanges.setClosable(false);
        tabs.getTabs().addAll(deliveries, exchanges);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, tabs);
        return page;
    }

    private TableView<EntregaResumo> deliveryTable(Obra obra) {
        TableView<EntregaResumo> table = new TableView<>();
        table.setPlaceholder(new Label("Nenhuma entrega registrada"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(
                column("Entrega #", row -> Long.toString(row.id())), column("Pedido original", EntregaResumo::pedido),
                column("Fornecedor", EntregaResumo::fornecedor), column("Recebimento", EntregaResumo::dataRecebimento),
                column("Nota fiscal", row -> value(row.notaFiscal())), column("Status", EntregaResumo::status),
                column("Itens", row -> Integer.toString(row.itens())));
        if (obra != null) table.setItems(FXCollections.observableArrayList(context.acompanhamentoService().entregas(obra.id())));
        return table;
    }

    private TableView<TrocaResumo> exchangeTable(Obra obra) {
        TableView<TrocaResumo> table = new TableView<>();
        table.setPlaceholder(new Label("Nenhuma troca registrada"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(
                column("Troca #", row -> Long.toString(row.id())), column("Pedido original", TrocaResumo::pedido),
                column("Material", TrocaResumo::material), column("Fornecedor", TrocaResumo::fornecedor),
                column("Quantidade", row -> number(row.quantidade())), column("Solicitação", TrocaResumo::dataSolicitacao),
                column("Previsão", row -> value(row.previsao())), column("Recebimento", row -> value(row.dataRecebimento())),
                column("Status", TrocaResumo::status), column("Motivo", TrocaResumo::motivo));
        if (obra != null) table.setItems(FXCollections.observableArrayList(context.acompanhamentoService().trocas(obra.id())));
        return table;
    }

    private <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private String value(String value) { return value == null || value.isBlank() ? "-" : value; }
    private String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value); }
}
