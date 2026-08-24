package dev.logicforge.app;

import dev.logicforge.ui.view.Workbench;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Starts the LogicForge workbench. */
public class LogicForgeApp extends Application {

    private static final String STYLESHEET = "/dev/logicforge/ui/logicforge.css";

    @Override
    public void start(Stage stage) {
        Workbench workbench = new Workbench(stage);
        Scene scene = new Scene(workbench, 1360, 860);
        scene.getStylesheets().add(LogicForgeApp.class.getResource(STYLESHEET).toExternalForm());

        workbench.installShortcuts(scene);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(event -> {
            if (!workbench.confirmClose()) {
                event.consume();
            }
        });
        stage.show();
        workbench.canvas().requestFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
