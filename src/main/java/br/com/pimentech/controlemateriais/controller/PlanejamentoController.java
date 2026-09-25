package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.model.*;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.service.FrentesService;
import br.com.pimentech.controlemateriais.service.PlanejamentoDiarioService;
import br.com.pimentech.controlemateriais.util.DateFormats;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Tela de operação diária; o cronograma financeiro anterior permanece apenas no banco. */
public final class PlanejamentoController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final ApplicationContext context;
    private final PlanejamentoDiarioService service;
    private final FrentesService frentes;
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final ComboBox<AreaObra> areaFilter = new ComboBox<>();
    private final ComboBox<DisciplinaObra> disciplineFilter = new ComboBox<>();
    private final ComboBox<ServicoArea> serviceFilter = new ComboBox<>();
    private final ComboBox<SituacaoServico> statusFilter = new ComboBox<>();
    private final ComboBox<AreaObra> entryArea = new ComboBox<>();
    private final ComboBox<ServicoArea> entryService = new ComboBox<>();
    private final FlowPane entryElements = new FlowPane(8, 5);
    private final List<CheckBox> entryElementChecks = new ArrayList<>();
    private final TextField quantity = field("Quantidade executada");
    private final TextField workers = field("Nº de trabalhadores");
    private final TextField hours = field("Horas por trabalhador (opcional)");
    private final TextField team = field("Equipe ou responsável (opcional)");
    private final TextArea note = new TextArea();
    private final ComboBox<TipoOcorrenciaServico> occurrenceType = new ComboBox<>();
    private final TextField occurrenceDescription = field("Descreva o que aconteceu");
    private final ComboBox<Material> occurrenceMaterial = new ComboBox<>();
    private final Button saveEntry = new Button("Salvar produção");
    private final Label entryHint = new Label("Selecione a frente e registre a produção do dia.");
    private final Label daySummary = new Label();
    private final Label details = new Label("Selecione um serviço para ver produtividade e comparações.");
    private final ListView<String> pending = new ListView<>();
    private final TableView<ResumoServicoDia> dayTable = new TableView<>();
    private final TableView<AreaObra> areaTable = new TableView<>();
    private final TableView<DisciplinaObra> disciplineTable = new TableView<>();
    private final TableView<ServicoArea> serviceTable = new TableView<>();
    private final Label catalogDetails = new Label("Selecione um serviço para ver pré-requisitos e próximas frentes.");
    private final TableView<ProducaoDiaria> historyTable = new TableView<>();
    private final TableView<OcorrenciaServico> occurrenceTable = new TableView<>();
    private final TableView<StatusChange> statusHistoryTable = new TableView<>();
    private final TableView<FrentesService.ResumoVinculo> linksTable = new TableView<>();
    private final TableView<FrentesService.EstadoTrecho> scopesTable = new TableView<>();
    private final TableView<LiberacaoTrecho> releasesTable = new TableView<>();
    private final ComboBox<SituacaoFrente> frontFilter = new ComboBox<>();
    private final Label frontDetails = new Label("Selecione um vínculo para conferir os trechos liberados e bloqueados.");
    private final CheckBox historyDateOnly = new CheckBox("Somente a data selecionada");
    private final Label workLabel = new Label();
    private TabPane tabs;
    private Node view;
    private Long editingProductionId;
    private Long lastWorkId;
    private boolean refreshing;
    private List<AreaObra> areas = List.of();
    private List<DisciplinaObra> disciplines = List.of();
    private List<ServicoArea> services = List.of();
    private List<ElementoServico> elements = List.of();
    private List<ElementoProducao> productionElements = List.of();
    private List<ProducaoDiaria> productions = List.of();
    private List<OcorrenciaServico> occurrences = List.of();
    private List<FrentesService.ResumoVinculo> frontSummaries = List.of();
    private List<TrechoVinculo> allScopes = List.of();
    private List<LiberacaoTrecho> releaseHistory = List.of();

    public PlanejamentoController(ApplicationContext context) {
        this.context = context;
        this.service = context.planejamentoDiarioService();
        this.frentes = context.frentesService();
    }

    public Node createView() {
        if (view != null) { refresh(); return view; }
        VBox page = new VBox(14);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(26));
        Label title = new Label("Planejamento e acompanhamento diário");
        title.getStyleClass().add("page-title");
        workLabel.getStyleClass().add("page-subtitle");
        DateFormats.configure(date);
        date.valueProperty().addListener((a, b, c) -> refreshView());
        setupFilters();
        setupTables();
        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Visão do dia", dailyPane()),
                new Tab("Áreas, disciplinas e serviços", catalogPane()), new Tab("Frentes e liberações", frontsPane()),
                new Tab("Histórico e ocorrências", historyPane()));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        page.getChildren().addAll(title, workLabel, filterBar(), tabs);
        view = page;
        refresh();
        return view;
    }

    private Node dailyPane() {
        VBox content = new VBox(12, entryPanel(), panel("Resumo do dia", daySummary),
                panel("Acompanhamento dos serviços", dayTable, details), panel("Pendências para o próximo dia", pending));
        content.setPadding(new Insets(8, 2, 18, 2));
        dayTable.setPrefHeight(300);
        pending.setPrefHeight(115);
        daySummary.setWrapText(true);
        details.setWrapText(true);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private Node filterBar() {
        areaFilter.setPromptText("Todas as áreas");
        disciplineFilter.setPromptText("Todas as disciplinas");
        serviceFilter.setPromptText("Todos os serviços");
        statusFilter.setPromptText("Todas as situações");
        areaFilter.setPrefWidth(200);
        disciplineFilter.setPrefWidth(170);
        serviceFilter.setPrefWidth(220);
        statusFilter.setPrefWidth(160);
        FlowPane bar = new FlowPane(10, 8, labeled("Data", date), labeled("Área", areaFilter),
                labeled("Disciplina", disciplineFilter), labeled("Serviço", serviceFilter), labeled("Situação do serviço", statusFilter),
                labeled(" ", button("Limpar filtros", this::clearFilters)));
        bar.setPrefWrapLength(920);
        return panel("Consultar o dia", bar);
    }

    private Node entryPanel() {
        entryArea.setPromptText("Escolha a área");
        entryService.setPromptText("Escolha o serviço");
        entryArea.setPrefWidth(230);
        entryService.setPrefWidth(270);
        entryArea.valueProperty().addListener((a, b, c) -> updateEntryServices());
        entryService.valueProperty().addListener((a, b, c) -> updateEntryElements());
        occurrenceType.getItems().setAll(TipoOcorrenciaServico.values());
        occurrenceType.setPromptText("Sem ocorrência");
        occurrenceType.valueProperty().addListener((a, b, c) -> updateOccurrenceInputs());
        updateOccurrenceInputs();
        occurrenceMaterial.setPromptText("Material cadastrado (opcional)");
        occurrenceMaterial.setMaxWidth(Double.MAX_VALUE);
        occurrenceMaterial.setCellFactory(list -> materialCell());
        occurrenceMaterial.setButtonCell(materialCell());
        note.setPromptText("Observações sobre a execução (opcional)");
        note.setPrefRowCount(2);
        quantity.setPrefWidth(165);
        workers.setPrefWidth(160);
        hours.setPrefWidth(190);
        FlowPane first = new FlowPane(10, 8, labeled("Área *", entryArea), labeled("Serviço *", entryService),
                labeled("Feito no dia *", quantity), labeled("Trabalhadores *", workers));
        first.setPrefWrapLength(800);
        HBox second = new HBox(10, labeled("Horas por pessoa", hours), labeled("Equipe / responsável", team));
        HBox.setHgrow(team, Priority.ALWAYS);
        team.setMaxWidth(Double.MAX_VALUE);
        HBox incident = new HBox(10, labeled("Ocorrência (opcional)", occurrenceType),
                labeled("Descrição", occurrenceDescription), labeled("Material", occurrenceMaterial));
        HBox.setHgrow(incident.getChildren().get(1), Priority.ALWAYS);
        occurrenceDescription.setMaxWidth(Double.MAX_VALUE);
        Button clear = new Button("Novo lançamento");
        clear.getStyleClass().add("secondary-button");
        clear.setOnAction(e -> clearEntry());
        saveEntry.getStyleClass().add("primary-button");
        saveEntry.setOnAction(e -> saveDay());
        entryHint.setWrapText(true);
        return panel("Lançamento rápido", entryHint, first, labeled("Elementos ou trechos atendidos", entryElements),
                second, labeled("Observações", note), incident,
                new HBox(10, saveEntry, clear));
    }

    private Node catalogPane() {
        Button newArea = button("Nova área", () -> editArea(null));
        Button editArea = button("Editar área", () -> editArea(areaTable.getSelectionModel().getSelectedItem()));
        Button newDiscipline = button("Nova disciplina", this::createDiscipline);
        Button newService = button("Novo serviço", () -> editService(null));
        Button editService = button("Editar serviço", () -> editService(serviceTable.getSelectionModel().getSelectedItem()));
        VBox content = new VBox(14, panel("1. Onde: áreas e locais", new HBox(8, newArea, editArea), areaTable),
                panel("2. Qual tipo de trabalho: disciplinas", newDiscipline, disciplineTable),
                panel("3. O que será feito: serviços e elementos", new HBox(8, newService, editService), serviceTable,
                        catalogDetails));
        content.setPadding(new Insets(8, 2, 18, 2));
        areaTable.setPrefHeight(220);
        disciplineTable.setPrefHeight(160);
        serviceTable.setPrefHeight(320);
        catalogDetails.setWrapText(true);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private Node frontsPane() {
        frontFilter.setPromptText("Todas as frentes");
        frontFilter.getItems().setAll(SituacaoFrente.values());
        frontFilter.valueProperty().addListener((a, b, c) -> refreshFronts());
        Button add = button("Vincular serviços", this::createLink);
        Button release = button("Confirmar liberação do trecho", () -> confirmScope(true));
        Button block = button("Registrar bloqueio do trecho", () -> confirmScope(false));
        Button clear = button("Limpar situação", () -> frontFilter.setValue(null));
        linksTable.getSelectionModel().selectedItemProperty().addListener((a, b, c) -> showLink(c));
        Label steps = new Label("1. Vincule o serviço que libera ao serviço que depende.  2. Escolha o vínculo e o trecho.  3. Confirme a liberação.");
        steps.setWrapText(true);
        VBox content = new VBox(14, steps, panel("1. Dependências entre serviços",
                new FlowPane(9, 7, add, frontFilter, clear), linksTable, frontDetails),
                panel("2. Trechos deste vínculo — selecione um para confirmar", new FlowPane(9, 7, release, block), scopesTable),
                panel("3. Histórico das confirmações e bloqueios", releasesTable));
        content.setPadding(new Insets(8, 2, 18, 2));
        linksTable.setPrefHeight(260); scopesTable.setPrefHeight(190); releasesTable.setPrefHeight(210);
        frontDetails.setWrapText(true);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); return scroll;
    }

    private Node historyPane() {
        Button correct = button("Corrigir lançamento", this::correctSelected);
        Button newOccurrence = button("Registrar ocorrência", () -> editOccurrence(null));
        Button editOccurrence = button("Editar ocorrência", () -> editOccurrence(occurrenceTable.getSelectionModel().getSelectedItem()));
        Button resolve = button("Marcar resolvida", this::resolveSelected);
        historyDateOnly.selectedProperty().addListener((a, b, c) -> refreshHistory());
        VBox content = new VBox(14, panel("Produção registrada", new HBox(9, historyDateOnly, correct), historyTable),
                panel("Ocorrências e providências", new HBox(8, newOccurrence, editOccurrence, resolve), occurrenceTable),
                panel("Mudanças de situação dos serviços", statusHistoryTable));
        content.setPadding(new Insets(8, 2, 18, 2));
        historyTable.setPrefHeight(275);
        occurrenceTable.setPrefHeight(275);
        statusHistoryTable.setPrefHeight(190);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private void setupFilters() {
        configureAreaCombo(areaFilter);
        configureAreaCombo(entryArea);
        configureServiceCombo(serviceFilter);
        configureServiceCombo(entryService);
        statusFilter.getItems().setAll(SituacaoServico.values());
        areaFilter.valueProperty().addListener((a, b, c) -> { updateFilterServices(); refreshView(); });
        disciplineFilter.valueProperty().addListener((a, b, c) -> { updateFilterServices(); refreshView(); });
        serviceFilter.valueProperty().addListener((a, b, c) -> refreshView());
        statusFilter.valueProperty().addListener((a, b, c) -> refreshView());
    }

    private void setupTables() {
        column(dayTable, "Área", 165, r -> areaPath(r.servico().areaId()));
        column(dayTable, "Disciplina", 115, r -> disciplineName(r.servico().disciplinaId()));
        column(dayTable, "Serviço", 205, r -> r.servico().descricao());
        column(dayTable, "Frente", 110, r -> frontState(r.servico().id()));
        column(dayTable, "Hoje", 90, r -> measure(r.feitoNoDia(), r.servico().unidade()));
        column(dayTable, "Acumulado", 100, r -> measure(r.acumulado(), r.servico().unidade()));
        column(dayTable, "Saldo", 95, r -> measure(r.saldo(), r.servico().unidade()));
        column(dayTable, "%", 65, r -> format(r.percentual()) + "%");
        column(dayTable, "Situação", 110, r -> r.situacao().label());
        column(dayTable, "Alerta", 170, this::alertText);
        dayTable.getSelectionModel().selectedItemProperty().addListener((a, b, c) -> showDetails(c));
        dayTable.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(ResumoServicoDia item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(empty || item == null ? "" : item.atrasado() || item.situacao() == SituacaoServico.PARALISADO
                        ? "-fx-background-color: #fff1e9;" : item.semAvancoRegistrado() ? "-fx-background-color: #fff9e7;" : "");
            }
        });
        column(areaTable, "Tipo", 155, r -> r.tipo().label());
        column(areaTable, "Nome", 300, AreaObra::nome);
        column(areaTable, "Área superior", 250, r -> areaName(r.areaPaiId()));
        column(disciplineTable, "Disciplina", 280, DisciplinaObra::nome);
        column(serviceTable, "Área", 220, r -> areaPath(r.areaId()));
        column(serviceTable, "Disciplina", 160, r -> disciplineName(r.disciplinaId()));
        column(serviceTable, "Serviço", 260, ServicoArea::descricao);
        column(serviceTable, "Elementos / trechos", 190, r -> elementNames(r.id()));
        column(serviceTable, "Unidade", 90, ServicoArea::unidade);
        column(serviceTable, "Previsto", 105, r -> measure(r.quantidadePrevista(), r.unidade()));
        column(serviceTable, "Início", 100, r -> dateText(r.inicioPrevisto()));
        column(serviceTable, "Fim", 100, r -> dateText(r.fimPrevisto()));
        column(serviceTable, "Meta/dia", 115, r -> r.metaDiaria() == null ? "—" : measure(r.metaDiaria(), r.unidade()) + "/dia");
        column(serviceTable, "Equipe responsável", 170, r -> nullText(r.equipeResponsavel()));
        serviceTable.getSelectionModel().selectedItemProperty().addListener((a, b, c) -> showCatalogDetails(c));
        column(historyTable, "Data", 105, r -> dateText(r.data()));
        column(historyTable, "Área", 165, r -> areaOfService(r.servicoId()));
        column(historyTable, "Serviço", 220, r -> serviceName(r.servicoId()));
        column(historyTable, "Elementos atendidos", 170, r -> productionElementNames(r.id()));
        column(historyTable, "Quantidade", 115, r -> measure(r.quantidade(), unitOfService(r.servicoId())));
        column(historyTable, "Trabalhadores", 105, r -> Integer.toString(r.trabalhadores()));
        column(historyTable, "Horas/pessoa", 105, r -> r.horasPorTrabalhador() == null ? "—" : format(r.horasPorTrabalhador()) + " h");
        column(historyTable, "Equipe", 150, r -> nullText(r.equipe()));
        column(historyTable, "Observações", 230, r -> nullText(r.observacao()));
        historyTable.setOnMouseClicked(e -> { if (e.getClickCount() == 2) correctSelected(); });
        column(occurrenceTable, "Data", 105, r -> dateText(r.data()));
        column(occurrenceTable, "Área", 155, r -> areaOfService(r.servicoId()));
        column(occurrenceTable, "Serviço", 190, r -> serviceName(r.servicoId()));
        column(occurrenceTable, "Tipo", 180, r -> r.tipo().label());
        column(occurrenceTable, "Descrição", 260, OcorrenciaServico::descricao);
        column(occurrenceTable, "Material", 165, r -> materialName(r.materialId()));
        column(occurrenceTable, "Situação", 105, r -> r.resolvidaEm() == null ? "Aberta" : "Resolvida em " + dateText(r.resolvidaEm()));
        column(statusHistoryTable, "Data", 110, r -> dateText(r.data()));
        column(statusHistoryTable, "Área", 190, r -> areaOfService(r.servicoId()));
        column(statusHistoryTable, "Disciplina", 150, r -> {
            ServicoArea item = findService(r.servicoId()); return item == null ? "—" : disciplineName(item.disciplinaId());
        });
        column(statusHistoryTable, "Serviço", 280, r -> serviceName(r.servicoId()));
        column(statusHistoryTable, "Nova situação", 160, r -> r.situacao().label());
        column(linksTable, "Serviço que libera", 255, r -> serviceWithLocation(r.vinculo().origemServicoId()));
        column(linksTable, "Serviço que depende", 255, r -> serviceWithLocation(r.vinculo().destinoServicoId()));
        column(linksTable, "Liberação", 165, r -> r.situacao().label());
        column(linksTable, "Trechos", 110, r -> Integer.toString(r.trechos().size()));
        column(scopesTable, "Elemento / trecho", 205, r -> r.trecho().codigo());
        column(scopesTable, "Situação", 190, r -> r.liberado() ? "Liberado" : "Bloqueado");
        column(scopesTable, "Data", 110, r -> r.ultimaConfirmacao() == null ? "—" : dateText(r.ultimaConfirmacao().data()));
        column(scopesTable, "Responsável", 185, r -> r.ultimaConfirmacao() == null ? "—" : r.ultimaConfirmacao().responsavel());
        column(scopesTable, "Observação", 270, r -> r.ultimaConfirmacao() == null ? "—" : nullText(r.ultimaConfirmacao().observacao()));
        column(releasesTable, "Data", 110, r -> dateText(r.data()));
        column(releasesTable, "Origem → destino", 305, r -> linkNamesByScope(r.trechoId()));
        column(releasesTable, "Trecho", 130, r -> scopeName(r.trechoId()));
        column(releasesTable, "Situação", 135, r -> r.liberado() ? "Liberado" : "Bloqueado");
        column(releasesTable, "Responsável", 185, LiberacaoTrecho::responsavel);
        column(releasesTable, "Observação", 260, r -> nullText(r.observacao()));
    }

    private void refresh() {
        refreshing = true;
        Obra obra = context.obraService().obraAtiva();
        workLabel.setText(obra == null ? "Ative uma obra para começar." : "Obra ativa: " + obra.nome());
        if (!Objects.equals(lastWorkId, obra == null ? null : obra.id())) {
            lastWorkId = obra == null ? null : obra.id();
            areaFilter.setValue(null); disciplineFilter.setValue(null);
            serviceFilter.setValue(null); statusFilter.setValue(null); frontFilter.setValue(null);
            entryArea.setValue(null); clearEntry();
        }
        if (obra == null) {
            areas = List.of(); disciplines = List.of(); services = List.of(); elements = List.of();
            productionElements = List.of(); productions = List.of(); occurrences = List.of(); frontSummaries = List.of();
            allScopes = List.of(); releaseHistory = List.of();
            areaFilter.getItems().clear(); serviceFilter.getItems().clear();
            disciplineFilter.getItems().clear();
            entryArea.getItems().clear(); entryService.getItems().clear(); occurrenceMaterial.getItems().clear();
            dayTable.getItems().clear(); areaTable.getItems().clear(); disciplineTable.getItems().clear(); serviceTable.getItems().clear();
            linksTable.getItems().clear(); scopesTable.getItems().clear(); releasesTable.getItems().clear();
            statusHistoryTable.getItems().clear();
            historyTable.getItems().clear(); occurrenceTable.getItems().clear(); pending.getItems().clear();
            daySummary.setText("Nenhuma obra ativa.");
            refreshing = false;
            return;
        }
        areas = service.listarAreas(obra.id());
        disciplines = frentes.disciplinas(obra.id());
        services = service.listarServicos(obra.id());
        elements = service.listarElementos(obra.id());
        productionElements = service.listarElementosProducao(obra.id());
        productions = service.listarProducoes(obra.id());
        occurrences = service.listarOcorrencias(obra.id());
        allScopes = frentes.trechos(obra.id());
        releaseHistory = frentes.historico(obra.id());
        Long filterAreaId = id(areaFilter.getValue());
        Long filterDisciplineId = id(disciplineFilter.getValue());
        Long entryAreaId = id(entryArea.getValue());
        areaFilter.setItems(FXCollections.observableArrayList(areas));
        disciplineFilter.setItems(FXCollections.observableArrayList(disciplines));
        entryArea.setItems(FXCollections.observableArrayList(areas));
        areaFilter.setValue(findArea(filterAreaId));
        disciplineFilter.setValue(findDiscipline(filterDisciplineId));
        entryArea.setValue(findArea(entryAreaId));
        updateFilterServices();
        updateEntryServices();
        occurrenceMaterial.setItems(FXCollections.observableArrayList(context.materialService().listar(obra.id()).stream()
                .filter(m -> m.ativo() && m.tipo() == MaterialTipo.MATERIAL).toList()));
        areaTable.setItems(FXCollections.observableArrayList(areas));
        disciplineTable.setItems(FXCollections.observableArrayList(disciplines));
        serviceTable.setItems(FXCollections.observableArrayList(services));
        refreshing = false;
        refreshView();
    }

    private void refreshView() {
        if (refreshing) return;
        Obra obra = context.obraService().obraAtiva();
        if (obra == null || date.getValue() == null) return;
        VisaoPlanejamentoDia vision = service.visao(obra.id(), date.getValue());
        frontSummaries = frentes.resumo(obra.id(), date.getValue());
        List<ResumoServicoDia> shown = vision.servicos().stream().filter(this::matches).toList();
        dayTable.setItems(FXCollections.observableArrayList(shown));
        Map<String, Double> totals = new java.util.TreeMap<>();
        int fronts = 0, workersCount = 0, occurrenceCount = 0;
        for (ResumoServicoDia row : shown) {
            ProducaoDiaria today = productions.stream().filter(p -> p.servicoId() == row.servico().id() && p.data().equals(date.getValue())).findFirst().orElse(null);
            if (today != null) {
                workersCount += today.trabalhadores();
                if (today.quantidade() > 0) { fronts++; totals.merge(row.servico().unidade(), today.quantidade(), Double::sum); }
            }
            occurrenceCount += (int) occurrences.stream().filter(o -> o.servicoId() == row.servico().id() && o.data().equals(date.getValue())).count();
        }
        String productionText = totals.isEmpty() ? "Sem produção registrada" : totals.entrySet().stream()
                .map(e -> measure(e.getValue(), e.getKey())).reduce((a, b) -> a + " · " + b).orElse("");
        daySummary.setText("Frentes com avanço: " + fronts + "   |   Trabalhadores nos lançamentos (soma): " + workersCount
                + "   |   Ocorrências: " + occurrenceCount + "\nProdução: " + productionText
                + "\nAs quantidades são somadas somente dentro da mesma unidade.");
        List<String> pendingLines = new ArrayList<>(vision.pendencias().stream()
                .filter(p -> shown.stream().anyMatch(r -> r.servico().id() == p.servicoId()))
                .map(p -> p.area() + " · " + p.servico() + " — " + p.descricao()).toList());
        for (FrentesService.ResumoVinculo link : frontSummaries) {
            if (shown.stream().noneMatch(r -> r.servico().id() == link.vinculo().destinoServicoId())) continue;
            for (FrentesService.EstadoTrecho scope : link.trechos()) if (!scope.liberado())
                pendingLines.add(areaOfService(link.vinculo().destinoServicoId()) + " · " + serviceName(link.vinculo().destinoServicoId())
                        + " — aguardando liberação de " + serviceName(link.vinculo().origemServicoId())
                        + " no trecho " + scope.trecho().codigo());
        }
        pending.setItems(FXCollections.observableArrayList(pendingLines));
        if (pending.getItems().isEmpty()) pending.getItems().add("Nenhuma pendência identificada para a data consultada.");
        showDetails(dayTable.getSelectionModel().getSelectedItem());
        showCatalogDetails(serviceTable.getSelectionModel().getSelectedItem());
        updateEntryHint();
        refreshHistory();
        refreshFronts();
    }

    private void refreshHistory() {
        if (view == null) return;
        historyTable.setItems(FXCollections.observableArrayList(productions.stream()
                .filter(p -> matchesService(p.servicoId()))
                .filter(p -> !historyDateOnly.isSelected() || p.data().equals(date.getValue()))
                .toList()));
        occurrenceTable.setItems(FXCollections.observableArrayList(occurrences.stream()
                .filter(o -> matchesService(o.servicoId()))
                .filter(o -> !historyDateOnly.isSelected() || o.data().equals(date.getValue()))
                .toList()));
        List<StatusChange> changes = new ArrayList<>();
        for (ServicoArea item : services) {
            if (!matchesService(item.id())) continue;
            List<ProducaoDiaria> itemProduction = productions.stream().filter(p -> p.servicoId() == item.id()).toList();
            List<OcorrenciaServico> itemOccurrences = occurrences.stream().filter(o -> o.servicoId() == item.id()).toList();
            java.util.Set<LocalDate> dates = new java.util.TreeSet<>();
            itemProduction.forEach(p -> dates.add(p.data()));
            itemOccurrences.forEach(o -> { dates.add(o.data()); if (o.resolvidaEm() != null) dates.add(o.resolvidaEm()); });
            SituacaoServico previous = SituacaoServico.NAO_INICIADO;
            for (LocalDate day : dates) {
                if (date.getValue() != null && day.isAfter(date.getValue())) continue;
                SituacaoServico actual = PlanejamentoDiarioService.calcular(item, findArea(item.areaId()), day,
                        itemProduction, itemOccurrences).situacao();
                if (actual != previous) {
                    if (!historyDateOnly.isSelected() || day.equals(date.getValue())) changes.add(new StatusChange(day, item.id(), actual));
                    previous = actual;
                }
            }
        }
        changes.sort(java.util.Comparator.comparing(StatusChange::data).reversed());
        statusHistoryTable.setItems(FXCollections.observableArrayList(changes));
    }

    private void refreshFronts() {
        if (view == null || date.getValue() == null || context.obraService().obraAtiva() == null) return;
        Long selectedId = linksTable.getSelectionModel().getSelectedItem() == null ? null
                : linksTable.getSelectionModel().getSelectedItem().vinculo().id();
        List<FrentesService.ResumoVinculo> shown = frontSummaries.stream().filter(this::matchesLink).toList();
        linksTable.setItems(FXCollections.observableArrayList(shown));
        if (selectedId != null) shown.stream().filter(r -> r.vinculo().id().equals(selectedId))
                .findFirst().ifPresent(r -> linksTable.getSelectionModel().select(r));
        if (linksTable.getSelectionModel().getSelectedItem() == null) showLink(null);
        refreshReleaseHistory();
    }
    private void refreshReleaseHistory() {
        if (date.getValue() == null) return;
        Long selectedLinkId = linksTable.getSelectionModel().getSelectedItem() == null ? null
                : linksTable.getSelectionModel().getSelectedItem().vinculo().id();
        List<Long> ids = linksTable.getItems().stream().map(r -> r.vinculo().id()).toList();
        releasesTable.setItems(FXCollections.observableArrayList(releaseHistory.stream()
                .filter(e -> !e.data().isAfter(date.getValue()))
                .filter(e -> {
                    TrechoVinculo scope = allScopes.stream().filter(t -> t.id() == e.trechoId()).findFirst().orElse(null);
                    return scope != null && ids.contains(scope.vinculoId())
                            && (selectedLinkId == null || scope.vinculoId() == selectedLinkId);
                }).toList()));
    }
    private boolean matchesLink(FrentesService.ResumoVinculo row) {
        VinculoFrente v = row.vinculo();
        ServicoArea source = findService(v.origemServicoId()), target = findService(v.destinoServicoId());
        return (areaFilter.getValue() == null || areaMatches(areaFilter.getValue().id(), v.areaId())
                    || areaMatches(areaFilter.getValue().id(), target.areaId()))
                && (disciplineFilter.getValue() == null || Objects.equals(source.disciplinaId(), disciplineFilter.getValue().id())
                    || Objects.equals(target.disciplinaId(), disciplineFilter.getValue().id()))
                && (serviceFilter.getValue() == null || v.origemServicoId() == serviceFilter.getValue().id()
                    || v.destinoServicoId() == serviceFilter.getValue().id())
                && (frontFilter.getValue() == null || row.situacao() == frontFilter.getValue())
                && (statusFilter.getValue() == null || dayTable.getItems().stream().anyMatch(r ->
                    r.servico().id() == v.origemServicoId() || r.servico().id() == v.destinoServicoId()));
    }
    private void showLink(FrentesService.ResumoVinculo row) {
        if (row == null) { scopesTable.getItems().clear(); frontDetails.setText("Selecione um vínculo para conferir os trechos liberados e bloqueados."); refreshReleaseHistory(); return; }
        Long selectedScope = scopesTable.getSelectionModel().getSelectedItem() == null ? null
                : scopesTable.getSelectionModel().getSelectedItem().trecho().id();
        scopesTable.setItems(FXCollections.observableArrayList(row.trechos()));
        if (selectedScope != null) row.trechos().stream().filter(s -> s.trecho().id().equals(selectedScope))
                .findFirst().ifPresent(s -> scopesTable.getSelectionModel().select(s));
        String released = row.trechos().stream().filter(FrentesService.EstadoTrecho::liberado)
                .map(s -> s.trecho().codigo()).reduce((a, b) -> a + ", " + b).orElse("nenhum");
        String blocked = row.trechos().stream().filter(s -> !s.liberado())
                .map(s -> s.trecho().codigo()).reduce((a, b) -> a + ", " + b).orElse("nenhum");
        frontDetails.setText("Origem: " + areaPath(row.vinculo().areaId())
                + " → Destino: " + areaOfService(row.vinculo().destinoServicoId())
                + ". Situação: " + row.situacao().label() + ". Liberados: " + released
                + ". Bloqueados: " + blocked + ". A conclusão da produção não libera a frente automaticamente.");
        refreshReleaseHistory();
    }

    private void createLink() {
        if (context.obraService().obraAtiva() == null) return;
        ComboBox<ServicoArea> source = new ComboBox<>(FXCollections.observableArrayList(services));
        ComboBox<ServicoArea> target = new ComboBox<>(FXCollections.observableArrayList(services));
        configureServiceCombo(source); configureServiceCombo(target);
        source.setPrefWidth(510); target.setPrefWidth(510);
        source.setPromptText("Selecione o serviço executado primeiro");
        target.setPromptText("Selecione o serviço que aguarda a liberação");
        Label help = new Label("Escolha os dois serviços da obra. Eles podem estar em locais diferentes."
                + " A dependência será criada bloqueada; depois você libera cada trecho com confirmação.");
        help.setWrapText(true);
        Label preview = new Label("1. Escolha o serviço de origem.\n2. Escolha o serviço que depende dele.");
        preview.setWrapText(true);
        Runnable update = () -> {
            if (source.getValue() == null || target.getValue() == null) {
                preview.setText("Selecione origem e destino para conferir os locais e trechos do vínculo."); return;
            }
            try {
                List<String> scopes = frentes.trechosDisponiveis(workId(), source.getValue().id(), target.getValue().id());
                List<String> sourceCodes = elements.stream().filter(e -> e.servicoId() == source.getValue().id())
                        .map(ElementoServico::codigo).toList();
                List<String> targetCodes = elements.stream().filter(e -> e.servicoId() == target.getValue().id())
                        .map(ElementoServico::codigo).toList();
                List<String> unmatched = targetCodes.stream().filter(code -> sourceCodes.stream()
                        .noneMatch(originCode -> originCode.equalsIgnoreCase(code))).toList();
                preview.setText("Origem: " + areaPath(source.getValue().areaId())
                        + "\nDestino: " + areaPath(target.getValue().areaId())
                        + "\nTrechos do vínculo que ficarão bloqueados: " + String.join(", ", scopes)
                        + (sourceCodes.isEmpty() || unmatched.isEmpty() ? ""
                            : "\nSem código igual na origem: " + String.join(", ", unmatched)
                            + ". Confirme a liberação desses trechos individualmente.")
                        + "\nApós salvar, selecione um trecho para confirmar sua liberação.");
            } catch (RuntimeException ex) { preview.setText(message(ex)); }
        };
        source.valueProperty().addListener((a, b, c) -> {
            Long targetId = id(target.getValue());
            target.setItems(FXCollections.observableArrayList(c == null ? services : frentes.destinosPossiveis(workId(), c.id())));
            target.setValue(target.getItems().stream().filter(s -> s.id().equals(targetId)).findFirst().orElse(null));
            update.run();
        });
        target.valueProperty().addListener((a, b, c) -> update.run());
        if (serviceTable.getSelectionModel().getSelectedItem() != null) source.setValue(serviceTable.getSelectionModel().getSelectedItem());
        Button addTarget = button("Cadastrar serviço de destino", () -> {
            java.util.Set<Long> before = services.stream().map(ServicoArea::id).collect(java.util.stream.Collectors.toSet());
            editService(null, source.getValue() == null ? null : findArea(source.getValue().areaId()));
            Long sourceId = id(source.getValue());
            source.setItems(FXCollections.observableArrayList(services));
            source.setValue(sourceId == null ? null : findService(sourceId));
            target.setItems(FXCollections.observableArrayList(sourceId == null ? services : frentes.destinosPossiveis(workId(), sourceId)));
            services.stream().filter(s -> !before.contains(s.id())).findFirst().ifPresent(target::setValue);
            update.run();
        });
        Dialog<ButtonType> dialog = formDialog("Vincular serviços", help,
                labeled("1. Serviço que libera a frente *", source),
                labeled("2. Serviço que depende da liberação *", target), addTarget,
                labeled("3. Confira os locais e trechos", preview));
        dialog.getDialogPane().setPrefWidth(590);
        commit(dialog, () -> {
            if (source.getValue() == null || target.getValue() == null) throw new IllegalArgumentException("Selecione origem e destino.");
            VinculoFrente created = frentes.criarVinculo(workId(), source.getValue().id(), target.getValue().id(),
                    frentes.trechosDisponiveis(workId(), source.getValue().id(), target.getValue().id()));
            clearFilters(); refresh(); tabs.getSelectionModel().select(2);
            linksTable.getItems().stream().filter(r -> r.vinculo().id().equals(created.id())).findFirst()
                    .ifPresent(r -> linksTable.getSelectionModel().select(r));
        });
    }
    private void clearFilters() {
        areaFilter.setValue(null); disciplineFilter.setValue(null);
        serviceFilter.setValue(null); statusFilter.setValue(null); frontFilter.setValue(null);
    }
    private void confirmScope(boolean release) {
        FrentesService.EstadoTrecho selected = scopesTable.getSelectionModel().getSelectedItem();
        if (selected == null) { UiAlerts.info("Frentes", "Selecione um elemento ou trecho do vínculo."); return; }
        DatePicker when = new DatePicker(date.getValue()); DateFormats.configure(when);
        TextField responsible = field("Quem confirmou a situação da frente?");
        TextArea observation = new TextArea(); observation.setPrefRowCount(3);
        observation.setPromptText("Inspeção, correções, pendências ou outra informação conhecida");
        Label help = new Label(release ? "Confirme explicitamente que este trecho está pronto para a próxima disciplina."
                : "Registre que este trecho continua ou voltou a ficar bloqueado.");
        help.setWrapText(true);
        Dialog<ButtonType> dialog = formDialog(release ? "Liberar " + selected.trecho().codigo()
                : "Bloquear " + selected.trecho().codigo(), help,
                labeled("Data *", when), labeled("Responsável *", responsible), labeled("Observação *", observation));
        commit(dialog, () -> {
            frentes.registrarLiberacao(workId(), selected.trecho().id(), when.getValue(), release,
                    responsible.getText(), observation.getText());
            refresh();
        });
    }

    private boolean matches(ResumoServicoDia row) {
        return (areaFilter.getValue() == null || areaMatches(areaFilter.getValue().id(), row.servico().areaId()))
                && (disciplineFilter.getValue() == null || Objects.equals(row.servico().disciplinaId(), disciplineFilter.getValue().id()))
                && (serviceFilter.getValue() == null || row.servico().id().equals(serviceFilter.getValue().id()))
                && (statusFilter.getValue() == null || row.situacao() == statusFilter.getValue());
    }
    private boolean matchesService(long serviceId) {
        ServicoArea item = findService(serviceId);
        return item != null && (areaFilter.getValue() == null || areaMatches(areaFilter.getValue().id(), item.areaId()))
                && (disciplineFilter.getValue() == null || Objects.equals(item.disciplinaId(), disciplineFilter.getValue().id()))
                && (serviceFilter.getValue() == null || item.id().equals(serviceFilter.getValue().id()))
                && (statusFilter.getValue() == null || dayTable.getItems().stream().anyMatch(r -> r.servico().id() == serviceId));
    }

    private void updateFilterServices() {
        Long old = id(serviceFilter.getValue());
        serviceFilter.setItems(FXCollections.observableArrayList(services.stream()
                .filter(s -> areaFilter.getValue() == null || areaMatches(areaFilter.getValue().id(), s.areaId()))
                .filter(s -> disciplineFilter.getValue() == null || Objects.equals(s.disciplinaId(), disciplineFilter.getValue().id())).toList()));
        serviceFilter.setValue(serviceFilter.getItems().stream().filter(s -> Objects.equals(s.id(), old)).findFirst().orElse(null));
    }
    private void updateEntryServices() {
        Long old = id(entryService.getValue());
        entryService.setItems(FXCollections.observableArrayList(services.stream()
                .filter(s -> entryArea.getValue() != null && s.areaId() == entryArea.getValue().id()).toList()));
        entryService.setValue(entryService.getItems().stream().filter(s -> Objects.equals(s.id(), old)).findFirst().orElse(null));
    }
    private void updateEntryElements() {
        entryElements.getChildren().clear(); entryElementChecks.clear();
        if (entryService.getValue() == null) { updateEntryHint(); return; }
        List<ElementoServico> options = elements.stream().filter(e -> e.servicoId() == entryService.getValue().id()).toList();
        if (options.isEmpty()) {
            entryElements.getChildren().add(new Label("Serviço sem elementos individualizados."));
            updateEntryHint(); return;
        }
        for (ElementoServico option : options) {
            CheckBox check = new CheckBox(option.codigo());
            check.setUserData(option.id()); entryElementChecks.add(check); entryElements.getChildren().add(check);
        }
        updateEntryHint();
    }
    private void updateEntryHint() {
        if (editingProductionId != null) return;
        if (entryService.getValue() == null) { entryHint.setText("Selecione a frente e registre a produção do dia."); return; }
        String state = frontState(entryService.getValue().id());
        entryHint.setText("Frente de entrada: " + state + ". "
                + (state.equals("Sem vínculo") ? "Nenhum pré-requisito foi cadastrado para este serviço."
                : dependencyDetails(entryService.getValue().id()))
                + " A produção não confirma liberação.");
    }
    private List<Long> selectedElementIds() {
        return entryElementChecks.stream().filter(CheckBox::isSelected).map(c -> (Long) c.getUserData()).toList();
    }
    private void updateOccurrenceInputs() {
        boolean selected = occurrenceType.getValue() != null;
        occurrenceDescription.setDisable(!selected);
        occurrenceMaterial.setDisable(occurrenceType.getValue() != TipoOcorrenciaServico.FALTA_MATERIAL);
        if (!selected) occurrenceDescription.clear();
        if (occurrenceType.getValue() != TipoOcorrenciaServico.FALTA_MATERIAL) occurrenceMaterial.setValue(null);
    }

    private void saveDay() {
        Obra obra = context.obraService().obraAtiva();
        if (obra == null) { UiAlerts.error("Planejamento", "Ative uma obra antes de lançar a produção."); return; }
        if (entryService.getValue() == null) { UiAlerts.error("Planejamento", "Selecione a área e o serviço."); return; }
        try {
            PlanejamentoDiarioService.ResultadoLancamento result = service.salvarDia(obra.id(), editingProductionId,
                    entryService.getValue().id(), date.getValue(), number(quantity.getText(), "Quantidade"),
                    integer(workers.getText(), "Trabalhadores"), optionalNumber(hours.getText(), "Horas por trabalhador"),
                    team.getText(), note.getText(), occurrenceType.getValue(), occurrenceDescription.getText(),
                    id(occurrenceMaterial.getValue()), selectedElementIds());
            boolean over = result.ultrapassouPrevisto();
            clearEntry();
            refresh();
            if (over) UiAlerts.info("Quantidade acima do previsto", "O lançamento foi salvo. O acumulado ultrapassou a quantidade prevista; confira o serviço e a medição.");
        } catch (RuntimeException ex) { UiAlerts.error("Não foi possível salvar", message(ex)); }
    }
    private void clearEntry() {
        editingProductionId = null;
        entryService.setValue(null); quantity.clear(); workers.clear(); hours.clear(); team.clear(); note.clear();
        occurrenceType.setValue(null); occurrenceDescription.clear(); occurrenceMaterial.setValue(null);
        saveEntry.setText("Salvar produção");
        entryHint.setText("Selecione a frente e registre a produção do dia.");
    }
    private void correctSelected() {
        ProducaoDiaria selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) { UiAlerts.info("Histórico", "Selecione um lançamento para corrigir."); return; }
        ServicoArea s = findService(selected.servicoId());
        if (s == null) return;
        date.setValue(selected.data());
        entryArea.setValue(findArea(s.areaId()));
        entryService.setValue(s);
        List<Long> linked = productionElements.stream().filter(p -> p.producaoId() == selected.id())
                .map(ElementoProducao::elementoId).toList();
        for (CheckBox check : entryElementChecks) check.setSelected(linked.contains((Long) check.getUserData()));
        quantity.setText(formatInput(selected.quantidade()));
        workers.setText(Integer.toString(selected.trabalhadores()));
        hours.setText(selected.horasPorTrabalhador() == null ? "" : formatInput(selected.horasPorTrabalhador()));
        team.setText(nullText(selected.equipe()).equals("—") ? "" : selected.equipe());
        note.setText(selected.observacao() == null ? "" : selected.observacao());
        occurrenceType.setValue(null);
        editingProductionId = selected.id();
        saveEntry.setText("Salvar correção");
        entryHint.setText("Corrigindo " + serviceName(selected.servicoId()) + " em " + dateText(selected.data())
                + ". O acumulado será recalculado. Corrija ocorrências separadamente no histórico, se necessário.");
        tabs.getSelectionModel().select(0);
    }

    private void editArea(AreaObra current) {
        if (context.obraService().obraAtiva() == null) return;
        ComboBox<TipoAreaObra> type = new ComboBox<>(FXCollections.observableArrayList(TipoAreaObra.values()));
        TextField name = field("Ex.: Bloco A, 2º pavimento, sala 101");
        ComboBox<AreaObra> parent = new ComboBox<>(FXCollections.observableArrayList(areas.stream()
                .filter(a -> current == null || !a.id().equals(current.id())).toList()));
        configureAreaCombo(parent);
        parent.setPromptText("Sem área superior");
        if (current != null) { type.setValue(current.tipo()); name.setText(current.nome()); parent.setValue(findArea(current.areaPaiId())); }
        if (type.getValue() == null) type.setValue(TipoAreaObra.SETOR);
        Dialog<ButtonType> dialog = formDialog(current == null ? "Nova área" : "Editar área",
                labeled("Tipo *", type), labeled("Nome *", name), labeled("Dentro de (opcional)", parent));
        commit(dialog, () -> { service.salvarArea(workId(), id(current), id(parent.getValue()), type.getValue(), name.getText()); refresh(); });
    }
    private void createDiscipline() {
        if (context.obraService().obraAtiva() == null) return;
        TextField name = field("Ex.: Impermeabilização");
        Dialog<ButtonType> dialog = formDialog("Nova disciplina", labeled("Nome da disciplina *", name));
        commit(dialog, () -> { frentes.criarDisciplina(workId(), name.getText()); refresh(); });
    }
    private void editService(ServicoArea current) {
        editService(current, null);
    }
    private void editService(ServicoArea current, AreaObra suggestedArea) {
        if (context.obraService().obraAtiva() == null) return;
        ComboBox<AreaObra> area = new ComboBox<>(FXCollections.observableArrayList(areas));
        area.setPromptText("Selecione a área");
        configureAreaCombo(area);
        ComboBox<DisciplinaObra> discipline = new ComboBox<>(FXCollections.observableArrayList(disciplines));
        discipline.setPromptText("Selecione a disciplina");
        TextField name = field("Ex.: Alvenaria de vedação");
        TextField unit = field("Ex.: m², m³, un");
        TextField planned = field("Quantidade total prevista");
        TextField goal = field("Meta por dia (opcional)");
        TextField responsible = field("Equipe ou responsável (opcional)");
        TextArea observations = new TextArea(); observations.setPromptText("Observações do serviço (opcional)"); observations.setPrefRowCount(2);
        TextField newElements = field("Ex.: P41, P43 ou parede da Sala 01");
        Label existingElements = new Label(current == null ? "" : "Já cadastrados: " + elementNames(current.id()) + ". Para adicionar, informe apenas os novos.");
        existingElements.setWrapText(true);
        DatePicker start = new DatePicker(), end = new DatePicker();
        DateFormats.configure(start); DateFormats.configure(end);
        if (current != null) {
            area.setValue(findArea(current.areaId())); discipline.setValue(findDiscipline(current.disciplinaId()));
            name.setText(current.descricao()); unit.setText(current.unidade());
            planned.setText(formatInput(current.quantidadePrevista()));
            goal.setText(current.metaDiaria() == null ? "" : formatInput(current.metaDiaria()));
            start.setValue(current.inicioPrevisto()); end.setValue(current.fimPrevisto());
            responsible.setText(current.equipeResponsavel() == null ? "" : current.equipeResponsavel());
            observations.setText(current.observacoes() == null ? "" : current.observacoes());
        } else if (suggestedArea != null) area.setValue(suggestedArea);
        else if (areaTable.getSelectionModel().getSelectedItem() != null) area.setValue(areaTable.getSelectionModel().getSelectedItem());
        if (current == null && disciplineTable.getSelectionModel().getSelectedItem() != null)
            discipline.setValue(disciplineTable.getSelectionModel().getSelectedItem());
        Dialog<ButtonType> dialog = formDialog(current == null ? "Novo serviço" : "Editar serviço",
                labeled("Área / local *", area), labeled("Disciplina *", discipline),
                labeled("Descrição da atividade *", name), labeled("Unidade *", unit),
                labeled("Quantidade prevista *", planned), labeled("Início previsto (opcional)", start),
                labeled("Fim previsto (opcional)", end), labeled("Meta diária (opcional)", goal),
                labeled("Equipe responsável", responsible), labeled("Observações", observations),
                existingElements, labeled("Elementos / trechos separados por vírgula", newElements));
        commit(dialog, () -> { if (area.getValue() == null || discipline.getValue() == null)
                throw new IllegalArgumentException("Selecione área e disciplina.");
            List<String> codes = newElements.getText().isBlank() ? List.of()
                    : java.util.Arrays.stream(newElements.getText().split(",")).map(String::trim).toList();
            frentes.salvarServico(workId(), id(current), area.getValue().id(), discipline.getValue().id(),
                    name.getText(), unit.getText(),
                    number(planned.getText(), "Quantidade prevista"), start.getValue(), end.getValue(),
                    optionalNumber(goal.getText(), "Meta diária"), responsible.getText(), observations.getText(), codes);
            refresh(); });
    }
    private void editOccurrence(OcorrenciaServico current) {
        if (context.obraService().obraAtiva() == null) return;
        ComboBox<ServicoArea> target = new ComboBox<>(FXCollections.observableArrayList(services));
        configureServiceCombo(target);
        target.setPromptText("Escolha o serviço");
        DatePicker when = new DatePicker(date.getValue()); DateFormats.configure(when);
        ComboBox<TipoOcorrenciaServico> type = new ComboBox<>(FXCollections.observableArrayList(TipoOcorrenciaServico.values()));
        TextArea description = new TextArea(); description.setPromptText("Descreva apenas o que foi informado ou observado."); description.setPrefRowCount(3);
        ComboBox<Material> material = new ComboBox<>(occurrenceMaterial.getItems());
        material.setPromptText("Material cadastrado (opcional)");
        material.setCellFactory(list -> materialCell()); material.setButtonCell(materialCell());
        type.valueProperty().addListener((a, b, c) -> { material.setDisable(c != TipoOcorrenciaServico.FALTA_MATERIAL); if (c != TipoOcorrenciaServico.FALTA_MATERIAL) material.setValue(null); });
        if (current != null) {
            target.setValue(findService(current.servicoId())); when.setValue(current.data()); type.setValue(current.tipo());
            description.setText(current.descricao());
            material.setValue(material.getItems().stream().filter(m -> Objects.equals(m.id(), current.materialId())).findFirst().orElse(null));
        } else if (entryService.getValue() != null) target.setValue(entryService.getValue());
        if (type.getValue() == null) material.setDisable(true);
        Dialog<ButtonType> dialog = formDialog(current == null ? "Registrar ocorrência" : "Editar ocorrência",
                labeled("Serviço *", target), labeled("Data *", when), labeled("Tipo *", type),
                labeled("Descrição *", description), labeled("Material da obra", material));
        commit(dialog, () -> { if (target.getValue() == null) throw new IllegalArgumentException("Selecione o serviço.");
            service.salvarOcorrencia(workId(), id(current), target.getValue().id(), when.getValue(), type.getValue(),
                    description.getText(), id(material.getValue()), current == null ? null : current.resolvidaEm()); refresh(); });
    }
    private void resolveSelected() {
        OcorrenciaServico selected = occurrenceTable.getSelectionModel().getSelectedItem();
        if (selected == null) { UiAlerts.info("Ocorrências", "Selecione uma ocorrência."); return; }
        if (selected.resolvidaEm() != null) { UiAlerts.info("Ocorrências", "Esta ocorrência já foi resolvida."); return; }
        try { service.resolverOcorrencia(workId(), selected.id(), LocalDate.now()); refresh(); }
        catch (RuntimeException ex) { UiAlerts.error("Não foi possível resolver", message(ex)); }
    }

    private void showDetails(ResumoServicoDia row) {
        if (row == null) { details.setText("Selecione um serviço para ver produtividade e comparações."); return; }
        String unit = row.servico().unidade();
        String goal = row.servico().metaDiaria() == null ? "Meta diária não informada"
                : "Meta: " + measure(row.servico().metaDiaria(), unit) + "/dia; comparação: " + difference(row.comparacaoMetaPercentual());
        details.setText("Produtividade: " + productivity(row.produtividadePessoaDia(), unit, "trabalhador/dia")
                + "; " + productivity(row.produtividadeHomemHora(), unit, "HH") + ".\n"
                + goal + ". Comparação com registro anterior do mesmo serviço: por trabalhador "
                + difference(row.comparacaoAnteriorPessoaPercentual()) + "; por homem-hora "
                + difference(row.comparacaoAnteriorHoraPercentual()) + ".\n"
                + dependencyDetails(row.servico().id()));
    }
    private void showCatalogDetails(ServicoArea item) {
        if (item == null) { catalogDetails.setText("Selecione um serviço para ver pré-requisitos e próximas frentes."); return; }
        catalogDetails.setText("Local: " + areaPath(item.areaId()) + " | Disciplina: " + disciplineName(item.disciplinaId())
                + " | Elementos: " + elementNames(item.id()) + "\nEquipe: " + nullText(item.equipeResponsavel())
                + " | Observações: " + nullText(item.observacoes()) + "\n" + dependencyDetails(item.id()));
    }
    private String dependencyDetails(long serviceId) {
        List<FrentesService.ResumoVinculo> incoming = frontSummaries.stream()
                .filter(r -> r.vinculo().destinoServicoId() == serviceId).toList();
        List<FrentesService.ResumoVinculo> outgoing = frontSummaries.stream()
                .filter(r -> r.vinculo().origemServicoId() == serviceId).toList();
        String before = incoming.isEmpty() ? "Sem pré-requisito cadastrado."
                : incoming.stream().map(r -> serviceWithLocation(r.vinculo().origemServicoId()) + " [" + r.situacao().label()
                + "; liberados: " + scopeCodes(r, true) + "; bloqueados: " + scopeCodes(r, false) + "]")
                .reduce((a, b) -> a + " | " + b).orElse("");
        String after = outgoing.isEmpty() ? "Nenhum serviço sucessor cadastrado."
                : outgoing.stream().map(r -> serviceWithLocation(r.vinculo().destinoServicoId()) + " [" + r.situacao().label()
                + "; trechos: " + r.trechos().stream().map(s -> s.trecho().codigo()).reduce((a, b) -> a + ", " + b).orElse("—") + "]")
                .reduce((a, b) -> a + " | " + b).orElse("");
        return "Precisa estar liberado antes: " + before + "\nPoderão entrar depois: " + after;
    }
    private String scopeCodes(FrentesService.ResumoVinculo row, boolean released) {
        return row.trechos().stream().filter(s -> s.liberado() == released).map(s -> s.trecho().codigo())
                .reduce((a, b) -> a + ", " + b).orElse("nenhum");
    }
    private String frontState(long serviceId) {
        List<FrentesService.EstadoTrecho> incoming = frontSummaries.stream()
                .filter(r -> r.vinculo().destinoServicoId() == serviceId).flatMap(r -> r.trechos().stream()).toList();
        if (incoming.isEmpty()) return "Sem vínculo";
        long released = incoming.stream().filter(FrentesService.EstadoTrecho::liberado).count();
        return released == 0 ? "Bloqueado" : released == incoming.size() ? "Liberado" : "Parcial";
    }
    private String alertText(ResumoServicoDia row) {
        List<String> alerts = new ArrayList<>();
        if (row.ultrapassouPrevisto()) alerts.add("Acima do previsto");
        if (row.atrasado()) alerts.add("Prazo vencido");
        if (row.situacao() == SituacaoServico.PARALISADO) alerts.add("Paralisado");
        if (row.semAvancoRegistrado()) alerts.add("Sem avanço registrado");
        else if (row.semLancamento()) alerts.add("Sem lançamento");
        return alerts.isEmpty() ? "—" : String.join(" · ", alerts);
    }

    private static VBox panel(String title, Node... children) {
        Label heading = new Label(title); heading.getStyleClass().add("panel-title");
        VBox box = new VBox(9); box.getStyleClass().add("planning-panel");
        box.getChildren().add(heading); box.getChildren().addAll(children);
        return box;
    }
    private static VBox labeled(String caption, Node control) {
        Label label = new Label(caption);
        VBox box = new VBox(4, label, control);
        return box;
    }
    private static TextField field(String prompt) { TextField field = new TextField(); field.setPromptText(prompt); return field; }
    private static Button button(String caption, Runnable action) { Button button = new Button(caption); button.setOnAction(e -> action.run()); return button; }
    private static <T> void column(TableView<T> table, String heading, double width, Function<T, String> value) {
        TableColumn<T, String> col = new TableColumn<>(heading);
        col.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        col.setPrefWidth(width); table.getColumns().add(col);
    }
    private static Dialog<ButtonType> formDialog(String title, Node... fields) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE));
        VBox content = new VBox(9, fields); content.setPadding(new Insets(10)); content.setPrefWidth(420);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(Math.min(520, fields.length * 72));
        dialog.getDialogPane().setContent(scroll); return dialog;
    }
    private static void commit(Dialog<ButtonType> dialog, Runnable save) {
        Button ok = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(1));
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try { save.run(); }
            catch (RuntimeException ex) { event.consume(); UiAlerts.error("Confira os dados", message(ex)); }
        });
        dialog.showAndWait();
    }
    private static ListCell<Material> materialCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Material item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.codigo() + " · " + item.descricao());
            }
        };
    }
    private long workId() { return context.obraService().obraAtiva().id(); }
    private DisciplinaObra findDiscipline(Long id) { return disciplines.stream().filter(d -> Objects.equals(d.id(), id)).findFirst().orElse(null); }
    private String disciplineName(Long id) { DisciplinaObra d = findDiscipline(id); return d == null ? "Sem disciplina (legado)" : d.nome(); }
    private AreaObra findArea(Long id) { return areas.stream().filter(a -> Objects.equals(a.id(), id)).findFirst().orElse(null); }
    private boolean areaMatches(long selected, long actual) {
        Long cursor = actual; java.util.Set<Long> visited = new java.util.HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (cursor == selected) return true;
            AreaObra area = findArea(cursor); cursor = area == null ? null : area.areaPaiId();
        }
        return false;
    }
    private String areaPath(Long id) {
        List<String> names = new ArrayList<>(); java.util.Set<Long> visited = new java.util.HashSet<>();
        Long cursor = id;
        while (cursor != null && visited.add(cursor)) {
            AreaObra area = findArea(cursor);
            if (area == null) break;
            names.add(0, area.nome()); cursor = area.areaPaiId();
        }
        return names.isEmpty() ? "—" : String.join(" → ", names);
    }
    private void configureAreaCombo(ComboBox<AreaObra> combo) {
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(AreaObra area) { return area == null ? "" : areaPath(area.id()); }
            @Override public AreaObra fromString(String value) { return null; }
        });
        combo.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(AreaObra area, boolean empty) {
                super.updateItem(area, empty); setText(empty || area == null ? null : areaPath(area.id()));
            }
        });
    }
    private void configureServiceCombo(ComboBox<ServicoArea> combo) {
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ServicoArea item) {
                return item == null ? "" : serviceLabel(item);
            }
            @Override public ServicoArea fromString(String value) { return null; }
        });
        combo.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ServicoArea item, boolean empty) {
                super.updateItem(item, empty); setText(empty || item == null ? null : serviceLabel(item));
            }
        });
    }
    private String serviceLabel(ServicoArea item) {
        return item.descricao() + " · " + disciplineName(item.disciplinaId()) + " · " + areaPath(item.areaId());
    }
    private String elementNames(long serviceId) {
        return elements.stream().filter(e -> e.servicoId() == serviceId).map(ElementoServico::codigo)
                .reduce((a, b) -> a + ", " + b).orElse("sem elementos individualizados");
    }
    private String productionElementNames(long productionId) {
        List<Long> ids = productionElements.stream().filter(p -> p.producaoId() == productionId)
                .map(ElementoProducao::elementoId).toList();
        return elements.stream().filter(e -> ids.contains(e.id())).map(ElementoServico::codigo)
                .reduce((a, b) -> a + ", " + b).orElse("—");
    }
    private String scopeName(long scopeId) {
        return allScopes.stream().filter(t -> t.id() == scopeId).map(TrechoVinculo::codigo).findFirst().orElse("—");
    }
    private String linkNamesByScope(long scopeId) {
        TrechoVinculo scope = allScopes.stream().filter(t -> t.id() == scopeId).findFirst().orElse(null);
        if (scope == null) return "—";
        return frontSummaries.stream().filter(r -> r.vinculo().id() == scope.vinculoId())
                .map(r -> serviceName(r.vinculo().origemServicoId()) + " → " + serviceName(r.vinculo().destinoServicoId()))
                .findFirst().orElse("—");
    }
    private ServicoArea findService(long id) { return services.stream().filter(s -> s.id() == id).findFirst().orElse(null); }
    private String areaName(Long id) { AreaObra a = findArea(id); return a == null ? "—" : a.toString(); }
    private String areaOfService(long id) { ServicoArea s = findService(id); return s == null ? "—" : areaPath(s.areaId()); }
    private String serviceName(long id) { ServicoArea s = findService(id); return s == null ? "—" : s.descricao(); }
    private String serviceWithLocation(long id) {
        ServicoArea s = findService(id);
        return s == null ? "—" : s.descricao() + " · " + areaPath(s.areaId());
    }
    private String unitOfService(long id) { ServicoArea s = findService(id); return s == null ? "" : s.unidade(); }
    private String materialName(Long id) {
        if (id == null) return "—";
        return context.materialRepository().findById(id).map(m -> m.codigo() + " · " + m.descricao()).orElse("—");
    }
    private static String name(AreaObra a) { return a == null ? "—" : a.toString(); }
    private static String nullText(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static String dateText(LocalDate d) { return d == null ? "—" : DATE.format(d); }
    private static String format(double value) { return String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value); }
    private static String formatInput(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ','); }
    private static String measure(double value, String unit) { return format(value) + " " + unit; }
    private static String productivity(Double value, String unit, String denominator) { return value == null ? "—" : format(value) + " " + unit + "/" + denominator; }
    private static String difference(Double value) { return value == null ? "sem base comparável" : (value > 0 ? "+" : "") + format(value) + "%"; }
    private static double number(String text, String label) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Informe " + label.toLowerCase(Locale.ROOT) + ".");
        try {
            String normalized = text.trim();
            if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(',', '.');
            return Double.parseDouble(normalized);
        }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " deve ser um número válido."); }
    }
    private static Double optionalNumber(String text, String label) { return text == null || text.isBlank() ? null : number(text, label); }
    private static int integer(String text, String label) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Informe " + label.toLowerCase(Locale.ROOT) + ".");
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " deve ser um número inteiro."); }
    }
    private static Long id(AreaObra value) { return value == null ? null : value.id(); }
    private static Long id(DisciplinaObra value) { return value == null ? null : value.id(); }
    private static Long id(ServicoArea value) { return value == null ? null : value.id(); }
    private static Long id(ProducaoDiaria value) { return value == null ? null : value.id(); }
    private static Long id(OcorrenciaServico value) { return value == null ? null : value.id(); }
    private static Long id(Material value) { return value == null ? null : value.id(); }
    private static String message(RuntimeException ex) { return ex.getMessage() == null ? "Erro inesperado." : ex.getMessage(); }
    private record StatusChange(LocalDate data, long servicoId, SituacaoServico situacao) { }
}
