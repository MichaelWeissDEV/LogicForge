package dev.logicforge.app;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where LogicForge keeps its own files, following each platform's conventions.
 *
 * <p>On Linux this is the XDG Base Directory Specification: configuration under
 * {@code $XDG_CONFIG_HOME/logicforge}, logs and other state under
 * {@code $XDG_STATE_HOME/logicforge} and disposable data under
 * {@code $XDG_CACHE_HOME/logicforge}, with the specification's defaults below the home
 * directory when a variable is unset, empty or not absolute.
 *
 * <p>Resolving a directory never creates it; whoever writes the first file does.
 */
public record AppDirectories(Path config, Path state, Path cache, Path documents) {

    static final String APPLICATION_DIRECTORY = "logicforge";

    /** The directories for the running process. */
    public static AppDirectories detect() {
        return resolve(System.getenv(), Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", ""));
    }

    /**
     * Resolves the directories from an environment, a home directory and an OS name; kept
     * apart from {@link #detect()} so the rules can be tested without touching the machine.
     */
    static AppDirectories resolve(Map<String, String> environment, Path home, String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path documents = documentsDirectory(environment, home);
        if (os.contains("win")) {
            Path local = absolute(environment.get("LOCALAPPDATA"))
                    .orElse(home.resolve("AppData").resolve("Local"))
                    .resolve("LogicForge");
            Path roaming = absolute(environment.get("APPDATA"))
                    .orElse(home.resolve("AppData").resolve("Roaming"))
                    .resolve("LogicForge");
            return new AppDirectories(roaming, local.resolve("Logs"), local.resolve("Cache"), documents);
        }
        if (os.contains("mac")) {
            Path library = home.resolve("Library");
            return new AppDirectories(library.resolve("Application Support").resolve("LogicForge"),
                    library.resolve("Logs").resolve("LogicForge"),
                    library.resolve("Caches").resolve("LogicForge"), documents);
        }
        return new AppDirectories(
                xdg(environment, "XDG_CONFIG_HOME", home.resolve(".config")),
                xdg(environment, "XDG_STATE_HOME", home.resolve(".local").resolve("state")),
                xdg(environment, "XDG_CACHE_HOME", home.resolve(".cache")),
                documents);
    }

    /** The log file inside the state directory. */
    public Path logFile() {
        return state.resolve("logicforge.log");
    }

    private static Path xdg(Map<String, String> environment, String variable, Path fallback) {
        return absolute(environment.get(variable)).orElse(fallback).resolve(APPLICATION_DIRECTORY);
    }

    /**
     * Where file choosers start for a project without a file: the user's documents folder
     * as named in {@code user-dirs.dirs} (it is localised, e.g. {@code ~/Dokumente}), else
     * {@code ~/Documents}, else the home directory. Inside a strictly confined snap
     * {@code $HOME} is the snap's private directory, so the user's real home is used.
     */
    private static Path documentsDirectory(Map<String, String> environment, Path home) {
        Path realHome = absolute(environment.get("SNAP_REAL_HOME")).orElse(home);
        Path configHome = absolute(environment.get("XDG_CONFIG_HOME")).orElse(home.resolve(".config"));
        Optional<Path> configured = userDocumentsDirectory(configHome.resolve("user-dirs.dirs"), realHome)
                .filter(Files::isDirectory);
        if (configured.isPresent()) {
            return configured.get();
        }
        Path documents = realHome.resolve("Documents");
        return Files.isDirectory(documents) ? documents : realHome;
    }

    /** Reads {@code XDG_DOCUMENTS_DIR="$HOME/..."} from a {@code user-dirs.dirs} file. */
    static Optional<Path> userDocumentsDirectory(Path userDirs, Path home) {
        if (!Files.isRegularFile(userDirs)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(userDirs)) {
                String trimmed = line.strip();
                if (!trimmed.startsWith("XDG_DOCUMENTS_DIR=")) {
                    continue;
                }
                String value = trimmed.substring("XDG_DOCUMENTS_DIR=".length());
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.equals("$HOME") || value.equals("$HOME/")) {
                    return Optional.of(home);
                }
                if (value.startsWith("$HOME/")) {
                    return Optional.of(home.resolve(value.substring("$HOME/".length())));
                }
                return absolute(value);
            }
        } catch (java.io.IOException | RuntimeException unreadable) {
            // An unreadable or odd file just means the default folder is used.
        }
        return Optional.empty();
    }

    /** The XDG specification says relative paths are invalid and must be ignored. */
    private static Optional<Path> absolute(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }
}
