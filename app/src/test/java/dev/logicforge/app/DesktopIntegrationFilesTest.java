package dev.logicforge.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.format.ProjectFormat;
import dev.logicforge.ui.AppInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The files in packaging/linux name the application, its window, icon and file type; they must
 * agree with the code, or the desktop would not associate windows, icons or .logic files.
 */
class DesktopIntegrationFilesTest {

    private static final Path PACKAGING =
            Path.of(System.getProperty("logicforge.packaging", "../packaging/linux"));
    private static final String MIME_TYPE = "application/x-logicforge-project";

    @Test
    void theDesktopEntryMatchesTheApplication() throws IOException {
        Map<String, String> entry = desktopEntry(PACKAGING.resolve(AppInfo.APPLICATION_ID + ".desktop"));

        assertEquals(AppInfo.APPLICATION_ID, entry.get("Icon"));
        assertEquals("logicforge %f", entry.get("Exec"));
        assertEquals(MIME_TYPE + ";", entry.get("MimeType"));
        // JavaFX derives the window's WM_CLASS from the Application class name.
        assertEquals(LogicForgeApp.class.getName(), entry.get("StartupWMClass"));
    }

    @Test
    void theMimeTypeClaimsTheProjectExtension() throws IOException {
        String mime = Files.readString(PACKAGING.resolve(AppInfo.APPLICATION_ID + ".xml"));

        assertTrue(mime.contains("type=\"" + MIME_TYPE + "\""), mime);
        assertTrue(mime.contains("<glob pattern=\"*." + ProjectFormat.EXTENSION + "\"/>"), mime);
        assertTrue(mime.contains("<icon name=\"" + AppInfo.APPLICATION_ID + "\"/>"), mime);
    }

    @Test
    void theAppStreamMetadataPointsAtTheDesktopEntry() throws IOException {
        String metainfo = Files.readString(PACKAGING.resolve(AppInfo.APPLICATION_ID + ".metainfo.xml"));

        assertTrue(metainfo.contains("<id>" + AppInfo.APPLICATION_ID + "</id>"));
        assertTrue(metainfo.contains("<launchable type=\"desktop-id\">" + AppInfo.APPLICATION_ID
                + ".desktop</launchable>"));
        assertTrue(metainfo.contains("<mediatype>" + MIME_TYPE + "</mediatype>"));
        assertTrue(metainfo.contains("<project_license>" + AppInfo.current().license() + "</project_license>"));
    }

    @Test
    void everyWindowIconSizeExistsInTheIconTheme() {
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256, 512}) {
            Path icon = PACKAGING.resolve("icons/hicolor/" + size + "x" + size + "/apps/"
                    + AppInfo.APPLICATION_ID + ".png");
            assertTrue(Files.isRegularFile(icon), icon.toString());
        }
    }

    private static Map<String, String> desktopEntry(Path file) throws IOException {
        Map<String, String> keys = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file)) {
            int equals = line.indexOf('=');
            if (!line.startsWith("#") && equals > 0) {
                keys.putIfAbsent(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return keys;
    }
}
