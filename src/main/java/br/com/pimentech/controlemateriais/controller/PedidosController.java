package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.dto.EntregaInput;
import br.com.pimentech.controlemateriais.dto.EntregaItemInput;
import br.com.pimentech.controlemateriais.dto.TrocaInput;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.model.PedidoItem;
import br.com.pimentech.controlemateriais.model.PedidoStatus;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.DateFormats;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;

public final class PedidosController {

    private final ApplicationContext context;
    private final TableView<Pedido> table = new TableView<>();

    public PedidosController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Pedidos");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null ? "Cadastre uma obra antes de criar pedidos" : "Pedidos da obra: " + obra.nome());
        subtitle.getStyleClass().add("page-subtitle");
        Button newButton = new Button("+ Novo pedido");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> openForm());
        Button detailButton = new Button("Ver itens");
        detailButton.getStyleClass().add("secondary-button");
        detailButton.setOnAction(event -> showDetails());
        Button deliveryButton = new Button("Registrar entrega");
        deliveryButton.getStyleClass().add("secondary-button");
        deliveryButton.setOnAction(event -> registerDelivery());
        Button exchangeButton = new Button("Solicitar troca");
        exchangeButton.getStyleClass().add("secondary-button");
        exchangeButton.setOnAction(event -> requestExchange());
        Button sheetButton = new Button("Gerar folha");
        sheetButton.getStyleClass().add("secondary-button");
        sheetButton.setOnAction(event -> generateSheet());
        Button statusButton = new Button("Alterar status");
        statusButton.getStyleClass().add("secondary-button");
        statusButton.setOnAction(event -> changeStatus());
        Button deleteButton = new Button("Excluir pedido");
        deleteButton.getStyleClass().add("secondary-button");
        deleteButton.setOnAction(event -> deleteSelected());
        HBox toolbar = new HBox(10, newButton, detailButton, deliveryButton, exchangeButton, statusButton, sheetButton, deleteButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        configureTable();
        refresh();
        VBox.setVgrow(table, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, toolbar, table);
        return page;
    }

    private void configureTable() {
        if (!table.getColumns().isEmpty()) return;
        table.setPlaceholder(new Label("Nenhum pedido cadastrado para a obra ativa"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<Pedido, String> number = new TableColumn<>("Pedido");
        number.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().numero()));
        TableColumn<Pedido, String> date = new TableColumn<>("Data do pedido");
        date.setCellValueFactory(cell -> new SimpleStringProperty(DateFormats.format(cell.getValue().dataPedido())));
        TableColumn<Pedido, String> due = new TableColumn<>("Previsão");
        due.setCellValueFactory(cell -> new SimpleStringProperty(DateFormats.format(cell.getValue().dataPrevistaEntrega())));
        TableColumn<Pedido, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new SimpleStringProperty(statusText(cell.getValue().status())));
        TableColumn<Pedido, String> items = new TableColumn<>("Itens");
        items.setCellValueFactory(cell -> new SimpleStringProperty(Integer.toString(cell.getValue().itens().size())));
        TableColumn<Pedido, String> duration = new TableColumn<>("Prazo real");
        duration.setCellValueFactory(cell -> new SimpleStringProperty(realDuration(cell.getValue())));
        table.getColumns().addAll(number, date, due, status, duration, items);
    }

    private void refresh() {
        Obra obra = context.obraService().obraAtiva();
        table.setItems(obra == null ? FXCollections.observableArrayList() : FXCollections.observableArrayList(context.pedidoService().listar(obra.id())));
    }

    private void openForm() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Pedidos", "Cadastre ou ative uma obra antes de criar um pedido.");
            return;
        }
        var requests = context.requisicaoService().listar(obra.id()).stream().filter(item -> item.status() != br.com.pimentech.controlemateriais.model.RequisicaoStatus.CONVERTIDA).toList();
        if (requests.isEmpty()) {
            UiAlerts.info("Pedidos", "Crie uma requisição antes de gerar um pedido.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Novo pedido");
        dialog.setHeaderText("O pedido será criado a partir de uma requisição");
        ButtonType save = new ButtonType("Salvar pedido", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ComboBox<Requisicao> request = new ComboBox<>(FXCollections.observableArrayList(requests));
        request.setConverter(requestConverter());
        request.setValue(requests.get(0));
        DatePicker orderDate = new DatePicker(LocalDate.now());
        DatePicker dueDate = new DatePicker(LocalDate.now().plusDays(7));
        DateFormats.configure(orderDate);
        DateFormats.configure(dueDate);
        TextArea notes = new TextArea();
        notes.setPrefRowCount(2);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        addRow(grid, 0, "Requisição", request);
        addRow(grid, 1, "Data do pedido", orderDate);
        addRow(grid, 2, "Previsão de entrega", dueDate);
        addRow(grid, 3, "Observação", notes);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(570);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                Requisicao selectedRequest = request.getValue();
                context.pedidoService().criarAPartirDaRequisicao(selectedRequest, null, null, orderDate.getValue(), dueDate.getValue(), notes.getText());
                refresh();
                return button;
            } catch (ValidationException exception) {
                UiAlerts.error("Validação", exception.getMessage());
                return null;
            } catch (RuntimeException exception) {
                UiAlerts.error("Não foi possível salvar", friendlyMessage(exception));
                return null;
            }
        });
        dialog.showAndWait();
    }

    private void showDetails() {
        Pedido selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Pedidos", "Selecione um pedido para ver os itens.");
            return;
        }
        TableView<PedidoItem> items = new TableView<>(FXCollections.observableArrayList(selected.itens()));
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<PedidoItem, String> material = new TableColumn<>("Material");
        material.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialDescricao()));
        TableColumn<PedidoItem, String> requested = new TableColumn<>("Solicitado");
        requested.setCellValueFactory(cell -> new SimpleStringProperty(number(cell.getValue().quantidadeSolicitada())));
        TableColumn<PedidoItem, String> purchased = new TableColumn<>("Comprado");
        purchased.setCellValueFactory(cell -> new SimpleStringProperty(number(cell.getValue().quantidadeComprada())));
        TableColumn<PedidoItem, String> received = new TableColumn<>("Recebido");
        received.setCellValueFactory(cell -> new SimpleStringProperty(number(cell.getValue().quantidadeRecebida())));
        TableColumn<PedidoItem, String> pending = new TableColumn<>("Pendente");
        pending.setCellValueFactory(cell -> new SimpleStringProperty(number(cell.getValue().quantidadePendente())));
        TableColumn<PedidoItem, String> reason = new TableColumn<>("Motivo da pendência");
        reason.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().motivoPendencia() == null ? "-" : cell.getValue().motivoPendencia()));
        items.getColumns().addAll(material, requested, purchased, received, pending, reason);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(selected.numero());
        dialog.setHeaderText("Itens do pedido");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(items);
        dialog.getDialogPane().setPrefSize(980, 400);
        dialog.showAndWait();
    }

    private void generateSheet() {
        Pedido selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Folha de pedido", "Selecione um pedido para gerar a folha.");
            return;
        }
        try {
            java.nio.file.Path file = context.folhaPedidoService().gerar(selected);
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file.toFile());
            UiAlerts.info("Folha de pedido", "Folha gerada em:\n" + file);
        } catch (RuntimeException | java.io.IOException exception) {
            UiAlerts.error("Folha de pedido", exception.getMessage() == null ? "Não foi possível gerar a folha." : exception.getMessage());
        }
    }

    private void changeStatus() {
        Pedido selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Status", "Selecione um pedido.");
            return;
        }
        ComboBox<PedidoStatus> status = new ComboBox<>(FXCollections.observableArrayList(
                PedidoStatus.SOLICITADO, PedidoStatus.COTADO, PedidoStatus.COMPRADO, PedidoStatus.RECEBIDO));
        status.setValue(selected.status() == PedidoStatus.CONCLUIDO ? PedidoStatus.RECEBIDO : selected.status());
        status.setConverter(new StringConverter<>() {
            @Override public String toString(PedidoStatus value) { return value == null ? "" : statusText(value); }
            @Override public PedidoStatus fromString(String value) { return null; }
        });
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Alterar status do pedido");
        dialog.setHeaderText(selected.numero() + " • status atual: " + statusText(selected.status()));
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        addRow(grid, 0, "Novo status", status);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                context.pedidoService().alterarStatus(selected, status.getValue());
                refresh();
                return button;
            } catch (RuntimeException exception) {
                UiAlerts.error("Status", friendlyMessage(exception));
                return null;
            }
        });
        dialog.showAndWait();
    }

    private void deleteSelected() {
        Pedido selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Pedidos", "Selecione um pedido para excluir.");
            return;
        }
        if (!UiAlerts.confirm("Excluir pedido", "O pedido " + selected.numero() + " será retirado da lista. Deseja continuar?")) {
            return;
        }
        try {
            context.pedidoService().excluir(selected);
            refresh();
            UiAlerts.info("Pedidos", "Pedido excluído com sucesso.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Pedidos", friendlyMessage(exception));
        }
    }

    private void registerDelivery() {
        Pedido selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Entregas", "Selecione um pedido para registrar a entrega.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registrar entrega • " + selected.numero());
        dialog.setHeaderText("Informe o que chegou e o que foi aceito");
        ButtonType save = new ButtonType("Registrar entrega", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        DatePicker receivingDate = new DatePicker(LocalDate.now());
        DateFormats.configure(receivingDate);
        TextField invoice = new TextField();
        invoice.setPromptText("Nota fiscal");
        GridPane lines = new GridPane();
        lines.setHgap(8);
        lines.setVgap(7);
        addDeliveryHeader(lines, 0, 0, "Material / pendente");
        addDeliveryHeader(lines, 1, 0, "Recebido");
        addDeliveryHeader(lines, 2, 0, "Aceito");
        addDeliveryHeader(lines, 3, 0, "Material correto?");
        addDeliveryHeader(lines, 4, 0, "Motivo da recusa");
        java.util.List<DeliveryLine> deliveryLines = selected.itens().stream().map(item -> new DeliveryLine(item)).toList();
        for (int index = 0; index < deliveryLines.size(); index++) {
            DeliveryLine line = deliveryLines.get(index);
            int row = index + 1;
            Label material = new Label(line.item.materialDescricao() + " (pendente: " + number(line.item.quantidadePendente()) + ")");
            material.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(material, Priority.ALWAYS);
            lines.add(material, 0, row);
            lines.add(line.received, 1, row);
            lines.add(line.accepted, 2, row);
            lines.add(line.correct, 3, row);
            lines.add(line.reason, 4, row);
            line.received.setPrefWidth(85);
            line.accepted.setPrefWidth(85);
            line.reason.setPrefWidth(180);
            line.correct.setText("Correto");
            line.reason.setPromptText("Motivo da recusa");
        }
        GridPane header = new GridPane();
        header.setHgap(12);
        header.setVgap(8);
        addRow(header, 0, "Data recebimento", receivingDate);
        addRow(header, 1, "Nota fiscal", invoice);
        VBox content = new VBox(12, header, new Label("Itens recebidos"), lines);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(900, 520);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                java.util.List<EntregaItemInput> items = deliveryLines.stream().map(line -> {
                    double received = decimal(line.received, "recebido");
                    double accepted = decimal(line.accepted, "aceito");
                    String reason = line.reason.getValue();
                    if (reason == null && received < line.item.quantidadePendente()) {
                        reason = "Quantidade faltante";
                    }
                    return new EntregaItemInput(line.item.id(), received, accepted,
                            line.correct.isSelected(), reason);
                }).toList();
                context.entregaService().registrar(new EntregaInput(selected.id(), selected.dataPrevistaEntrega(), receivingDate.getValue(), invoice.getText(), null, items));
                refresh();
                return button;
            } catch (ValidationException exception) {
                UiAlerts.error("Validação", exception.getMessage());
                return null;
            } catch (RuntimeException exception) {
                UiAlerts.error("Não foi possível registrar", friendlyMessage(exception));
                return null;
            }
        });
        dialog.showAndWait();
    }

    private void addDeliveryHeader(GridPane grid, int column, int row, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        grid.add(label, column, row);
        if (column == 0) GridPane.setHgrow(label, Priority.ALWAYS);
    }

    private void requestExchange() {
        Pedido selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Trocas", "Selecione um pedido para solicitar uma troca.");
            return;
        }
        if (selected.fornecedorId() == null) {
            UiAlerts.info("Trocas", "Informe o fornecedor no pedido antes de solicitar uma troca.");
            return;
        }
        var pendingItems = selected.itens().stream().filter(item -> item.quantidadePendente() > 0).toList();
        if (pendingItems.isEmpty()) {
            UiAlerts.info("Trocas", "Esse pedido não possui itens pendentes.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Solicitar troca • " + selected.numero());
        dialog.setHeaderText("Registre o material recusado e a previsão de substituição");
        ButtonType save = new ButtonType("Solicitar troca", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ComboBox<PedidoItem> item = new ComboBox<>(FXCollections.observableArrayList(pendingItems));
        item.setConverter(new StringConverter<>() {
            @Override public String toString(PedidoItem value) { return value == null ? "" : value.materialDescricao() + " • pendente " + number(value.quantidadePendente()); }
            @Override public PedidoItem fromString(String value) { return null; }
        });
        item.setValue(pendingItems.get(0));
        TextField quantity = new TextField(number(pendingItems.get(0).quantidadePendente()));
        TextArea reason = new TextArea();
        reason.setPrefRowCount(2);
        DatePicker dueDate = new DatePicker(LocalDate.now().plusDays(7));
        DateFormats.configure(dueDate);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        addRow(grid, 0, "Material", item);
        addRow(grid, 1, "Quantidade", quantity);
        addRow(grid, 2, "Motivo", reason);
        addRow(grid, 3, "Previsão da troca", dueDate);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(600);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                PedidoItem selectedItem = item.getValue();
                context.trocaService().solicitar(new TrocaInput(selectedItem.id(), selected.fornecedorId(), LocalDate.now(),
                        decimal(quantity, "quantidade"), reason.getText(), dueDate.getValue(), null));
                refresh();
                return button;
            } catch (ValidationException exception) {
                UiAlerts.error("Validação", exception.getMessage());
                return null;
            } catch (RuntimeException exception) {
                UiAlerts.error("Não foi possível solicitar", friendlyMessage(exception));
                return null;
            }
        });
        dialog.showAndWait();
    }

    private void addRow(GridPane grid, int row, String label, Node input) {
        Label text = new Label(label);
        text.getStyleClass().add("form-label");
        grid.add(text, 0, row);
        grid.add(input, 1, row);
        if (input instanceof ComboBox<?> combo) combo.setPrefWidth(360);
        if (input instanceof DatePicker picker) picker.setPrefWidth(360);
        if (input instanceof TextArea area) area.setPrefWidth(360);
    }

    private StringConverter<Requisicao> requestConverter() {
        return new StringConverter<>() {
            @Override public String toString(Requisicao item) { return item == null ? "" : item.numero() + " • " + item.itens().size() + " item(ns)"; }
            @Override public Requisicao fromString(String string) { return null; }
        };
    }

    private String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value); }

    private String statusText(PedidoStatus status) {
        return switch (status) {
            case SOLICITADO -> "SOLICITADO";
            case COTADO -> "COTADO";
            case COMPRADO -> "COMPRADO";
            case RECEBIDO, CONCLUIDO -> "RECEBIDO";
            case ENTREGA_PARCIAL -> "ENTREGA PARCIAL";
            case COM_PENDENCIA -> "COM PENDÊNCIA";
            case EM_TROCA -> "EM TROCA";
            default -> status.name();
        };
    }

    private String realDuration(Pedido pedido) {
        if (pedido.dataRecebimentoCompleto() == null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(pedido.dataPedido(), LocalDate.now());
            return "Em andamento (" + days + " dias)";
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(pedido.dataPedido(), pedido.dataRecebimentoCompleto());
        return days + (days == 1 ? " dia" : " dias");
    }
    private double decimal(TextField field, String name) {
        try { return Double.parseDouble(field.getText().trim().replace(',', '.')); }
        catch (NumberFormatException exception) { throw new ValidationException("Informe um número válido para " + name); }
    }
    private String friendlyMessage(RuntimeException exception) { return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage(); }

    private static final class DeliveryLine {
        private final PedidoItem item;
        private final TextField received = new TextField("0");
        private final TextField accepted = new TextField("0");
        private final CheckBox correct = new CheckBox();
        private final ComboBox<String> reason = new ComboBox<>(FXCollections.observableArrayList(
                "Quantidade faltante",
                "Material danificado",
                "Especificação diferente (marca/modelo)",
                "Material errado",
                "Quantidade incorreta",
                "Outro"));

        private DeliveryLine(PedidoItem item) {
            this.item = item;
            this.correct.setSelected(true);
        }
    }
}
