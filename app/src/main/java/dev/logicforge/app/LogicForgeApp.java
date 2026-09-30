package dev.logicforge.app;

import dev.logicforge.ui.AppIcons;
import dev.logicforge.ui.view.Workbench;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Starts the LogicForge workbench. The process normally enters through {@link Main}; started
 * directly as a JavaFX application (e.g. from an IDE) it parses its own arguments.
 */
public class LogicForgeApp extends Application {

    static final String STYLESHEET = "/dev/logicforge/ui/logicforge.css";

    static final double PREFERRED_WIDTH = 1360;
    static final double PREFERRED_HEIGHT = 860;
    static final double MINIMUM_WIDTH = 900;
    static final double MINIMUM_HEIGHT = 600;

    private static LaunchOptions preparedOptions;
    private static AppDirectories preparedDirectories;
    private static AppLog preparedLog;
    private static UncaughtErrorHandler preparedErrors;

    private LaunchOptions options;
    private AppDirectories directories;
    private AppLog log;

    /** Hands the start-up decisions made by {@link Main} to the instance JavaFX creates. */
    static synchronized void prepare(LaunchOptions options, AppDirectories directories, AppLog log,
                                     UncaughtErrorHandler errors) {
        preparedOptions = options;
        preparedDirectories = directories;
        preparedLog = log;
        preparedErrors = errors;
    }

    @Override
    public void init() {
        synchronized (LogicForgeApp.class) {
            directories = preparedDirectories != null ? preparedDirectories : AppDirectories.detect();
            log = preparedLog != null ? preparedLog : new AppLog(directories.logFile());
            options = preparedOptions;
        }
        if (options == null) {
            // Launched directly (e.g. from an IDE) rather than through Main.
            options = LaunchOptions.parse(getParameters().getRaw(), Path.of("").toAbsolutePath());
        }
    }

    @Override
    public void start(Stage stage) {
        UncaughtErrorHandler errors;
        synchronized (LogicForgeApp.class) {
            errors = preparedErrors != null ? preparedErrors
                    : UncaughtErrorHandler.install(log, options.mode() != LaunchOptions.Mode.SMOKE_TEST);
        }
        Thread.currentThread().setUncaughtExceptionHandler(errors);
        errors.setOwner(() -> stage.isShowing() ? stage : null);

        Workbench workbench = new Workbench(stage);
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(workbench,
                startSize(PREFERRED_WIDTH, MINIMUM_WIDTH, screen.getWidth()),
                startSize(PREFERRED_HEIGHT, MINIMUM_HEIGHT, screen.getHeight()));
        scene.getStylesheets().add(LogicForgeApp.class.getResource(STYLESHEET).toExternalForm());

        workbench.installShortcuts(scene);
        workbench.projects().setDefaultDirectory(directories.documents());
        stage.setScene(scene);
        AppIcons.apply(stage);
        // Never demand more than the screen offers, e.g. on a small laptop display.
        stage.setMinWidth(Math.min(MINIMUM_WIDTH, screen.getWidth()));
        stage.setMinHeight(Math.min(MINIMUM_HEIGHT, screen.getHeight()));
        stage.setOnCloseRequest(event -> {
            if (!workbench.confirmClose()) {
                event.consume();
            }
        });
        // Closing the workbench ends the application, Study and memory windows included.
        stage.setOnHidden(event -> Platform.exit());
        stage.show();
        workbench.canvas().requestFocus();

        if (options.mode() == LaunchOptions.Mode.SMOKE_TEST) {
            Platform.runLater(() -> SmokeTest.run(workbench, stage, options.file(), log));
            return;
        }
        if (!options.ignoredFiles().isEmpty()) {
            log.info("Only one project is opened per window; ignored " + options.ignoredFiles());
        }
        options.file().ifPresent(file -> Platform.runLater(() -> workbench.projects().open(file)));
    }

    /** The preferred size, reduced to 90 % of the screen if needed, but never below the minimum. */
    static double startSize(double preferred, double minimum, double screen) {
        return Math.max(Math.min(minimum, screen), Math.min(preferred, screen * 0.9));
    }
}
