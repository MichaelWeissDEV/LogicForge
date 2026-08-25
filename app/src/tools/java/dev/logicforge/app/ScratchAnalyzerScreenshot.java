package dev.logicforge.app;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.ui.command.AddComponentCommand;
import dev.logicforge.ui.command.ConnectCommand;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.LogicAnalyzerController;
import dev.logicforge.ui.view.Workbench;
import java.io.File;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/** Throwaway manual verification: Clock -> D Flip-Flop -> LED with the Logic Analyzer. */
public final class ScratchAnalyzerScreenshot {

    private static Path target = Path.of("build/scratch-analyzer.png");

    private ScratchAnalyzerScreenshot() {
    }

    public static final class ScratchApp extends Application {

        @Override
        public void start(Stage stage) throws Exception {
            Workbench workbench = new Workbench(stage);
            Scene scene = new Scene(workbench, 1360, 900);
            scene.getStylesheets().add(
                    LogicForgeApp.class.getResource("/dev/logicforge/ui/logicforge.css").toExternalForm());
            stage.setScene(scene);
            stage.show();

            CircuitEditor editor = workbench.editor();
            ComponentInstance clock = place(editor, "source.clock", 0, 0, "CLK1");
            ComponentInstance dff = place(editor, "sequential.d_ff", 220, 0, "DFF1");
            ComponentInstance led = place(editor, "output.led", 420, -20, "Q_LED");
            ComponentInstance sw = place(editor, "source.toggle", 0, 100, "D1");

            wire(editor, clock, "OUT", dff, "CLK");
            wire(editor, sw, "OUT", dff, "D");
            wire(editor, dff, "Q", led, "IN");

            LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
            analyzer.addSignal(new PortReference(clock.id(), "OUT"), "CLK");
            analyzer.addSignal(new PortReference(sw.id(), "OUT"), "D");
            analyzer.addSignal(new PortReference(dff.id(), "Q"), "Q");

            editor.toggleInput(sw.id());
            for (int i = 0; i < 5; i++) {
                editor.stepTime();
            }
            workbench.canvas().zoomToFit();

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
        Application.launch(ScratchApp.class, args);
    }
}
