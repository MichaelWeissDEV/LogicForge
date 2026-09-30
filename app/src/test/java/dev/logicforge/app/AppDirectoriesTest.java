package dev.logicforge.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppDirectoriesTest {

    @TempDir
    Path home;

    @Test
    void linuxDefaultsFollowTheXdgSpecification() {
        AppDirectories directories = AppDirectories.resolve(Map.of(), home, "Linux");

        assertEquals(home.resolve(".config/logicforge"), directories.config());
        assertEquals(home.resolve(".local/state/logicforge"), directories.state());
        assertEquals(home.resolve(".cache/logicforge"), directories.cache());
        assertEquals(home.resolve(".local/state/logicforge/logicforge.log"), directories.logFile());
    }

    @Test
    void xdgVariablesWin() {
        Map<String, String> environment = Map.of(
                "XDG_CONFIG_HOME", "/custom/config",
                "XDG_STATE_HOME", "/custom/state",
                "XDG_CACHE_HOME", "/custom/cache");

        AppDirectories directories = AppDirectories.resolve(environment, home, "Linux");

        assertEquals(Path.of("/custom/config/logicforge"), directories.config());
        assertEquals(Path.of("/custom/state/logicforge"), directories.state());
        assertEquals(Path.of("/custom/cache/logicforge"), directories.cache());
    }

    @Test
    void relativeOrEmptyXdgVariablesAreIgnoredAsTheSpecificationRequires() {
        Map<String, String> environment = Map.of(
                "XDG_CONFIG_HOME", "relative/config",
                "XDG_STATE_HOME", "",
                "XDG_CACHE_HOME", "   ");

        AppDirectories directories = AppDirectories.resolve(environment, home, "Linux");

        assertEquals(home.resolve(".config/logicforge"), directories.config());
        assertEquals(home.resolve(".local/state/logicforge"), directories.state());
        assertEquals(home.resolve(".cache/logicforge"), directories.cache());
    }

    @Test
    void resolvingCreatesNothing() throws IOException {
        AppDirectories.resolve(Map.of(), home, "Linux");

        try (var entries = Files.list(home)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void documentsFallBackToTheHomeDirectory() {
        assertEquals(home, AppDirectories.resolve(Map.of(), home, "Linux").documents());
    }

    @Test
    void theEnglishDocumentsFolderIsUsedWhenItExists() throws IOException {
        Path documents = Files.createDirectory(home.resolve("Documents"));

        assertEquals(documents, AppDirectories.resolve(Map.of(), home, "Linux").documents());
    }

    @Test
    void aLocalisedDocumentsFolderFromUserDirsIsUsed() throws IOException {
        Path documents = Files.createDirectory(home.resolve("Dokumente"));
        Files.createDirectories(home.resolve(".config"));
        Files.writeString(home.resolve(".config/user-dirs.dirs"), """
                # This file is written by xdg-user-dirs-update
                XDG_DESKTOP_DIR="$HOME/Schreibtisch"
                XDG_DOCUMENTS_DIR="$HOME/Dokumente"
                """);

        assertEquals(documents, AppDirectories.resolve(Map.of(), home, "Linux").documents());
    }

    @Test
    void userDirsAcceptAbsolutePathsAndIgnoreGarbage() throws IOException {
        Path userDirs = home.resolve("user-dirs.dirs");
        Files.writeString(userDirs, "XDG_DOCUMENTS_DIR=\"/srv/docs\"\n");
        assertEquals(Optional.of(Path.of("/srv/docs")), AppDirectories.userDocumentsDirectory(userDirs, home));

        Files.writeString(userDirs, "XDG_DOCUMENTS_DIR=\"relative/docs\"\n");
        assertEquals(Optional.empty(), AppDirectories.userDocumentsDirectory(userDirs, home));

        assertEquals(Optional.empty(), AppDirectories.userDocumentsDirectory(home.resolve("missing"), home));
    }

    @Test
    void insideASnapTheRealHomeIsOfferedForDocuments() throws IOException {
        Path realHome = Files.createDirectory(home.resolve("real-home"));
        Path documents = Files.createDirectory(realHome.resolve("Documents"));
        Path snapHome = Files.createDirectories(home.resolve("snap/logicforge/x1"));

        AppDirectories directories = AppDirectories.resolve(
                Map.of("SNAP_REAL_HOME", realHome.toString()), snapHome, "Linux");

        assertEquals(documents, directories.documents());
        assertEquals(snapHome.resolve(".local/state/logicforge"), directories.state(),
                "the application's own files stay inside the snap's writable area");
    }

    @Test
    void otherPlatformsUseTheirConventions() {
        AppDirectories mac = AppDirectories.resolve(Map.of(), home, "Mac OS X");
        assertEquals(home.resolve("Library/Logs/LogicForge"), mac.state());

        AppDirectories windows = AppDirectories.resolve(
                Map.of("LOCALAPPDATA", home.resolve("Local").toString()), home, "Windows 11");
        assertEquals(home.resolve("Local/LogicForge/Logs"), windows.state());
        assertFalse(windows.config().toString().isBlank());
    }
}
