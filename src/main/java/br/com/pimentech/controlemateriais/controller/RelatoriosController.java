package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;

public final class RelatoriosController {

    private final ApplicationContext context;
    private final Window owner;

    public RelatoriosController(ApplicationContext context, Window owner) {
        this.context = context;
        this.owner = owner;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Relatórios");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Exporte informações da obra em formato CSV para análise e arquivamento.");
        subtitle.getStyleClass().add("page-subtitle");
        Obra obra = context.obraService().obraAtiva();
        DatePicker start = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker end = new DatePicker(LocalDate.now());
        Button orders = new Button("Exportar pedidos por período");
        orders.getStyleClass().add("primary-button");
        orders.setOnAction(event -> exportOrders(obra, start.getValue(), end.getValue()));
        Button planning = new Button("Exportar planejamento de compras");
        planning.getStyleClass().add("secondary-button");
        planning.setOnAction(event -> exportPlanning(obra));
        GridPane card = new GridPane();
        card.setHgap(12);
        card.setVgap(12);
        card.getStyleClass().add("dashboard-panel");
        card.setPadding(new Insets(20));
        card.add(new Label("Data inicial"), 0, 0);
        card.add(start, 1, 0);
        card.add(new Label("Data final"), 0, 1);
        card.add(end, 1, 1);
        card.add(orders, 1, 2);
        card.add(planning, 1, 3);
        page.getChildren().addAll(title, subtitle, card);
        return page;
    }

    private void exportOrders(Obra obra, LocalDate start, LocalDate end) {
        if (obra == null) { UiAlerts.info("Relatórios", "Cadastre ou ative uma obra antes de exportar."); return; }
        File file = choose("pedidos_" + LocalDate.now() + ".csv", "Salvar relatório de pedidos");
        if (file == null) return;
        try {
            context.reportService().exportPedidos(obra.id(), start, end, file.toPath());
            UiAlerts.info("Relatórios", "Relatório exportado em:\n" + file);
        } catch (RuntimeException exception) { UiAlerts.error("Relatórios", friendly(exception)); }
    }

    private void exportPlanning(Obra obra) {
        if (obra == null) { UiAlerts.info("Relatórios", "Cadastre ou ative uma obra antes de exportar."); return; }
        File file = choose("planejamento_" + LocalDate.now() + ".csv", "Salvar planejamento");
        if (file == null) return;
        try {
            context.reportService().exportPlanejamento(obra.id(), file.toPath());
            UiAlerts.info("Relatórios", "Planejamento exportado em:\n" + file);
        } catch (RuntimeException exception) { UiAlerts.error("Relatórios", friendly(exception)); }
    }

    private File choose(String name, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        return chooser.showSaveDialog(owner);
    }

    private String friendly(RuntimeException exception) {
        return exception.getMessage() == null ? "Não foi possível exportar o relatório." : exception.getMessage();
    }
}
