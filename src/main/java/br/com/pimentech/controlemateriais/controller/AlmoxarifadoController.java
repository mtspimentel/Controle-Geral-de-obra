package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.EstoqueMovimentacao;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.MaterialTipo;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.RetiradaPendente;
import br.com.pimentech.controlemateriais.model.TipoMovimentacaoEstoque;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AlmoxarifadoController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ApplicationContext context;
    private final TableView<Material> stockTable = new TableView<>();
    private final TableView<EstoqueMovimentacao> historyTable = new TableView<>();
    private final TableView<RetiradaPendente> pendingTable = new TableView<>();
    private final Label stockCount = new Label("0");
    private final Label lowStockCount = new Label("0");
    private final Label pendingCount = new Label("0");
    private final Label movementCount = new Label("0");
    private final TextField search = new TextField();
    private MaterialTipo selectedType = MaterialTipo.MATERIAL;
    private TableColumn<Material, String> remainingColumn;
    private Node view;

    public AlmoxarifadoController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        if (view != null) {
            refresh();
            return view;
        }
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));

        Label title = new Label("Controle");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null
                ? "Cadastre ou ative uma obra para movimentar o estoque"
                : "Entradas, saídas e retiradas da obra: " + obra.nome());
        subtitle.getStyleClass().add("page-subtitle");

        GridPane indicators = new GridPane();
        indicators.setHgap(14);
        indicators.setVgap(14);
        indicators.add(indicator("Materiais cadastrados", stockCount, "indicator-blue"), 0, 0);
        indicators.add(indicator("Abaixo do mínimo", lowStockCount, "indicator-red"), 1, 0);
        indicators.add(indicator("Retiradas pendentes", pendingCount, "indicator-yellow"), 2, 0);
        indicators.add(indicator("Movimentações", movementCount, "indicator-green"), 3, 0);

        search.setPromptText("Pesquisar código, descrição ou categoria");
        search.setPrefWidth(320);
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());

        configureStockTable();
        configureHistoryTable();
        configurePendingTable();
        TabPane sections = new TabPane();
        sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sections.getTabs().addAll(
                new Tab("Materiais e saldo", createMaterialsSection()),
                new Tab("Movimentações", createMovementsSection()));
        VBox.setVgrow(sections, Priority.ALWAYS);

        page.getChildren().addAll(title, subtitle, indicators, sections);
        refresh();
        view = page;
        return view;
    }

    private Node createMaterialsSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("dashboard-panel");
        section.setPadding(new Insets(16));

        Label heading = new Label("Catálogo de materiais");
        heading.getStyleClass().add("panel-title");
        Label help = new Label("Filtre por tipo, pesquise e selecione uma linha para editar.");
        help.getStyleClass().add("page-subtitle");
        Button newMaterial = actionButton("+ Novo material", "primary-button", () -> openForm(null));
        Button editMaterial = actionButton("Editar selecionado", "secondary-button", this::editSelected);
        HBox toolbar = new HBox(10, search, newMaterial, editMaterial);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("control-toolbar");
        TabPane typeTabs = createTypeTabs();
        VBox.setVgrow(stockTable, Priority.ALWAYS);
        section.getChildren().addAll(heading, help, toolbar, typeTabs, stockTable);
        return section;
    }

    private Node createMovementsSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("dashboard-panel");
        section.setPadding(new Insets(16));
        Label heading = new Label("Movimentações do almoxarifado");
        heading.getStyleClass().add("panel-title");
        Label help = new Label("Registre o caminho do material desde a entrada até a devolução.");
        help.getStyleClass().add("page-subtitle");
        Button entry = actionButton("Registrar entrada", "primary-button", () -> openEntryForm(TipoMovimentacaoEstoque.ENTRADA));
        Button exit = actionButton("Saída definitiva", "secondary-button", () -> openExitForm(false));
        Button withdrawal = actionButton("Retirada para uso", "secondary-button", () -> openExitForm(true));
        Button returnButton = actionButton("Registrar devolução", "secondary-button", this::openReturnForm);
        HBox toolbar = new HBox(10, entry, exit, withdrawal, returnButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("control-toolbar");
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Pendências de devolução", pendingTable),
                new Tab("Histórico", historyTable));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        section.getChildren().addAll(heading, help, toolbar, tabs);
        return section;
    }

    private VBox indicator(String caption, Label value, String style) {
        VBox card = new VBox(7, value, new Label(caption));
        card.setPadding(new Insets(15));
        card.setMinWidth(165);
        card.getStyleClass().addAll("indicator-card", style);
        value.getStyleClass().add("indicator-value");
        card.getChildren().get(1).getStyleClass().add("indicator-label");
        return card;
    }

    private Button actionButton(String text, String style, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void configureStockTable() {
        if (!stockTable.getColumns().isEmpty()) return;
        stockTable.setPlaceholder(new Label("Nenhum material cadastrado para a obra ativa"));
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        stockTable.getColumns().addAll(
                column("Código", Material::codigo),
                column("Material", Material::descricao),
                column("Unidade", Material::unidade),
                column("Categoria", material -> displayValue(material.categoria())),
                column("Saldo", material -> number(material.estoqueAtual())),
                column("Mínimo", material -> number(material.estoqueMinimo())),
                remainingColumn = new TableColumn<>("Dias de estoque"),
                column("Situação", material -> material.estoqueAtual() <= material.estoqueMinimo() ? "COMPRAR" : "OK"));
        remainingColumn.setCellValueFactory(cell -> new SimpleStringProperty(remainingDays(cell.getValue())));
        updateRemainingColumn();
    }

    private TabPane createTypeTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (MaterialTipo type : MaterialTipo.values()) {
            Tab tab = new Tab(type.label());
            tab.setUserData(type);
            tabs.getTabs().add(tab);
        }
        tabs.getSelectionModel().select(selectedType.ordinal());
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null) return;
            selectedType = (MaterialTipo) newTab.getUserData();
            updateRemainingColumn();
            refresh();
        });
        return tabs;
    }

    private void configureHistoryTable() {
        if (!historyTable.getColumns().isEmpty()) return;
        historyTable.setPlaceholder(new Label("Nenhuma movimentação registrada"));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        historyTable.getColumns().addAll(
                column("Data", item -> item.data() == null ? "-" : DATE_TIME.format(item.data())),
                column("Tipo", item -> item.tipo().label()),
                column("Código", EstoqueMovimentacao::codigoMaterial),
                column("Material", EstoqueMovimentacao::descricaoMaterial),
                column("Quantidade", item -> number(item.quantidade())),
                column("Responsável", EstoqueMovimentacao::responsavel),
                column("Retirante", item -> displayValue(item.retirante())),
                column("Serviço", item -> displayValue(item.servico())));
    }

    private void configurePendingTable() {
        if (!pendingTable.getColumns().isEmpty()) return;
        pendingTable.setPlaceholder(new Label("Nenhuma retirada aguardando devolução"));
        pendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        pendingTable.getColumns().addAll(
                column("Código", RetiradaPendente::codigoMaterial),
                column("Material", RetiradaPendente::descricaoMaterial),
                column("Pendente", item -> number(item.quantidade()) + " " + item.unidade()),
                column("Retirante", RetiradaPendente::retirante),
                column("Serviço", RetiradaPendente::servico));
    }

    private void refresh() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            stockTable.setItems(FXCollections.observableArrayList());
            historyTable.setItems(FXCollections.observableArrayList());
            pendingTable.setItems(FXCollections.observableArrayList());
            setIndicators(0, 0, 0, 0);
            return;
        }
        List<Material> allMaterials = context.materialService().listar(obra.id());
        List<Material> materials = context.materialService().pesquisar(obra.id(), search.getText()).stream()
                .filter(item -> item.tipo() == selectedType)
                .toList();
        List<EstoqueMovimentacao> history = context.almoxarifadoService().listarMovimentacoes(obra.id());
        List<RetiradaPendente> pending = context.almoxarifadoService().listarRetiradasPendentes(obra.id());
        stockTable.setItems(FXCollections.observableArrayList(materials));
        historyTable.setItems(FXCollections.observableArrayList(history));
        pendingTable.setItems(FXCollections.observableArrayList(pending));
        setIndicators(allMaterials.size(), (int) allMaterials.stream().filter(item -> item.estoqueAtual() <= item.estoqueMinimo()).count(),
                pending.size(), history.size());
    }

    private void setIndicators(int stock, int low, int pending, int movements) {
        stockCount.setText(Integer.toString(stock));
        lowStockCount.setText(Integer.toString(low));
        pendingCount.setText(Integer.toString(pending));
        movementCount.setText(Integer.toString(movements));
    }

    private void editSelected() {
        Material selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Controle", "Selecione um material para editar.");
            return;
        }
        openForm(selected);
    }

    private void openForm(Material existing) {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Controle", "Cadastre ou ative uma obra antes de cadastrar materiais.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Novo material" : "Editar material");
        dialog.setHeaderText("Cadastro do material");
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<MaterialTipo> type = new ComboBox<>(FXCollections.observableArrayList(MaterialTipo.values()));
        type.setValue(existing == null ? selectedType : existing.tipo());
        TextField code = new TextField(formValue(existing == null ? null : existing.codigo()));
        TextField description = new TextField(formValue(existing == null ? null : existing.descricao()));
        TextField unit = new TextField(formValue(existing == null ? null : existing.unidade()));
        TextField category = new TextField(formValue(existing == null ? null : existing.categoria()));
        TextField specification = new TextField(formValue(existing == null ? null : existing.especificacao()));
        TextField minimumStock = new TextField(number(existing == null ? 0 : existing.estoqueMinimo()));
        TextField averageConsumption = new TextField(number(existing == null ? 0 : existing.consumoMedioDiario()));
        TextField averageLeadTime = new TextField(Integer.toString(existing == null ? 0 : existing.prazoMedioEntregaDias()));
        TextField rentedDays = new TextField(Integer.toString(existing == null ? 0 : existing.diasLocado()));
        TextField currentStock = new TextField(number(existing == null ? 0 : existing.estoqueAtual()));
        TextField notes = new TextField(formValue(existing == null ? null : existing.observacao()));
        currentStock.setDisable(existing != null);
        rentedDays.setDisable(type.getValue() != MaterialTipo.EQUIPAMENTO);
        type.valueProperty().addListener((observable, oldValue, newValue) ->
                rentedDays.setDisable(newValue != MaterialTipo.EQUIPAMENTO));

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
        addRow(grid, 10, existing == null ? "Estoque inicial" : "Estoque atual (use Controle)", currentStock);
        addRow(grid, 11, "Observação", notes);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(560);

        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                double min = decimal(minimumStock, "estoque mínimo");
                double consumption = decimal(averageConsumption, "consumo médio diário");
                int leadTime = whole(averageLeadTime, "prazo médio");
                int rented = whole(rentedDays, "dias locado");
                double stock = decimal(currentStock, "estoque atual");
                if (existing == null) {
                    context.materialService().criar(obra.id(), type.getValue(), code.getText(), description.getText(), unit.getText(),
                            category.getText(), specification.getText(), min, consumption, leadTime, rented, stock, notes.getText());
                } else {
                    context.materialService().atualizar(new Material(existing.id(), existing.obraId(), type.getValue(), code.getText(),
                            description.getText(), unit.getText(), category.getText(), specification.getText(), min, consumption,
                            leadTime, rented, notes.getText(), existing.ativo(), stock, existing.createdAt(), existing.updatedAt()));
                }
                refresh();
                return button;
            } catch (ValidationException exception) {
                UiAlerts.error("Validação", exception.getMessage());
                return null;
            } catch (RuntimeException exception) {
                UiAlerts.error("Não foi possível salvar", message(exception));
                return null;
            }
        });
        dialog.showAndWait();
    }

    private void openEntryForm(TipoMovimentacaoEstoque type) {
        Obra obra = activeWork();
        if (obra == null) return;
        Dialog<ButtonType> dialog = baseDialog("Registrar entrada", "Entrada de material no estoque");
        ComboBox<Material> material = materialCombo(obra);
        TextField quantity = new TextField();
        TextField responsible = new TextField();
        TextField note = new TextField();
        GridPane grid = formGrid();
        addRow(grid, 0, "Material *", material);
        addRow(grid, 1, "Quantidade *", quantity);
        addRow(grid, 2, "Liberado por *", responsible);
        addRow(grid, 3, "Observação", note);
        dialog.getDialogPane().setContent(grid);
        ButtonType save = saveButton(dialog);
        installValidation(dialog, save, () -> {
            context.almoxarifadoService().registrarEntrada(obra.id(), selectedMaterial(material), decimal(quantity), responsible.getText(), note.getText());
            refresh();
        });
        dialog.showAndWait();
    }

    private void openExitForm(boolean withdrawal) {
        Obra obra = activeWork();
        if (obra == null) return;
        Dialog<ButtonType> dialog = baseDialog(withdrawal ? "Retirada para uso" : "Saída definitiva",
                withdrawal ? "Retirada temporária com devolução pendente" : "Baixa definitiva de material");
        ComboBox<Material> material = materialCombo(obra);
        TextField quantity = new TextField();
        TextField requester = new TextField();
        TextField service = new TextField();
        TextField responsible = new TextField();
        TextField note = new TextField();
        GridPane grid = formGrid();
        addRow(grid, 0, "Material *", material);
        addRow(grid, 1, "Quantidade *", quantity);
        addRow(grid, 2, "Retirante *", requester);
        addRow(grid, 3, "Serviço *", service);
        addRow(grid, 4, "Liberado por *", responsible);
        addRow(grid, 5, "Observação", note);
        dialog.getDialogPane().setContent(grid);
        ButtonType save = saveButton(dialog);
        installValidation(dialog, save, () -> {
            if (withdrawal) {
                context.almoxarifadoService().registrarRetirada(obra.id(), selectedMaterial(material), decimal(quantity),
                        requester.getText(), service.getText(), responsible.getText(), note.getText());
            } else {
                context.almoxarifadoService().registrarSaida(obra.id(), selectedMaterial(material), decimal(quantity),
                        requester.getText(), service.getText(), responsible.getText(), note.getText());
            }
            refresh();
        });
        dialog.showAndWait();
    }

    private void openReturnForm() {
        Obra obra = activeWork();
        if (obra == null) return;
        List<RetiradaPendente> pending = context.almoxarifadoService().listarRetiradasPendentes(obra.id());
        if (pending.isEmpty()) {
            UiAlerts.info("Devolução", "Não há retiradas pendentes para devolver.");
            return;
        }
        Dialog<ButtonType> dialog = baseDialog("Registrar devolução", "Reposição de material retirado para uso");
        ComboBox<RetiradaPendente> withdrawal = new ComboBox<>(FXCollections.observableArrayList(pending));
        withdrawal.setMaxWidth(Double.MAX_VALUE);
        TextField quantity = new TextField();
        TextField responsible = new TextField();
        TextField note = new TextField();
        withdrawal.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) quantity.setText(number(newValue.quantidade()));
        });
        withdrawal.getSelectionModel().selectFirst();
        GridPane grid = formGrid();
        addRow(grid, 0, "Retirada *", withdrawal);
        addRow(grid, 1, "Quantidade *", quantity);
        addRow(grid, 2, "Conferente *", responsible);
        addRow(grid, 3, "Observação", note);
        dialog.getDialogPane().setContent(grid);
        ButtonType save = saveButton(dialog);
        installValidation(dialog, save, () -> {
            RetiradaPendente selected = withdrawal.getValue();
            if (selected == null) throw new ValidationException("Selecione uma retirada pendente.");
            context.almoxarifadoService().registrarDevolucao(obra.id(), selected.referencia(), decimal(quantity),
                    responsible.getText(), note.getText());
            refresh();
        });
        dialog.showAndWait();
    }

    private Obra activeWork() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) UiAlerts.info("Controle", "Cadastre ou ative uma obra antes de movimentar o estoque.");
        return obra;
    }

    private Dialog<ButtonType> baseDialog(String title, String header) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(620);
        return dialog;
    }

    private ButtonType saveButton(Dialog<ButtonType> dialog) {
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(0, save);
        return save;
    }

    private void installValidation(Dialog<ButtonType> dialog, ButtonType save, Runnable action) {
        Node button = dialog.getDialogPane().lookupButton(save);
        button.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                event.consume();
                UiAlerts.error("Não foi possível registrar", message(exception));
            }
        });
    }

    private ComboBox<Material> materialCombo(Obra obra) {
        ComboBox<Material> combo = new ComboBox<>(FXCollections.observableArrayList(context.materialService().listar(obra.id())));
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Material material) {
                return material == null ? "" : material.codigo() + " - " + material.descricao() + " (saldo: " + number(material.estoqueAtual()) + ")";
            }
            @Override public Material fromString(String value) { return combo.getValue(); }
        });
        combo.getSelectionModel().selectFirst();
        return combo;
    }

    private long selectedMaterial(ComboBox<Material> combo) {
        if (combo.getValue() == null) throw new ValidationException("Selecione um material.");
        return combo.getValue().id();
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
        if (input instanceof TextInputControl control) control.setPrefWidth(390);
        else if (input instanceof ComboBox<?> combo) combo.setPrefWidth(390);
    }

    private double decimal(TextField field) {
        try {
            double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ValidationException("Informe uma quantidade válida.");
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

    private <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private String formValue(String text) {
        return text == null ? "" : text;
    }

    private String displayValue(String text) {
        return text == null || text.isBlank() ? "-" : text;
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

    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage();
    }
}
