package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;

public final class ConfiguracoesController {

    private final ApplicationContext context;
    private final Window owner;

    public ConfiguracoesController(ApplicationContext context, Window owner) {
        this.context = context;
        this.owner = owner;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Configurações");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Dados locais, cópias de segurança e restauração do banco SQLite.");
        subtitle.getStyleClass().add("page-subtitle");

        GridPane card = new GridPane();
        card.setHgap(14);
        card.setVgap(14);
        card.getStyleClass().add("dashboard-panel");
        card.setPadding(new Insets(20));
        Label location = new Label(context.databaseManager().getDatabasePath().toString());
        location.setWrapText(true);
        TextField backupName = new TextField("backup_" + java.time.LocalDate.now() + ".db");
        Button backup = new Button("Fazer backup");
        backup.getStyleClass().add("primary-button");
        backup.setOnAction(event -> backup(backupName.getText()));
        Button restore = new Button("Restaurar backup");
        restore.getStyleClass().add("secondary-button");
        restore.setOnAction(event -> restore());
        card.add(new Label("Banco atual"), 0, 0);
        card.add(location, 1, 0);
        card.add(new Label("Nome sugerido"), 0, 1);
        card.add(backupName, 1, 1);
        card.add(backup, 1, 2);
        card.add(restore, 1, 3);
        page.getChildren().addAll(title, subtitle, card);
        return page;
    }

    private void backup(String name) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar backup do banco");
        chooser.setInitialFileName(name == null || name.isBlank() ? "backup_" + java.time.LocalDate.now() + ".db" : name.trim());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Banco SQLite (*.db)", "*.db"));
        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;
        try {
            Path path = context.backupService().criarBackup(selected.toPath());
            UiAlerts.info("Backup", "Backup criado em:\n" + path);
        } catch (RuntimeException exception) {
            UiAlerts.error("Backup", friendly(exception));
        }
    }

    private void restore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar backup para restaurar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Banco SQLite (*.db)", "*.db"));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) return;
        if (!UiAlerts.confirm("Restaurar backup", "Os dados atuais serão substituídos pelo backup selecionado. Deseja continuar?")) return;
        try {
            context.backupService().restaurar(selected.toPath());
            UiAlerts.info("Backup", "Backup restaurado. Reabra a tela para atualizar os dados.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Restauração", friendly(exception));
        }
    }

    private String friendly(RuntimeException exception) {
        return exception.getMessage() == null ? "Não foi possível concluir a operação." : exception.getMessage();
    }
}
