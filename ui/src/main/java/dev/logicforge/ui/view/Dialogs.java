package dev.logicforge.ui.view;

import dev.logicforge.ui.AppIcons;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Consistent message dialogs: the application's look and icon, text that wraps instead of
 * being cut off, and optional details that stay folded away until asked for.
 */
public final class Dialogs {

    private static final double TEXT_WIDTH = 460;

    private Dialogs() {
    }

    /** Shows an error and waits until it is dismissed. */
    public static void error(Window owner, String title, String header, String detail) {
        Alert alert = alert(Alert.AlertType.ERROR, owner, title, header, detail);
        alert.showAndWait();
    }

    /**
     * Shows an error with a technical explanation (such as a stack trace) that is only
     * visible after expanding the dialog.
     */
    public static void errorWithDetails(Window owner, String title, String header, String detail,
                                        String technicalDetails) {
        Alert alert = alert(Alert.AlertType.ERROR, owner, title, header, detail);
        TextArea details = new TextArea(technicalDetails);
        details.setEditable(false);
        details.setWrapText(false);
        details.setPrefRowCount(14);
        details.setPrefColumnCount(80);
        VBox.setVgrow(details, Priority.ALWAYS);
        alert.getDialogPane().setExpandableContent(new VBox(details));
        alert.showAndWait();
    }

    /** Asks a yes/no question; {@code true} if the user confirmed. */
    public static boolean confirm(Window owner, String title, String header, String detail,
                                  String confirmLabel) {
        Alert alert = alert(Alert.AlertType.CONFIRMATION, owner, title, header, detail);
        ButtonType confirm = new ButtonType(confirmLabel, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(confirm, ButtonType.CANCEL);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == confirm;
    }

    /** A pre-configured alert for callers that need their own buttons. */
    public static Alert alert(Alert.AlertType type, Window owner, String title, String header,
                              String detail) {
        Alert alert = new Alert(type);
        if (owner != null && owner.isShowing()) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(header);
        DialogPane pane = alert.getDialogPane();
        if (detail != null && !detail.isBlank()) {
            Label content = new Label(detail);
            content.setWrapText(true);
            content.setMaxWidth(TEXT_WIDTH);
            content.setMinHeight(Region.USE_PREF_SIZE);
            pane.setContent(content);
        }
        // Without this a long message gets an ellipsis instead of a taller dialog.
        pane.setMinHeight(Region.USE_PREF_SIZE);
        alert.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            pane.getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage dialogStage) {
            AppIcons.apply(dialogStage);
        }
        return alert;
    }
}
