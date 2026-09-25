package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.Prioridade;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.model.RequisicaoItem;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.DateFormats;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextArea;
import javafx.util.StringConverter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public final class RequisicoesController {

    private final ApplicationContext context;
    private final TableView<Requisicao> table = new TableView<>();

    public RequisicoesController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Requisições");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null ? "Cadastre uma obra antes de criar requisições" : "Obra: " + obra.nome());
        subtitle.getStyleClass().add("page-subtitle");
        Button newButton = new Button("+ Nova requisição");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> openForm(null));
        Button editButton = new Button("Editar selecionada");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> editSelected());
        HBox toolbar = new HBox(10, newButton, editButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        configureTable();
        refresh();
        VBox.setVgrow(table, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, toolbar, table);
        return page;
    }

    private void configureTable() {
        if (!table.getColumns().isEmpty()) return;
        table.setPlaceholder(new Label("Nenhuma requisição cadastrada para a obra ativa"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<Requisicao, String> number = new TableColumn<>("Número");
        number.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().numero()));
        TableColumn<Requisicao, String> requester = new TableColumn<>("Solicitante");
        requester.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().solicitante()));
        TableColumn<Requisicao, String> date = new TableColumn<>("Data");
        date.setCellValueFactory(cell -> new SimpleStringProperty(DateFormats.format(cell.getValue().dataRequisicao())));
        TableColumn<Requisicao, String> priority = new TableColumn<>("Prioridade");
        priority.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().prioridade().name()));
        TableColumn<Requisicao, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status().name()));
        TableColumn<Requisicao, String> items = new TableColumn<>("Itens");
        items.setCellValueFactory(cell -> new SimpleStringProperty(Integer.toString(cell.getValue().itens().size())));
        table.getColumns().addAll(number, requester, date, priority, status, items);
    }

    private void refresh() {
        Obra obra = context.obraService().obraAtiva();
        table.setItems(obra == null ? FXCollections.observableArrayList() : FXCollections.observableArrayList(context.requisicaoService().listar(obra.id())));
    }

    private void editSelected() {
        Requisicao selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("RequisiÃ§Ãµes", "Selecione uma requisiÃ§Ã£o para editar.");
            return;
        }
        if (selected.status() == br.com.pimentech.controlemateriais.model.RequisicaoStatus.CONVERTIDA) {
            UiAlerts.info("RequisiÃ§Ãµes", "Essa requisiÃ§Ã£o jÃ¡ foi convertida em pedido e nÃ£o pode ser alterada.");
            return;
        }
        openForm(selected);
    }

    private void openForm(Requisicao existing) {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Requisições", "Cadastre ou ative uma obra antes de criar uma requisição.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nova requisição" : "Editar requisição " + existing.numero());
        dialog.setHeaderText("Informe os materiais necessários para a obra");
        ButtonType save = new ButtonType("Salvar requisição", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField requester = new TextField(existing == null ? "" : existing.solicitante());
        DatePicker date = new DatePicker(existing == null ? LocalDate.now() : existing.dataRequisicao());
        DateFormats.configure(date);
        ComboBox<Prioridade> priority = new ComboBox<>(FXCollections.observableArrayList(Prioridade.values()));
        priority.setValue(existing == null ? Prioridade.NORMAL : existing.prioridade());
        TextArea notes = new TextArea(existing == null || existing.observacao() == null ? "" : existing.observacao());
        notes.setPrefRowCount(2);
        ComboBox<Material> material = new ComboBox<>();
        material.setItems(FXCollections.observableArrayList(context.materialService().listar(obra.id())));
        material.setConverter(materialConverter());
        material.setPrefWidth(300);
        TextField quantity = new TextField();
        quantity.setPromptText("Quantidade");
        Button addItem = new Button("Adicionar material");
        addItem.getStyleClass().add("secondary-button");
        ObservableList<RequisicaoItem> items = FXCollections.observableArrayList(existing == null ? java.util.List.of() : existing.itens());
        TableView<RequisicaoItem> itemTable = createItemTable(items);
        addItem.setOnAction(event -> addItem(material, quantity, items));
        Button removeItem = new Button("Remover selecionado");
        removeItem.getStyleClass().add("secondary-button");
        removeItem.setOnAction(event -> {
            RequisicaoItem selectedItem = itemTable.getSelectionModel().getSelectedItem();
            if (selectedItem != null) items.remove(selectedItem);
        });

        GridPane header = formGrid();
        addRow(header, 0, "Solicitante *", requester);
        addRow(header, 1, "Data", date);
        addRow(header, 2, "Prioridade", priority);
        addRow(header, 3, "Observação", notes);
        HBox itemEntry = new HBox(8, material, quantity, addItem, removeItem);
        itemEntry.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(12, header, new Label("Materiais da requisição"), itemEntry, itemTable);
        VBox.setVgrow(itemTable, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(820, 600);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                if (existing == null) {
                    context.requisicaoService().criar(obra.id(), obra.nome(), requester.getText(), date.getValue(), priority.getValue(), notes.getText(), items);
                } else {
                    context.requisicaoService().atualizar(existing, requester.getText(), date.getValue(), priority.getValue(), notes.getText(), items);
                }
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

    private void addItem(ComboBox<Material> material, TextField quantity, ObservableList<RequisicaoItem> items) {
        Material selected = material.getValue();
        if (selected == null) {
            UiAlerts.info("Itens", "Selecione um material.");
            return;
        }
        try {
            double amount = Double.parseDouble(quantity.getText().trim().replace(',', '.'));
            if (amount <= 0) throw new NumberFormatException();
            if (items.stream().anyMatch(item -> item.materialId().equals(selected.id()))) {
                UiAlerts.info("Itens", "Esse material já foi adicionado.");
                return;
            }
            items.add(new RequisicaoItem(null, selected.id(), selected.descricao(), selected.unidade(), amount, null));
            quantity.clear();
        } catch (NumberFormatException exception) {
            UiAlerts.error("Quantidade inválida", "Informe uma quantidade maior que zero.");
        }
    }

    private TableView<RequisicaoItem> createItemTable(ObservableList<RequisicaoItem> items) {
        TableView<RequisicaoItem> itemTable = new TableView<>(items);
        itemTable.setPlaceholder(new Label("Adicione materiais acima"));
        itemTable.setPrefHeight(220);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<RequisicaoItem, String> description = new TableColumn<>("Material");
        description.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialDescricao()));
        TableColumn<RequisicaoItem, String> unit = new TableColumn<>("Unidade");
        unit.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().unidade()));
        TableColumn<RequisicaoItem, String> amount = new TableColumn<>("Quantidade");
        amount.setCellValueFactory(cell -> new SimpleStringProperty(number(cell.getValue().quantidade())));
        itemTable.getColumns().addAll(description, unit, amount);
        return itemTable;
    }

    private StringConverter<Material> materialConverter() {
        return new StringConverter<>() {
            @Override public String toString(Material material) { return material == null ? "" : material.codigo() + " - " + material.descricao(); }
            @Override public Material fromString(String string) { return null; }
        };
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(9);
        return grid;
    }

    private void addRow(GridPane grid, int row, String label, Node input) {
        Label text = new Label(label);
        text.getStyleClass().add("form-label");
        grid.add(text, 0, row);
        grid.add(input, 1, row);
        if (input instanceof TextInputControl control) control.setPrefWidth(420);
        if (input instanceof DatePicker picker) picker.setPrefWidth(420);
        if (input instanceof ComboBox<?> combo) combo.setPrefWidth(420);
    }

    private String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value); }
    private String friendlyMessage(RuntimeException exception) { return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage(); }
}
