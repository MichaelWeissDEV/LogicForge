package dev.logicforge.ui.view;

import dev.logicforge.ui.AppIcons;
import dev.logicforge.ui.AppInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Help &rarr; About LogicForge: name, version, licence and a one-line description. */
public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(Window owner) {
        AppInfo info = AppInfo.current();
        Alert alert = Dialogs.alert(Alert.AlertType.NONE, owner, "About " + info.name(), null, null);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.getDialogPane().setContent(content(info));
        alert.showAndWait();
    }

    static VBox content(AppInfo info) {
        Label name = new Label(info.name());
        name.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label version = new Label("Version " + info.version());
        Label summary = new Label(info.summary()
                + " with four-state signals, hierarchical circuits, a logic analyzer"
                + " and the programmable LF-8 computer.");
        summary.setWrapText(true);
        summary.setMaxWidth(380);
        Label license = new Label("Released under the " + info.license() + " License.");

        VBox box = new VBox(8);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(12, 24, 4, 24));
        image().ifPresent(icon -> box.getChildren().add(icon));
        box.getChildren().addAll(name, version, summary, license);
        if (!info.homepage().isBlank()) {
            // Selectable rather than a link: LogicForge never opens network connections itself.
            TextField homepage = new TextField(info.homepage());
            homepage.setEditable(false);
            homepage.setFocusTraversable(false);
            homepage.setPrefColumnCount(Math.max(20, info.homepage().length()));
            box.getChildren().add(homepage);
        }
        return box;
    }

    private static java.util.Optional<ImageView> image() {
        return AppIcons.images().stream()
                .filter(image -> image.getWidth() >= 128)
                .findFirst()
                .map(AboutDialog::view);
    }

    private static ImageView view(Image image) {
        ImageView view = new ImageView(image);
        view.setFitWidth(96);
        view.setFitHeight(96);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
