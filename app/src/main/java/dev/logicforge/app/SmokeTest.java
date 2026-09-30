package dev.logicforge.app;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.ui.AppInfo;
import dev.logicforge.ui.view.ProjectController;
import dev.logicforge.ui.view.Workbench;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * {@code logicforge --smoke-test [FILE]}: proves that an installed package really works.
 *
 * <p>With the real window up, it opens {@code FILE} (or a copy of a bundled example) through
 * the same path as File &rarr; Open, saves it through the same path as File &rarr; Save,
 * reads the saved file back and compares, then exits with status 0. Any failure — including
 * an error that would otherwise have been a dialog — prints the reason and exits non-zero,
 * and a watchdog ends a run that hangs. This is a check for the packaging, not a feature.
 */
final class SmokeTest {

    static final String BUNDLED_EXAMPLE = "/examples/logic/gates.logic";
    static final long TIMEOUT_MILLIS = 120_000;

    private SmokeTest() {
    }

    static void run(Workbench workbench, Stage stage, Optional<Path> file, AppLog log) {
        startWatchdog();
        try {
            Path project = file.isPresent() ? file.get() : bundledExampleCopy();
            List<String> reported = new ArrayList<>();
            ProjectController projects = workbench.projects();
            projects.setErrorPresenter((title, header, detail) -> reported.add(header + ": " + detail));

            require(!stage.getIcons().isEmpty(), "the window has no application icon");
            require(!AppInfo.current().version().equals(AppInfo.UNKNOWN_VERSION),
                    "the build information (version) is missing");
            require(projects.open(project) && reported.isEmpty(),
                    "opening " + project + " failed: " + reported);
            require(workbench.editor().compileError().isEmpty(),
                    "the project does not compile: " + workbench.editor().compileError().orElse(""));
            int components = workbench.editor().document().componentCount();

            require(projects.save() && reported.isEmpty(), "saving " + project + " failed: " + reported);
            require(!workbench.editor().isDirty(), "the project is still marked as modified after saving");
            CircuitProject reloaded = ProjectFormat.load(project);
            require(ProjectFormat.toJson(reloaded).equals(ProjectFormat.toJson(workbench.editor().project())),
                    "the saved file does not read back as the same project");
            require(projects.open(project) && reported.isEmpty(), "reopening " + project + " failed: " + reported);
            require(workbench.editor().document().componentCount() == components,
                    "the reopened project has a different number of components");

            System.out.println("LogicForge smoke test passed: " + AppInfo.current().nameAndVersion()
                    + ", opened, saved and reopened " + project + " (" + components + " components)");
            Platform.exit();
        } catch (Throwable failure) {
            log.error("Smoke test failed", failure);
            System.err.println("LogicForge smoke test FAILED: " + UncaughtErrorHandler.summary(failure));
            Runtime.getRuntime().halt(1);
        }
    }

    private static Path bundledExampleCopy() throws IOException {
        Path directory = Files.createTempDirectory("logicforge-smoke-");
        directory.toFile().deleteOnExit();
        Path copy = directory.resolve("smoke-test.logic");
        try (InputStream stream = SmokeTest.class.getResourceAsStream(BUNDLED_EXAMPLE)) {
            if (stream == null) {
                throw new IOException("The bundled example " + BUNDLED_EXAMPLE + " is missing");
            }
            Files.copy(stream, copy);
        }
        copy.toFile().deleteOnExit();
        copy.resolveSibling(copy.getFileName() + ".bak").toFile().deleteOnExit();
        return copy;
    }

    private static void require(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }

    private static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            System.err.println("LogicForge smoke test FAILED: no result after "
                    + TIMEOUT_MILLIS / 1000 + " seconds");
            Runtime.getRuntime().halt(2);
        }, "smoke-test-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
