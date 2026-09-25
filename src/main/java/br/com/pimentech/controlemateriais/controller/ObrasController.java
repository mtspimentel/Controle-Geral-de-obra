package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.ObraStatus;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.DateFormats;
import br.com.pimentech.controlemateriais.util.UiAlerts;
import javafx.collections.FXCollections;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public final class ObrasController {

    private final ApplicationContext context;
    private final TableView<Obra> table = new TableView<>();

    public ObrasController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));

        Label title = new Label("Obras");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Cadastre, selecione e edite as obras usadas nos pedidos, no controle e no diário de obra.");
        subtitle.getStyleClass().add("page-subtitle");

        Button newButton = new Button("+ Nova obra");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> openForm(null));
        Button editButton = new Button("Editar obra selecionada");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> editSelected());
        HBox toolbar = new HBox(10, newButton, editButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                editSelected();
            }
        });
        refresh();
        VBox.setVgrow(table, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, toolbar, table);
        return page;
    }

    private void configureTable() {
        if (!table.getColumns().isEmpty()) {
            return;
        }
        table.setPlaceholder(new Label("Nenhuma obra cadastrada"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<Obra, String> code = new TableColumn<>("Código");
        code.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().codigo()));
        TableColumn<Obra, String> name = new TableColumn<>("Obra");
        name.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().nome()));
        TableColumn<Obra, String> responsible = new TableColumn<>("Responsável");
        responsible.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(value(cell.getValue().responsavel())));
        TableColumn<Obra, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().status().name()));
        TableColumn<Obra, String> end = new TableColumn<>("Previsão de término");
        end.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(formatDate(cell.getValue().previsaoTermino())));
        table.getColumns().addAll(code, name, responsible, status, end);
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(context.obraService().listar()));
    }

    private void editSelected() {
        Obra selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Obras", "Selecione uma obra para editar.");
            return;
        }
        openForm(selected);
    }

    private void openForm(Obra existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nova obra" : "Editar obra");
        dialog.setHeaderText("Dados principais da obra");
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(value(existing == null ? null : existing.nome()));
        TextField code = new TextField(value(existing == null ? null : existing.codigo()));
        TextField address = new TextField(value(existing == null ? null : existing.endereco()));
        TextField responsible = new TextField(value(existing == null ? null : existing.responsavel()));
        DatePicker start = new DatePicker(existing == null ? LocalDate.now() : existing.dataInicio());
        DatePicker end = new DatePicker(existing == null ? null : existing.previsaoTermino());
        DateFormats.configure(start);
        DateFormats.configure(end);
        ComboBox<ObraStatus> status = new ComboBox<>(FXCollections.observableArrayList(ObraStatus.values()));
        status.setValue(existing == null ? ObraStatus.ATIVA : existing.status());

        GridPane grid = formGrid();
        addRow(grid, 0, "Nome *", name);
        addRow(grid, 1, "Código *", code);
        addRow(grid, 2, "Endereço", address);
        addRow(grid, 3, "Responsável", responsible);
        addRow(grid, 4, "Data de início", start);
        addRow(grid, 5, "Previsão de término", end);
        addRow(grid, 6, "Status", status);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            try {
                if (existing == null) {
                    context.obraService().criar(name.getText(), code.getText(), address.getText(), responsible.getText(),
                            start.getValue(), end.getValue(), status.getValue());
                } else {
                    context.obraService().atualizar(new Obra(existing.id(), name.getText(), code.getText(), address.getText(),
                            responsible.getText(), start.getValue(), end.getValue(), status.getValue(), existing.ativo(),
                            existing.createdAt(), existing.updatedAt()));
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
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        return grid;
    }

    private void addRow(GridPane grid, int row, String label, Node input) {
        Label text = new Label(label);
        text.getStyleClass().add("form-label");
        grid.add(text, 0, row);
        grid.add(input, 1, row);
        if (input instanceof TextInputControl control) {
            control.setPrefWidth(330);
        } else if (input instanceof DatePicker picker) {
            picker.setPrefWidth(330);
        } else if (input instanceof ComboBox<?> combo) {
            combo.setPrefWidth(330);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String formatDate(LocalDate date) {
        return DateFormats.format(date);
    }

    private String friendlyMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage();
    }
}
