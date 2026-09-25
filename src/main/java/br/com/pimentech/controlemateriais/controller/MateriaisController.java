package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.MaterialTipo;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class MateriaisController {

    private final ApplicationContext context;
    private final TableView<Material> table = new TableView<>();
    private final TextField search = new TextField();
    private MaterialTipo selectedType = MaterialTipo.MATERIAL;
    private TableColumn<Material, String> remainingColumn;

    public MateriaisController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Materiais");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null ? "Nenhuma obra ativa selecionada" : "Obra: " + obra.nome() + " (" + obra.codigo() + ")");
        subtitle.getStyleClass().add("page-subtitle");

        search.setPromptText("Pesquisar por código, descrição ou categoria");
        search.setPrefWidth(320);
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        Button newButton = new Button("+ Novo material");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> openForm(null));
        Button editButton = new Button("Editar selecionado");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> editSelected());
        HBox toolbar = new HBox(10, search, newButton, editButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        TabPane typeTabs = createTypeTabs();
        configureTable();
        refresh();
        VBox.setVgrow(table, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, toolbar, typeTabs, table);
        return page;
    }

    private TabPane createTypeTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (MaterialTipo type : MaterialTipo.values()) {
            Tab tab = new Tab(type.label());
            tab.setUserData(type);
            tabs.getTabs().add(tab);
        }
        tabs.getSelectionModel().select(0);
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null) return;
            selectedType = (MaterialTipo) newTab.getUserData();
            updateRemainingColumn();
            refresh();
        });
        return tabs;
    }

    private void configureTable() {
        if (!table.getColumns().isEmpty()) {
            return;
        }
        table.setPlaceholder(new Label("Nenhum material cadastrado para a obra ativa"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<Material, String> code = new TableColumn<>("Código");
        code.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().codigo()));
        TableColumn<Material, String> description = new TableColumn<>("Material");
        description.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().descricao()));
        TableColumn<Material, String> unit = new TableColumn<>("Unidade");
        unit.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().unidade()));
        TableColumn<Material, String> category = new TableColumn<>("Categoria");
        category.setCellValueFactory(cell -> new SimpleStringProperty(value(cell.getValue().categoria())));
        TableColumn<Material, String> stock = new TableColumn<>("Estoque");
        stock.setCellValueFactory(cell -> new SimpleStringProperty(number(cell.getValue().estoqueAtual())));
        remainingColumn = new TableColumn<>("Dias de estoque");
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(remainingDays(cell.getValue())));
        TableColumn<Material, String> status = new TableColumn<>("Situação");
        status.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().estoqueAtual() <= cell.getValue().estoqueMinimo() ? "COMPRAR" : "OK"));
        table.getColumns().addAll(code, description, unit, category, stock, remainingColumn, status);
        updateRemainingColumn();
    }

    private void refresh() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            table.setItems(FXCollections.observableArrayList());
            return;
        }
        table.setItems(FXCollections.observableArrayList(context.materialService().pesquisar(obra.id(), search.getText()).stream()
                .filter(material -> material.tipo() == selectedType).toList()));
    }

    private void editSelected() {
        Material selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Materiais", "Selecione um material para editar.");
            return;
        }
        openForm(selected);
    }

    private void openForm(Material existing) {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Materiais", "Cadastre ou ative uma obra antes de cadastrar materiais.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Novo material" : "Editar material");
        dialog.setHeaderText("Cadastro do material");
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<MaterialTipo> type = new ComboBox<>(FXCollections.observableArrayList(MaterialTipo.values()));
        type.setValue(existing == null ? selectedType : existing.tipo());
        TextField code = new TextField(value(existing == null ? null : existing.codigo()));
        TextField description = new TextField(value(existing == null ? null : existing.descricao()));
        TextField unit = new TextField(value(existing == null ? null : existing.unidade()));
        TextField category = new TextField(value(existing == null ? null : existing.categoria()));
        TextField specification = new TextField(value(existing == null ? null : existing.especificacao()));
        TextField minimumStock = new TextField(number(existing == null ? 0 : existing.estoqueMinimo()));
        TextField averageConsumption = new TextField(number(existing == null ? 0 : existing.consumoMedioDiario()));
        TextField averageLeadTime = new TextField(Integer.toString(existing == null ? 0 : existing.prazoMedioEntregaDias()));
        TextField rentedDays = new TextField(Integer.toString(existing == null ? 0 : existing.diasLocado()));
        TextField currentStock = new TextField(number(existing == null ? 0 : existing.estoqueAtual()));
        TextField notes = new TextField(value(existing == null ? null : existing.observacao()));
        currentStock.setDisable(existing != null);
        rentedDays.setDisable(type.getValue() != MaterialTipo.EQUIPAMENTO);
        type.valueProperty().addListener((observable, oldValue, newValue) -> rentedDays.setDisable(newValue != MaterialTipo.EQUIPAMENTO));

        GridPane grid = formGrid();
        addRow(grid, 0, "Tipo *", type);
        addRow(grid, 1, "Código *", code);
        addRow(grid, 2, "Descrição *", description);
        addRow(grid, 3, "Unidade *", unit);
        addRow(grid, 4, "Categoria", category);
        addRow(grid, 5, "Especificação", specification);
        addRow(grid, 6, "Estoque mínimo", minimumStock);
        addRow(grid, 7, "Consumo médio/dia", averageConsumption);
        addRow(grid, 8, "Prazo médio (dias)", averageLeadTime);
        addRow(grid, 9, "Dias locado (equipamento)", rentedDays);
        addRow(grid, 10, existing == null ? "Estoque inicial" : "Estoque atual (use Almoxarifado)", currentStock);
        addRow(grid, 11, "Observação", notes);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(560);

        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            try {
                double min = decimal(minimumStock, "estoque mínimo");
                double consumption = decimal(averageConsumption, "consumo médio diário");
                int leadTime = whole(averageLeadTime, "prazo médio");
                int rented = whole(rentedDays, "dias locado");
                double stock = decimal(currentStock, "estoque atual");
                if (existing == null) {
                    context.materialService().criar(obra.id(), type.getValue(), code.getText(), description.getText(), unit.getText(), category.getText(),
                            specification.getText(), min, consumption, leadTime, rented, stock, notes.getText());
                } else {
                    context.materialService().atualizar(new Material(existing.id(), existing.obraId(), type.getValue(), code.getText(), description.getText(),
                            unit.getText(), category.getText(), specification.getText(), min, consumption, leadTime, rented, notes.getText(),
                            existing.ativo(), stock, existing.createdAt(), existing.updatedAt()));
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

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(9);
        grid.setPadding(new Insets(10, 0, 0, 0));
        return grid;
    }

    private void addRow(GridPane grid, int row, String label, Node input) {
        Label text = new Label(label);
        text.getStyleClass().add("form-label");
        grid.add(text, 0, row);
        grid.add(input, 1, row);
        if (input instanceof TextInputControl control) {
            control.setPrefWidth(360);
        } else if (input instanceof ComboBox<?> combo) {
            combo.setPrefWidth(360);
        }
    }

    private double decimal(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new ValidationException("Informe um número válido para " + name);
        }
    }

    private int whole(TextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new ValidationException("Informe um número inteiro válido para " + name);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }

    private String remainingDays(Material material) {
        if (material.tipo() == MaterialTipo.EQUIPAMENTO) return material.diasLocado() + " dias";
        if (material.consumoMedioDiario() <= 0) return "Não informado";
        return number(material.estoqueAtual() / material.consumoMedioDiario()) + " dias";
    }

    private void updateRemainingColumn() {
        if (remainingColumn != null) {
            remainingColumn.setText(selectedType == MaterialTipo.EQUIPAMENTO ? "Dias locado" : "Dias de estoque");
        }
    }

    private String friendlyMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage();
    }
}
