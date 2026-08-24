package dev.logicforge.app;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.ui.command.AddComponentCommand;
import dev.logicforge.ui.command.ConnectCommand;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.view.Workbench;
import java.io.File;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Development aid: builds a demo circuit in the workbench and writes a picture of the
 * window to a file, so the look of the editor can be reviewed without clicking through it.
 *
 * <p>Run it with {@code ./gradlew :app:screenshot}. It is not part of the application and
 * is not a test.
 */
public final class WorkbenchScreenshot {

    private static Path target = Path.of("build/screenshot.png");

    private WorkbenchScreenshot() {
    }

    /**
     * The JavaFX side. Started through {@link Application#launch} from a plain main method
     * so that it also runs when JavaFX is on the class path rather than the module path.
     */
    public static final class ScreenshotApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Workbench workbench = new Workbench(stage);
        Scene scene = new Scene(workbench, 1360, 860);
        scene.getStylesheets().add(
                LogicForgeApp.class.getResource("/dev/logicforge/ui/logicforge.css").toExternalForm());
        workbench.installShortcuts(scene);
        stage.setScene(scene);
        stage.show();

        buildDemoCircuit(workbench.editor());
        workbench.canvas().zoomToFit();

        // Let the scene settle for a few pulses, then capture it.
        Platform.runLater(() -> Platform.runLater(() -> {
            WritableImage image = scene.snapshot(null);
            try {
                File file = target.toFile();
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                System.out.println("Wrote " + file.getAbsolutePath());
            } catch (Exception failure) {
                failure.printStackTrace();
            }
            Platform.exit();
        }));
    }
    }

    /** Two switches into an AND gate and an LED, plus an XOR half of a half adder. */
    private static void buildDemoCircuit(CircuitEditor editor) {
        ComponentInstance switchA = place(editor, "source.toggle", 0, 0, "A");
        ComponentInstance switchB = place(editor, "source.toggle", 0, 120, "B");
        ComponentInstance and = place(editor, "logic.and", 200, 20, "CARRY_AND");
        ComponentInstance xor = place(editor, "logic.xor", 200, 160, "SUM_XOR");
        ComponentInstance carry = place(editor, "output.led", 380, 20, "CARRY");
        ComponentInstance sum = place(editor, "output.led", 380, 160, "SUM");
        ComponentInstance probe = place(editor, "output.probe", 380, 260, "");
        ComponentInstance inverter = place(editor, "logic.not", 200, 260, "");

        wire(editor, switchA, "OUT", and, "IN0");
        wire(editor, switchB, "OUT", and, "IN1");
        wire(editor, switchA, "OUT", xor, "IN0");
        wire(editor, switchB, "OUT", xor, "IN1");
        wire(editor, and, "OUT", carry, "IN");
        wire(editor, xor, "OUT", sum, "IN");
        wire(editor, switchB, "OUT", inverter, "A");
        wire(editor, inverter, "Y", probe, "IN");

        editor.toggleInput(switchA.id());
        editor.selection().selectComponent(and.id());
    }

    private static ComponentInstance place(CircuitEditor editor, String definitionId, double x,
                                           double y, String label) {
        ParameterValues parameters = editor.definition(definitionId).orElseThrow().defaultParameters();
        ComponentInstance instance = ComponentInstance
                .create(definitionId, new CircuitPoint(x, y), parameters).withLabel(label);
        editor.execute(new AddComponentCommand(editor.document(), instance));
        return instance;
    }

    private static void wire(CircuitEditor editor, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        editor.execute(new ConnectCommand(editor.document(), Connection.create(
                new PortReference(from.id(), fromPort), new PortReference(to.id(), toPort))));
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            target = Path.of(args[0]);
        }
        Application.launch(ScreenshotApp.class, args);
    }
}
