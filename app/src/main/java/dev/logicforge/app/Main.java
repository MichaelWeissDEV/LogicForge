package dev.logicforge.app;

import dev.logicforge.ui.AppInfo;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;

/**
 * The process entry point: answers {@code --version} and {@code --help} without starting a
 * user interface, installs the error handler, then launches the JavaFX application.
 *
 * <p>Deliberately not the {@link Application} subclass itself, so the launcher never needs
 * JavaFX just to print a version.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(List.of(args), Path.of("").toAbsolutePath());
        } catch (IllegalArgumentException invalid) {
            System.err.println("logicforge: " + invalid.getMessage());
            System.err.print(LaunchOptions.USAGE);
            System.exit(2);
            return;
        }
        switch (options.mode()) {
            case VERSION -> {
                System.out.println(AppInfo.current().nameAndVersion());
                return;
            }
            case HELP -> {
                System.out.print(LaunchOptions.USAGE);
                return;
            }
            case GUI, SMOKE_TEST -> {
                // Continue below.
            }
        }

        AppDirectories directories = AppDirectories.detect();
        AppLog log = new AppLog(directories.logFile());
        UncaughtErrorHandler errors = UncaughtErrorHandler.install(log,
                options.mode() != LaunchOptions.Mode.SMOKE_TEST);
        LogicForgeApp.prepare(options, directories, log, errors);
        try {
            Application.launch(LogicForgeApp.class, args);
        } catch (RuntimeException startupFailure) {
            log.error("LogicForge could not start", startupFailure);
            System.err.println("logicforge: the user interface could not be started: "
                    + UncaughtErrorHandler.summary(startupFailure));
            if (System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null) {
                System.err.println("logicforge: no graphical session found (DISPLAY and WAYLAND_DISPLAY are unset).");
            }
            System.exit(1);
        }
    }
}
