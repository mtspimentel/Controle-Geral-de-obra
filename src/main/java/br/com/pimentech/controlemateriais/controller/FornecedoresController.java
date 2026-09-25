package br.com.pimentech.controlemateriais.controller;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Fornecedor;
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

public final class FornecedoresController {

    private final ApplicationContext context;
    private final TableView<Fornecedor> table = new TableView<>();
    private final TextField search = new TextField();

    public FornecedoresController(ApplicationContext context) {
        this.context = context;
    }

    public Node createView() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setPadding(new Insets(30));
        Label title = new Label("Fornecedores");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Mantenha os contatos e dados comerciais usados nas cotações e pedidos.");
        subtitle.getStyleClass().add("page-subtitle");

        search.setPromptText("Pesquisar fornecedor, CNPJ ou contato");
        search.setPrefWidth(320);
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        Button newButton = new Button("+ Novo fornecedor");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> openForm(null));
        Button editButton = new Button("Editar selecionado");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> editSelected());
        HBox toolbar = new HBox(10, search, newButton, editButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        refresh();
        VBox.setVgrow(table, Priority.ALWAYS);
        page.getChildren().addAll(title, subtitle, toolbar, table);
        return page;
    }

    private void configureTable() {
        if (!table.getColumns().isEmpty()) {
            return;
        }
        table.setPlaceholder(new Label("Nenhum fornecedor cadastrado"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<Fornecedor, String> name = new TableColumn<>("Fornecedor");
        name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().nome()));
        TableColumn<Fornecedor, String> cnpj = new TableColumn<>("CNPJ");
        cnpj.setCellValueFactory(cell -> new SimpleStringProperty(value(cell.getValue().cnpj())));
        TableColumn<Fornecedor, String> contact = new TableColumn<>("Contato");
        contact.setCellValueFactory(cell -> new SimpleStringProperty(value(cell.getValue().contato())));
        TableColumn<Fornecedor, String> phone = new TableColumn<>("Telefone");
        phone.setCellValueFactory(cell -> new SimpleStringProperty(value(cell.getValue().telefone())));
        TableColumn<Fornecedor, String> email = new TableColumn<>("E-mail");
        email.setCellValueFactory(cell -> new SimpleStringProperty(value(cell.getValue().email())));
        table.getColumns().addAll(name, cnpj, contact, phone, email);
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(context.fornecedorService().pesquisar(search.getText())));
    }

    private void editSelected() {
        Fornecedor selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Fornecedores", "Selecione um fornecedor para editar.");
            return;
        }
        openForm(selected);
    }

    private void openForm(Fornecedor existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Novo fornecedor" : "Editar fornecedor");
        dialog.setHeaderText("Dados do fornecedor");
        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(value(existing == null ? null : existing.nome()));
        TextField cnpj = new TextField(value(existing == null ? null : existing.cnpj()));
        TextField phone = new TextField(value(existing == null ? null : existing.telefone()));
        TextField email = new TextField(value(existing == null ? null : existing.email()));
        TextField contact = new TextField(value(existing == null ? null : existing.contato()));
        TextField notes = new TextField(value(existing == null ? null : existing.observacao()));

        GridPane grid = formGrid();
        addRow(grid, 0, "Nome *", name);
        addRow(grid, 1, "CNPJ", cnpj);
        addRow(grid, 2, "Telefone", phone);
        addRow(grid, 3, "E-mail", email);
        addRow(grid, 4, "Contato", contact);
        addRow(grid, 5, "Observação", notes);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            try {
                if (existing == null) {
                    context.fornecedorService().criar(name.getText(), cnpj.getText(), phone.getText(), email.getText(), contact.getText(), notes.getText());
                } else {
                    context.fornecedorService().atualizar(new Fornecedor(existing.id(), name.getText(), cnpj.getText(), phone.getText(),
                            email.getText(), contact.getText(), notes.getText(), existing.ativo(), existing.createdAt(), existing.updatedAt()));
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
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String friendlyMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "Verifique os dados e tente novamente." : exception.getMessage();
    }
}
