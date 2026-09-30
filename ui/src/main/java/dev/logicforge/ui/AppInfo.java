package dev.logicforge.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Who this application is: name, stable application id, version and licence.
 *
 * <p>The version is not written here. The build generates {@code build-info.properties} from
 * the single version in {@code gradle.properties}, so the About dialog, {@code --version},
 * the packages and their metadata can never disagree.
 */
public record AppInfo(String name, String applicationId, String version, String summary,
                      String license, String homepage) {

    /** Reverse-DNS id shared by the desktop entry, the AppStream metadata and the icons. */
    public static final String APPLICATION_ID = "dev.logicforge.LogicForge";

    /** Shown when the build information is missing, e.g. classes run straight from an IDE. */
    public static final String UNKNOWN_VERSION = "development build";

    static final String RESOURCE = "/dev/logicforge/ui/build-info.properties";

    private static final AppInfo CURRENT = load();

    public AppInfo {
        requireText(name, "name");
        requireText(applicationId, "applicationId");
        requireText(version, "version");
    }

    /** The information of the running build. */
    public static AppInfo current() {
        return CURRENT;
    }

    /** "LogicForge 0.1.0" */
    public String nameAndVersion() {
        return name + " " + version;
    }

    /** Reads the generated build information, falling back to defaults for missing keys. */
    public static AppInfo from(Properties properties) {
        return new AppInfo(
                value(properties, "name", "LogicForge"),
                value(properties, "applicationId", APPLICATION_ID),
                value(properties, "version", UNKNOWN_VERSION),
                value(properties, "summary", "Digital logic simulator"),
                value(properties, "license", "MIT"),
                value(properties, "homepage", ""));
    }

    private static AppInfo load() {
        Properties properties = new Properties();
        try (InputStream stream = AppInfo.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read " + RESOURCE, failure);
        }
        return from(properties);
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }
}
