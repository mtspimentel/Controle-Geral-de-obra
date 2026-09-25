package br.com.pimentech.controlemateriais;

import br.com.pimentech.controlemateriais.controller.MainController;
import br.com.pimentech.controlemateriais.service.ApplicationContext;
import br.com.pimentech.controlemateriais.util.ApplicationLogger;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class Main extends Application {

    private static final String APPLICATION_TITLE = "Planejamento Rio Claro | Obras e Almoxarifado";

    @Override
    public void start(Stage stage) {
        ApplicationContext context;
        try {
            context = new ApplicationContext();
        } catch (RuntimeException exception) {
            ApplicationLogger.error("Falha ao iniciar a aplicação", exception);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(APPLICATION_TITLE);
            alert.setHeaderText("Não foi possível iniciar o banco local");
            alert.setContentText(exception.getMessage() == null ? "Feche o programa e tente novamente." : exception.getMessage());
            alert.showAndWait();
            return;
        }

        MainController controller = new MainController(context);
        Scene scene = new Scene(controller.createView(), 1280, 760);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle(APPLICATION_TITLE);
        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(true);
        stage.setMaximized(false);
        stage.setFullScreen(false);
        stage.setMinWidth(1080);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> context.close());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
