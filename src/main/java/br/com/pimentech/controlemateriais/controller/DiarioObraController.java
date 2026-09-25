package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.DiarioObra;
import br.com.pimentech.controlemateriais.model.Obra;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

public final class DiarioObraController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> FUNCTIONS = List.of(
            "Engenheiro", "Arquiteto", "Mestre de obras", "Encarregado", "Pedreiro", "Servente",
            "Carpinteiro", "Armador", "Eletricista", "Encanador", "Pintor", "Operador de máquina", "Outro");

    private final ApplicationContext context;
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final ComboBox<String> weather = new ComboBox<>();
    private final TextArea equipment = textArea("Uma linha por equipamento, máquina ou ferramenta.", 5);
    private final CheckBox sunny = new CheckBox("Ensolarado");
    private final CheckBox cloudy = new CheckBox("Nublado");
    private final CheckBox rainy = new CheckBox("Chuvoso");
    private final CheckBox windy = new CheckBox("Vento forte");
    private final TextField otherWeather = new TextField();
    private final TextArea activities = textArea("Descreva os serviços e etapas executados no dia.", 6);
    private final ComboBox<String> functionSelector = new ComboBox<>();
    private final Spinner<Integer> workforceQuantity = new Spinner<>();
    private final TableView<WorkforceItem> workforceTable = new TableView<>();
    private final ObservableList<WorkforceItem> workforceItems = FXCollections.observableArrayList();
    private final TextArea notes = textArea("Registre observações gerais da obra.", 4);
    private final TextArea incidents = textArea("Registre atrasos, acidentes, falta de material ou qualquer intercorrência.", 4);
    private final TableView<DiarioObra> table = new TableView<>();
    private TabPane sections;
    private Tab editTab;
    private DiarioObra selected;
    private Node view;

    public DiarioObraController(ApplicationContext context) {
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

        Label title = new Label("Diário de obra");
        title.getStyleClass().add("page-title");
        Obra obra = context.obraService().obraAtiva();
        Label subtitle = new Label(obra == null
                ? "Cadastre ou ative uma obra para registrar o acompanhamento diário"
                : "Atividades e ocorrências da obra: " + obra.nome());
        subtitle.getStyleClass().add("page-subtitle");

        configureInputs();
        configureTable();
        ScrollPane form = createFormPanel();
        VBox history = createHistoryPanel();
        sections = new TabPane();
        sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab fillTab = new Tab("Preencher diário", form);
        editTab = new Tab("Editar / imprimir", history);
        fillTab.setClosable(false);
        editTab.setClosable(false);
        sections.getTabs().addAll(fillTab, editTab);
        VBox.setVgrow(sections, Priority.ALWAYS);

        page.getChildren().addAll(title, subtitle, sections);
        refresh();
        view = page;
        return view;
    }

    private void configureInputs() {
        DateFormats.configure(date);
        weather.setItems(FXCollections.observableArrayList("Ensolarado", "Nublado", "Chuvoso", "Vento forte", "Outro"));
        weather.setEditable(true);
        weather.setPromptText("Selecione ou informe o clima");
        weather.setMaxWidth(Double.MAX_VALUE);
    }

    private ScrollPane createFormPanel() {
        VBox content = new VBox(14);
        content.getStyleClass().add("rdo-form");
        content.setPadding(new Insets(4, 4, 20, 4));

        Label heading = new Label("Preencher Registro Diário de Obra");
        heading.getStyleClass().add("panel-title");
        Label help = new Label("Preencha cada bloco do RDO para facilitar a conferência e a impressão.");
        help.getStyleClass().add("page-subtitle");

        GridPane identification = formGrid();
        addRow(identification, 0, "Data do RDO *", date);
        Obra obra = context.obraService().obraAtiva();
        Label work = new Label(obra == null ? "Nenhuma obra ativa" : obra.nome() + " • " + obra.codigo());
        work.getStyleClass().add("rdo-work-label");
        identification.add(new Label("Obra ativa"), 0, 1);
        identification.add(work, 1, 1);

        HBox teamAndEquipment = new HBox(14,
                rdoSection("Efetivo e funções", createWorkforceEditor()),
                rdoSection("Equipamentos e ferramentas", createEquipmentEditor()));
        HBox.setHgrow(teamAndEquipment.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(teamAndEquipment.getChildren().get(1), Priority.ALWAYS);

        HBox activitiesAndNotes = new HBox(14,
                rdoSection("Atividades e serviços executados", activities),
                rdoSection("Observações", notes));
        HBox.setHgrow(activitiesAndNotes.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(activitiesAndNotes.getChildren().get(1), Priority.ALWAYS);

        Button newButton = new Button("Novo RDO");
        newButton.getStyleClass().add("secondary-button");
        newButton.setOnAction(event -> clearForm());
        Button saveButton = new Button("Salvar diário");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(event -> save());
        HBox actions = new HBox(10, newButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(
                heading, help,
                rdoSection("Identificação do registro", identification),
                rdoSection("Condição climática", createClimateEditor()),
                teamAndEquipment,
                activitiesAndNotes,
                rdoSection("Intercorrências e providências", incidents),
                actions);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("rdo-inner-scroll");
        return scroll;
    }

    private VBox rdoSection(String title, Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("rdo-section-title");
        VBox section = new VBox(9, heading, content);
        section.getStyleClass().add("rdo-section");
        section.setPadding(new Insets(14));
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private HBox createClimateEditor() {
        otherWeather.setPromptText("Outra condição");
        otherWeather.setPrefWidth(150);
        HBox climate = new HBox(16, sunny, cloudy, rainy, windy,
                new Label("Outra:"), otherWeather);
        climate.setAlignment(Pos.CENTER_LEFT);
        climate.setPadding(new Insets(4, 0, 4, 0));
        return climate;
    }

    private VBox createEquipmentEditor() {
        Label help = new Label("Uma linha por item. Ex.: 2 x Andaime tubular");
        help.getStyleClass().add("rdo-help");
        VBox editor = new VBox(7, help, equipment);
        VBox.setVgrow(equipment, Priority.ALWAYS);
        return editor;
    }

    private VBox createTextSection(String title, TextArea area) {
        return rdoSection(title, area);
    }

    private VBox createLegacyFormPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("dashboard-panel");
        panel.setPadding(new Insets(18));
        panel.setPrefWidth(700);
        panel.setMaxWidth(Double.MAX_VALUE);

        Label heading = new Label("Preencher diário");
        heading.getStyleClass().add("panel-title");
        Label help = new Label("Registre o que aconteceu na obra durante o dia.");
        help.getStyleClass().add("page-subtitle");

        GridPane grid = formGrid();
        addRow(grid, 0, "Data *", date);
        addRow(grid, 1, "Clima", weather);
        addRow(grid, 2, "Atividades do dia *", activities);
        addRow(grid, 3, "Efetivo e funções", createWorkforceEditor());
        addRow(grid, 4, "Observações", notes);
        addRow(grid, 5, "Intercorrências", incidents);

        Button newButton = new Button("Novo diário");
        newButton.getStyleClass().add("secondary-button");
        newButton.setOnAction(event -> clearForm());
        Button saveButton = new Button("Salvar diário");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(event -> save());
        HBox actions = new HBox(10, newButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        panel.getChildren().addAll(heading, help, grid, actions);
        return panel;
    }

    private VBox createWorkforceEditor() {
        functionSelector.setItems(FXCollections.observableArrayList(FUNCTIONS));
        functionSelector.setEditable(true);
        functionSelector.setPromptText("Selecione a função");
        functionSelector.setPrefWidth(190);
        functionSelector.getSelectionModel().selectFirst();
        workforceQuantity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        workforceQuantity.setPrefWidth(82);

        Button add = new Button("Adicionar");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> addWorkforce());
        Button remove = new Button("Remover");
        remove.getStyleClass().add("secondary-button");
        remove.setOnAction(event -> removeWorkforce());
        HBox controls = new HBox(8, functionSelector, workforceQuantity, add, remove);
        controls.setAlignment(Pos.CENTER_LEFT);

        if (workforceTable.getColumns().isEmpty()) {
            workforceTable.setItems(workforceItems);
            workforceTable.setPlaceholder(new Label("Nenhum profissional adicionado"));
            workforceTable.setPrefHeight(135);
            workforceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            workforceTable.getColumns().addAll(
                    column("Quantidade", item -> Integer.toString(item.quantity())),
                    column("Função", WorkforceItem::function));
        }
        VBox editor = new VBox(8, controls, workforceTable);
        editor.setPrefWidth(500);
        return editor;
    }

    private void addWorkforce() {
        String function = functionSelector.getEditor().getText().trim();
        if (function.isBlank()) {
            UiAlerts.info("Efetivo", "Selecione ou informe uma função.");
            return;
        }
        int quantity = workforceQuantity.getValue();
        workforceItems.add(new WorkforceItem(quantity, function));
        functionSelector.getEditor().clear();
        workforceQuantity.getValueFactory().setValue(1);
    }

    private void removeWorkforce() {
        WorkforceItem selectedItem = workforceTable.getSelectionModel().getSelectedItem();
        if (selectedItem != null) workforceItems.remove(selectedItem);
    }

    private VBox createHistoryPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("dashboard-panel");
        panel.setPadding(new Insets(18));
        Label heading = new Label("Diários registrados");
        heading.getStyleClass().add("panel-title");
        Label help = new Label("Selecione um diário para editar ou abrir o documento para impressão.");
        help.getStyleClass().add("page-subtitle");
        Button editButton = new Button("Editar diário selecionado");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> editSelected());
        Button openButton = new Button("Abrir RDO");
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(event -> openPrintable());
        Button printButton = new Button("Imprimir RDO");
        printButton.getStyleClass().add("secondary-button");
        printButton.setOnAction(event -> printPrintable());
        HBox actions = new HBox(10, editButton, openButton, printButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(table, Priority.ALWAYS);
        panel.getChildren().addAll(heading, help, actions, table);
        return panel;
    }

    private void configureTable() {
        table.setPlaceholder(new Label("Nenhum diário registrado para a obra ativa"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(
                column("Data", item -> DateFormats.format(item.data())),
                column("Clima", item -> value(item.clima())),
                column("Atividades", item -> summary(item.atividades())),
                column("Equipamentos", item -> summary(item.equipamentos())),
                column("Efetivo e funções", item -> summary(item.efetivo())),
                column("Intercorrências", item -> summary(item.intercorrencias())));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> load(newValue));
    }

    private void editSelected() {
        DiarioObra diario = table.getSelectionModel().getSelectedItem();
        if (diario == null) {
            UiAlerts.info("Diário de obra", "Selecione um diário para editar.");
            return;
        }
        load(diario);
        sections.getSelectionModel().select(0);
    }

    private void openPrintable() {
        Path file = createSelectedRdoFile();
        if (file == null) return;
        try {
            if (!openFile(file)) {
                UiAlerts.info("RDO gerado", "O arquivo foi salvo em:\n" + file
                        + "\nAbra-o manualmente e use Ctrl+P para imprimir.");
            }
        } catch (IOException | RuntimeException exception) {
            UiAlerts.error("NÃ£o foi possÃ­vel abrir o RDO", message(exception));
        }
    }

    private void printPrintable() {
        Path file = createSelectedRdoFile();
        if (file == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.PRINT)) {
                    try {
                        desktop.print(file.toFile());
                        return;
                    } catch (IOException ignored) {
                        // Abre o RDO no navegador para impressão manual.
                    }
                }
            }
            if (openFile(file)) {
                UiAlerts.info("RDO aberto", "A impressÃ£o direta nÃ£o estÃ¡ disponÃ­vel neste computador. Use Ctrl+P no navegador.");
            } else {
                UiAlerts.info("RDO gerado", "O arquivo foi salvo em:\n" + file
                        + "\nAbra-o manualmente e use Ctrl+P para imprimir.");
            }
        } catch (IOException | RuntimeException exception) {
            UiAlerts.error("NÃ£o foi possÃ­vel imprimir o RDO", message(exception));
        }
    }

    private Path createSelectedRdoFile() {
        DiarioObra diario = table.getSelectionModel().getSelectedItem();
        if (diario == null) {
            UiAlerts.info("Diário de obra", "Selecione um RDO para abrir ou imprimir.");
            return null;
        }
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Diário de obra", "Cadastre ou ative uma obra antes de gerar o RDO.");
            return null;
        }
        try {
            return exportRdoHtml(obra, diario);
        } catch (IOException | RuntimeException exception) {
            UiAlerts.error("Não foi possível gerar o RDO", message(exception));
            return null;
        }
    }

    private void legacyCreateSelectedRdoFile() {
        DiarioObra diario = table.getSelectionModel().getSelectedItem();
        if (diario == null) {
            UiAlerts.info("Diário de obra", "Selecione um diário para abrir o arquivo.");
            return;
        }
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Diário de obra", "Cadastre ou ative uma obra antes de abrir o documento.");
            return;
        }
        try {
            Path file = exportRdoHtml(obra, diario);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                UiAlerts.info("Arquivo gerado", "O documento foi salvo em:\n" + file);
            }
        } catch (IOException | RuntimeException exception) {
            UiAlerts.error("Não foi possível abrir o documento", message(exception));
        }
    }

    private boolean openFile(Path file) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(file.toUri());
                    return true;
                } catch (IOException ignored) {
                    // Tenta a abertura do arquivo pelo sistema operacional.
                }
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    desktop.open(file.toFile());
                    return true;
                } catch (IOException ignored) {
                    // Tenta o Explorer no Windows abaixo.
                }
            }
        }
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win")) {
            new ProcessBuilder("explorer.exe", file.toAbsolutePath().toString()).start();
            return true;
        }
        return false;
    }

    private Path exportRdoHtml(Obra obra, DiarioObra diario) throws IOException {
        Path exports = context.databaseManager().getRootDirectory().resolve("exports");
        Files.createDirectories(exports);
        String workCode = safeFilePart(obra.codigo());
        Path file = exports.resolve("rdo_" + workCode + "_" + FILE_DATE.format(diario.data()) + ".html");
        String climate = value(diario.clima());
        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <title>RDO - %s - %s</title>
                    <style>
                        @page { size: A4 portrait; margin: 8mm; }
                        * { box-sizing: border-box; }
                        body { margin: 0; color: #243447; font-family: Arial, sans-serif; font-size: 10px; }
                        .rdo { border: 1px solid #91a4b5; border-radius: 4px; overflow: hidden; }
                        .header { display: grid; grid-template-columns: 1fr 2fr 1fr; min-height: 58px; background: #f5f8fb; border-bottom: 2px solid #1f4e79; }
                        .brand, .doc-number { display: flex; align-items: center; justify-content: center; text-align: center; font-weight: bold; }
                        .brand { color: #1f4e79; font-size: 14px; letter-spacing: .5px; border-right: 1px solid #b8c6d2; }
                        .doc-number { color: #526273; font-size: 10px; border-left: 1px solid #b8c6d2; }
                        .title { text-align: center; padding: 10px 6px; font-weight: bold; color: #17365d; font-size: 13px; }
                        .title small { display: block; margin-top: 5px; color: #374151; font-size: 9px; font-weight: normal; }
                        .meta { display: grid; grid-template-columns: 1.7fr 1fr 1fr; border-bottom: 1px solid #91a4b5; }
                        .field { min-height: 31px; padding: 5px 7px; border-right: 1px solid #c5d0da; border-bottom: 1px solid #c5d0da; }
                        .field:nth-child(3n) { border-right: 0; }
                        .field:nth-last-child(-n+3) { border-bottom: 0; }
                        .label { display: block; font-size: 8px; font-weight: bold; text-transform: uppercase; color: #374151; margin-bottom: 3px; }
                        .climate { border-bottom: 1px solid #91a4b5; padding: 7px; text-align: center; color: #526273; }
                        .climate strong { display: block; background: #1f4e79; color: white; margin: -7px -7px 7px; padding: 4px; font-size: 8px; letter-spacing: .3px; }
                        .body { display: grid; grid-template-columns: 48fr 52fr; }
                        .left, .right { min-width: 0; }
                        .left { border-right: 1px solid #91a4b5; }
                        .block { border-bottom: 1px solid #91a4b5; }
                        .block:last-child { border-bottom: 0; }
                        .block h2 { margin: 0; padding: 6px; background: #e9f0f6; color: #1f4e79; font-size: 9px; letter-spacing: .25px; border-bottom: 1px solid #b8c6d2; }
                        .content { min-height: 55px; padding: 7px; white-space: normal; line-height: 1.4; }
                        .activities { min-height: 210px; }
                        .observations { min-height: 105px; }
                        .incidents { min-height: 80px; }
                        table { width: 100%%; border-collapse: collapse; table-layout: fixed; }
                        th, td { padding: 3px 5px; border-bottom: 1px solid #d1d5db; vertical-align: top; word-wrap: break-word; }
                        th { text-align: left; font-size: 8px; }
                        td:first-child, th:first-child { width: 22mm; text-align: center; }
                        .signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; padding: 30px 55px 14px; }
                        .signature { width: 68%%; justify-self: center; border-top: 1px solid #526273; padding-top: 5px; text-align: center; font-size: 9px; color: #34495e; }
                        .footer { border-top: 1px solid #c5d0da; padding: 6px; text-align: center; font-size: 8px; color: #697586; background: #f8fafc; }
                        @media print { .no-print { display: none; } }
                    </style>
                </head>
                <body>
                    <div class="rdo">
                        <div class="header">
                            <div class="brand">PLANEJAMENTO<br>RIO CLARO</div>
                            <div class="title">RELATÓRIO DIÁRIO DE OBRA (RDO)<small>Acompanhamento da execução, equipe, equipamentos e ocorrências</small></div>
                            <div class="doc-number">REGISTRO Nº<br>%s</div>
                        </div>
                        <div class="meta">
                            <div class="field"><span class="label">Obra</span>%s</div>
                            <div class="field"><span class="label">Código</span>%s</div>
                            <div class="field"><span class="label">Data do RDO</span>%s</div>
                            <div class="field"><span class="label">Endereço</span>%s</div>
                            <div class="field"><span class="label">Responsável pela obra</span>%s</div>
                            <div class="field"><span class="label">Data de emissão</span>%s</div>
                        </div>
                        <div class="climate"><strong>CONDIÇÃO CLIMÁTICA</strong>%s &nbsp;&nbsp; %s &nbsp;&nbsp; %s &nbsp;&nbsp; %s &nbsp;&nbsp; %s</div>
                        <div class="body">
                            <div class="left">
                                <div class="block"><h2>EFETIVO E FUNÇÕES</h2><div class="content">%s</div></div>
                                <div class="block"><h2>EQUIPAMENTOS E FERRAMENTAS</h2><div class="content">%s</div></div>
                            </div>
                            <div class="right">
                                <div class="block"><h2>ATIVIDADES E SERVIÇOS EXECUTADOS</h2><div class="content activities">%s</div></div>
                                <div class="block"><h2>OBSERVAÇÕES</h2><div class="content observations">%s</div></div>
                                <div class="block"><h2>INTERCORRÊNCIAS E PROVIDÊNCIAS</h2><div class="content incidents">%s</div></div>
                            </div>
                        </div>
                        <div class="signatures">
                            <div class="signature">Engenheiro responsável<br><br>Nome e assinatura</div>
                            <div class="signature">Fiscal da obra<br><br>Nome e assinatura</div>
                        </div>
                        <div class="footer">Documento gerado pelo Planejamento Rio Claro. Conferir os dados antes da assinatura.</div>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(diario.data().toString()), escapeHtml(obra.nome()), String.valueOf(diario.id()),
                htmlText(obra.nome()), htmlText(obra.codigo()), htmlText(diario.data().format(DISPLAY_DATE)),
                htmlText(obra.endereco()), htmlText(obra.responsavel()), htmlText(diario.data().format(DISPLAY_DATE)),
                weatherMark("Ensolarado", climate), weatherMark("Nublado", climate), weatherMark("Chuvoso", climate),
                weatherMark("Vento forte", climate), weatherOtherMark(climate),
                workforceRdoHtml(diario.efetivo()), equipmentRdoHtml(diario.equipamentos()),
                htmlText(diario.atividades()), htmlText(diario.observacoes()), htmlText(diario.intercorrencias()));
        html = customizeRdoHeader(html, obra, diario);
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return file;
    }

    private String customizeRdoHeader(String html, Obra obra, DiarioObra diario) {
        String header = """
                <div class="header">
                    <div class="brand"><span class="brand-mark">✕</span><span class="brand-name">INTEGRAL<small>construtora</small></span></div>
                    <div class="title">ELABORAÇÃO DE PROJETO EXECUTIVO E EXECUÇÃO DE OBRA DE IMPLANTAÇÃO
                        <div class="project-name" contenteditable="true" title="Clique para preencher antes de imprimir">NOME DA OBRA</div>
                    </div>
                </div>
                """;
        header = """
                <div class="header">
                    <div class="brand"><span class="brand-mark">\u2715</span><span class="brand-name">INTEGRAL<small>construtora</small></span></div>
                    <div class="title">ELABORA\u00C7\u00C3O DE PROJETO EXECUTIVO E EXECU\u00C7\u00C3O DE OBRA DE IMPLANTA\u00C7\u00C3O
                        <div class="project-name" contenteditable="true" title="Clique para preencher antes de imprimir">NOME DA OBRA</div>
                    </div>
                </div>
                """;
        String meta = """
                <div class="meta">
                    <div class="field contractor"><span class="label">Contratada</span>INTEGRAL CONSTRUTORA E EMPREENDIMENTOS LTDA</div>
                    <div class="field"><span class="label">Data de início</span>%s</div>
                    <div class="field"><span class="label">Data entrega</span>%s</div>
                    <div class="field report"><span class="label">Relatório nº</span><strong>%s</strong></div>
                    <div class="field responsible"><span class="label">Responsável</span>%s</div>
                    <div class="field"><span class="label">Data ocorrência</span>%s</div>
                </div>
                <div class="rdo-label">RELATÓRIO DIÁRIO DE OBRA (RDO)</div>
                """.formatted(
                dateText(obra.dataInicio()), dateText(obra.previsaoTermino()), String.valueOf(diario.id()),
                htmlText(obra.responsavel()), htmlText(diario.data().format(DISPLAY_DATE)));
        meta = """
                <div class="meta">
                    <div class="field contractor"><span class="label">Contratada</span>INTEGRAL CONSTRUTORA E EMPREENDIMENTOS LTDA</div>
                    <div class="field"><span class="label">Data de in\u00EDcio</span>%s</div>
                    <div class="field"><span class="label">Data entrega</span>%s</div>
                    <div class="field report"><span class="label">Relat\u00F3rio n\u00BA</span><strong>%s</strong></div>
                    <div class="field responsible"><span class="label">Respons\u00E1vel</span>%s</div>
                    <div class="field"><span class="label">Data ocorr\u00EAncia</span>%s</div>
                </div>
                <div class="rdo-label">RELAT\u00D3RIO DI\u00C1RIO DE OBRA (RDO)</div>
                """.formatted(
                dateText(obra.dataInicio()), dateText(obra.previsaoTermino()), String.valueOf(diario.id()),
                htmlText(obra.responsavel()), htmlText(diario.data().format(DISPLAY_DATE)));
        String styles = """
                        .header { grid-template-columns: 1fr 3fr; min-height: 61px; }
                        .brand { display: flex; gap: 5px; align-items: center; justify-content: center; border-right: 1px solid #b8c6d2; }
                        .brand-mark { color: #36a852; font-size: 23px; font-weight: bold; line-height: 1; }
                        .brand-name { color: #1f2937; font-size: 14px; letter-spacing: .6px; text-align: left; }
                        .brand-name small { display: block; color: #7a8794; font-size: 8px; letter-spacing: 0; font-weight: normal; }
                        .title { display: flex; flex-direction: column; justify-content: center; padding: 8px 18px; color: #172235; font-size: 9px; line-height: 1.3; letter-spacing: .2px; }
                        .project-name { margin-top: 4px; padding: 3px 8px; border-bottom: 1px solid #526273; color: #1f4e79; font-size: 10px; font-weight: bold; text-align: center; outline: 0; }
                        .project-name:focus { background: #fff8df; }
                        .meta { grid-template-columns: 2.2fr 1fr 1fr .8fr; }
                        .field { min-height: 29px; }
                        .contractor { grid-column: span 1; }
                        .responsible { grid-column: span 2; }
                        .report { text-align: center; }
                        .report strong { color: #1f4e79; font-size: 15px; }
                        .rdo-label { border-bottom: 1px solid #91a4b5; padding: 5px; color: #1f4e79; font-size: 11px; font-weight: bold; text-align: center; letter-spacing: .35px; }
                """;
        html = html.replace("</style>", styles + "</style>");
        html = replaceHtmlBlock(html, "<div class=\"header\">", "<div class=\"meta\">", header + "<div class=\"meta\">");
        return replaceHtmlBlock(html, "<div class=\"meta\">", "<div class=\"climate\">", meta + "<div class=\"climate\">");
    }

    private String replaceHtmlBlock(String html, String startMarker, String endMarker, String replacement) {
        int start = html.indexOf(startMarker);
        int end = html.indexOf(endMarker, start);
        if (start < 0 || end < 0) return html;
        return html.substring(0, start) + replacement + html.substring(end + endMarker.length());
    }

    private String dateText(LocalDate date) {
        return date == null ? "Não informado" : date.format(DISPLAY_DATE);
    }

    private String weatherMark(String label, String climate) {
        return climate.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))
                ? "☒ " + label : "☐ " + label;
    }

    private String weatherOtherMark(String climate) {
        String custom = climate.replaceAll("(?i)ensolarado|nublado|chuvoso|vento forte|[,;]", " ")
                .replaceAll("\\s+", " ").trim();
        return custom.isBlank() ? "☐ Outra" : "☒ Outra: " + escapeHtml(custom);
    }

    private String workforceRdoHtml(String value) {
        return itemRdoHtml(value, "Função");
    }

    private String equipmentRdoHtml(String value) {
        return itemRdoHtml(value, "Equipamento / ferramenta");
    }

    private String itemRdoHtml(String value, String descriptionHeader) {
        if (value == null || value.isBlank()) return "Não informado";
        StringBuilder html = new StringBuilder("<table><tr><th>Qtd.</th><th>")
                .append(descriptionHeader).append("</th></tr>");
        for (String item : value.split("[;\\n]+")) {
            String text = item.trim();
            if (text.isBlank()) continue;
            Matcher matcher = Pattern.compile("^(\\d+)\\s*[xX-]\\s*(.+)$").matcher(text);
            String quantity = matcher.matches() ? matcher.group(1) : "1";
            String description = matcher.matches() ? matcher.group(2).trim() : text;
            html.append("<tr><td>").append(escapeHtml(quantity)).append("</td><td>")
                    .append(escapeHtml(description)).append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    private Path exportHtml(Obra obra, DiarioObra diario) throws IOException {
        Path exports = context.databaseManager().getRootDirectory().resolve("exports");
        Files.createDirectories(exports);
        String workCode = safeFilePart(obra.codigo());
        Path file = exports.resolve("diario_obra_" + workCode + "_" + FILE_DATE.format(diario.data()) + ".html");
        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <title>Diário de Obra - %s - %s</title>
                    <style>
                        @page { size: A4; margin: 18mm 16mm; }
                        * { box-sizing: border-box; }
                        body { margin: 0; color: #1f2937; font-family: Arial, sans-serif; font-size: 12px; }
                        .header { border-bottom: 3px solid #1f4e79; padding-bottom: 12px; margin-bottom: 14px; }
                        .header h1 { color: #1f4e79; font-size: 22px; letter-spacing: .8px; margin: 0 0 8px; }
                        .header p { margin: 3px 0; color: #526273; }
                        .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 18px; margin-bottom: 16px; }
                        .meta div { border-bottom: 1px solid #d6dde5; padding: 5px 0; }
                        .label { color: #526273; font-weight: bold; text-transform: uppercase; font-size: 10px; }
                        .section { border: 1px solid #ccd6e0; border-radius: 4px; margin: 10px 0; page-break-inside: avoid; }
                        .section h2 { background: #edf3f8; color: #1f4e79; font-size: 12px; margin: 0; padding: 8px 10px; }
                        .content { min-height: 48px; padding: 11px; line-height: 1.5; white-space: normal; }
                        .signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-top: 36px; padding: 0 55px; page-break-inside: avoid; }
                        .signature { width: 68%%; justify-self: center; border-top: 1px solid #526273; padding-top: 7px; text-align: center; }
                        .footer { border-top: 1px solid #d6dde5; color: #697586; font-size: 10px; margin-top: 30px; padding-top: 8px; text-align: center; }
                        @media print { .no-print { display: none; } }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>DIÁRIO DE OBRA</h1>
                        <p>Registro diário de acompanhamento e execução dos serviços</p>
                    </div>
                    <div class="meta">
                        <div><span class="label">Obra</span><br>%s</div>
                        <div><span class="label">Código da obra</span><br>%s</div>
                        <div><span class="label">Endereço</span><br>%s</div>
                        <div><span class="label">Data do registro</span><br>%s</div>
                        <div><span class="label">Responsável da obra</span><br>%s</div>
                        <div><span class="label">Condição climática</span><br>%s</div>
                    </div>
                    <div class="section"><h2>1. ATIVIDADES E SERVIÇOS EXECUTADOS</h2><div class="content">%s</div></div>
                    <div class="section"><h2>2. EFETIVO E FUNÇÕES</h2><div class="content">%s</div></div>
                    <div class="section"><h2>3. OBSERVAÇÕES</h2><div class="content">%s</div></div>
                    <div class="section"><h2>4. INTERCORRÊNCIAS E PROVIDÊNCIAS</h2><div class="content">%s</div></div>
                    <div class="signatures">
                        <div class="signature">Engenheiro responsável<br><br>Nome e assinatura</div>
                        <div class="signature">Fiscal da obra<br><br>Nome e assinatura</div>
                    </div>
                    <div class="footer">Documento de registro diário da obra — preencher, conferir e assinar pelos responsáveis.</div>
                </body>
                </html>
                """.formatted(
                escapeHtml(obra.nome()), escapeHtml(diario.data().toString()), escapeHtml(obra.nome()), escapeHtml(obra.codigo()),
                htmlText(obra.endereco()), htmlText(diario.data().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                htmlText(obra.responsavel()), htmlText(diario.clima()), htmlText(diario.atividades()),
                workforceHtml(diario.efetivo()), htmlText(diario.observacoes()), htmlText(diario.intercorrencias()));
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return file;
    }

    private String workforceHtml(String value) {
        if (value == null || value.isBlank()) return "Não informado";
        StringBuilder html = new StringBuilder("<ul>");
        for (String item : value.split("[;\\n]+")) {
            if (!item.isBlank()) html.append("<li>").append(escapeHtml(item.trim())).append("</li>");
        }
        return html.append("</ul>").toString();
    }

    private String htmlText(String value) {
        if (value == null || value.isBlank()) return "Não informado";
        return escapeHtml(value).replace("\r\n", "<br>").replace("\n", "<br>");
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String safeFilePart(String value) {
        return value == null || value.isBlank() ? "obra" : value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void refresh() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            table.setItems(FXCollections.observableArrayList());
            return;
        }
        List<DiarioObra> diarios = context.diarioObraService().listar(obra.id());
        table.setItems(FXCollections.observableArrayList(diarios));
        if (selected != null) {
            diarios.stream().filter(item -> item.id().equals(selected.id())).findFirst()
                    .ifPresent(item -> table.getSelectionModel().select(item));
        }
    }

    private void save() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) {
            UiAlerts.info("Diário de obra", "Cadastre ou ative uma obra antes de salvar o diário.");
            return;
        }
        try {
            selected = context.diarioObraService().salvar(obra.id(), selected == null ? null : selected.id(), date.getValue(),
                    activities.getText(), formatWorkforce(), equipment.getText(), formatWeather(), notes.getText(), incidents.getText());
            refresh();
            UiAlerts.info("Diário de obra", "Diário salvo com sucesso.");
        } catch (ValidationException exception) {
            UiAlerts.error("Validação", exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Não foi possível salvar", message(exception));
        }
    }

    private void load(DiarioObra diario) {
        if (diario == null) return;
        selected = diario;
        date.setValue(diario.data());
        loadWeather(diario.clima());
        activities.setText(value(diario.atividades()));
        loadWorkforce(diario.efetivo());
        equipment.setText(value(diario.equipamentos()));
        notes.setText(value(diario.observacoes()));
        incidents.setText(value(diario.intercorrencias()));
    }

    private String formatWorkforce() {
        return workforceItems.stream()
                .map(item -> item.quantity() + " x " + item.function())
                .reduce((first, second) -> first + "; " + second)
                .orElse("");
    }

    private String formatWeather() {
        StringBuilder result = new StringBuilder();
        appendWeather(result, sunny, "Ensolarado");
        appendWeather(result, cloudy, "Nublado");
        appendWeather(result, rainy, "Chuvoso");
        appendWeather(result, windy, "Vento forte");
        if (!otherWeather.getText().isBlank()) appendWeather(result, otherWeather.getText().trim());
        return result.toString();
    }

    private void appendWeather(StringBuilder result, CheckBox option, String label) {
        if (option.isSelected()) appendWeather(result, label);
    }

    private void appendWeather(StringBuilder result, String label) {
        if (result.length() > 0) result.append(", ");
        result.append(label);
    }

    private void loadWeather(String value) {
        String climate = value(value);
        String normalized = climate.toLowerCase(Locale.ROOT);
        sunny.setSelected(normalized.contains("ensolarado"));
        cloudy.setSelected(normalized.contains("nublado"));
        rainy.setSelected(normalized.contains("chuvoso"));
        windy.setSelected(normalized.contains("vento forte"));
        String custom = climate.replaceAll("(?i)ensolarado|nublado|chuvoso|vento forte|[,;]", " ")
                .replaceAll("\\s+", " ").trim();
        otherWeather.setText(custom);
    }

    private void loadWorkforce(String value) {
        workforceItems.clear();
        if (value == null || value.isBlank()) return;
        for (String item : value.split("[;\\n]+")) {
            String text = item.trim();
            if (text.isBlank()) continue;
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^(\\d+)\\s*[xX-]\\s*(.+)$").matcher(text);
            if (matcher.matches()) {
                workforceItems.add(new WorkforceItem(Integer.parseInt(matcher.group(1)), matcher.group(2).trim()));
            } else {
                workforceItems.add(new WorkforceItem(1, text));
            }
        }
    }

    private void clearForm() {
        selected = null;
        table.getSelectionModel().clearSelection();
        Obra obra = context.obraService().obraAtiva();
        date.setValue(obra == null ? LocalDate.now() : nextAvailableDate(obra.id()));
        weather.getEditor().clear();
        sunny.setSelected(false);
        cloudy.setSelected(false);
        rainy.setSelected(false);
        windy.setSelected(false);
        otherWeather.clear();
        activities.clear();
        workforceItems.clear();
        equipment.clear();
        notes.clear();
        incidents.clear();
        if (sections != null) sections.getSelectionModel().select(0);
    }

    private LocalDate nextAvailableDate(long obraId) {
        LocalDate candidate = LocalDate.now();
        List<DiarioObra> diarios = context.diarioObraService().listar(obraId);
        while (containsDate(diarios, candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private boolean containsDate(List<DiarioObra> diarios, LocalDate date) {
        for (DiarioObra diario : diarios) {
            if (date.equals(diario.data())) return true;
        }
        return false;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        return grid;
    }

    private void addRow(GridPane grid, int row, String label, Node input) {
        Label text = new Label(label);
        text.getStyleClass().add("form-label");
        grid.add(text, 0, row);
        grid.add(input, 1, row);
        if (input instanceof TextInputControl control) {
            control.setPrefWidth(290);
            if (input instanceof TextArea area) area.setWrapText(true);
        } else if (input instanceof ComboBox<?> combo) {
            combo.setPrefWidth(290);
        } else if (input instanceof DatePicker picker) {
            picker.setPrefWidth(290);
        }
    }

    private TextArea textArea(String prompt, int rows) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setPrefRowCount(rows);
        area.setWrapText(true);
        return area;
    }

    private <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private String summary(String text) {
        if (text == null || text.isBlank()) return "-";
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 90 ? compact.substring(0, 87) + "..." : compact;
    }

    private String message(Throwable exception) {
        return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage();
    }

    private record WorkforceItem(int quantity, String function) {
    }
}
