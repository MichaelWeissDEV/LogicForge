package dev.logicforge.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void theBuildInformationIsGeneratedFromTheGradleVersion() {
        AppInfo info = AppInfo.current();

        assertNotEquals(AppInfo.UNKNOWN_VERSION, info.version(),
                "build-info.properties must be on the class path of every build");
        assertEquals(System.getProperty("logicforge.version", info.version()), info.version());
        assertEquals("LogicForge", info.name());
        assertEquals(AppInfo.APPLICATION_ID, info.applicationId());
        assertEquals("LogicForge " + info.version(), info.nameAndVersion());
    }

    @Test
    void valuesAreReadAndTrimmed() {
        Properties properties = new Properties();
        properties.setProperty("version", " 1.2.3 ");
        properties.setProperty("homepage", "https://example.org");

        AppInfo info = AppInfo.from(properties);

        assertEquals("1.2.3", info.version());
        assertEquals("https://example.org", info.homepage());
    }

    @Test
    void missingOrBlankValuesFallBackToDefaults() {
        Properties properties = new Properties();
        properties.setProperty("version", "   ");

        AppInfo info = AppInfo.from(properties);

        assertEquals(AppInfo.UNKNOWN_VERSION, info.version());
        assertEquals("LogicForge", info.name());
        assertEquals("dev.logicforge.LogicForge", info.applicationId());
        assertEquals("MIT", info.license());
        assertEquals("", info.homepage());
    }

    @Test
    void theIdentityCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new AppInfo("LogicForge", "dev.logicforge.LogicForge", " ", "", "MIT", ""));
    }

    @Test
    void everyIconSizeIsBundled() {
        for (int size : AppIcons.SIZES) {
            assertNotEquals(null, AppIcons.class.getResource(AppIcons.resource(size)),
                    "icon " + size + " is missing from the resources");
        }
    }
}
