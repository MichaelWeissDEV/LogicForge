package dev.logicforge.ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * The application icon in the sizes a window manager asks for.
 *
 * <p>The images are the same files the Linux packages install into the hicolor icon theme;
 * the build copies them into this module's resources, so there is one set of icons.
 */
public final class AppIcons {

    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private static List<Image> images;

    private AppIcons() {
    }

    /** Gives {@code stage} the application icon. Missing icon resources are not an error. */
    public static void apply(Stage stage) {
        stage.getIcons().setAll(images());
    }

    /** The icon images, smallest first; empty when the resources are not on the class path. */
    public static synchronized List<Image> images() {
        if (images == null) {
            List<Image> loaded = new ArrayList<>();
            for (int size : SIZES) {
                try (InputStream stream = AppIcons.class.getResourceAsStream(resource(size))) {
                    if (stream != null) {
                        loaded.add(new Image(stream));
                    }
                } catch (java.io.IOException ignored) {
                    // A missing size only means the window manager scales another one.
                }
            }
            images = List.copyOf(loaded);
        }
        return images;
    }

    static String resource(int size) {
        return "/dev/logicforge/ui/icons/logicforge-" + size + ".png";
    }
}
