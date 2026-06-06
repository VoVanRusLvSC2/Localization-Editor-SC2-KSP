package lv.lenc;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallerLaunchCommandTest {
    @Test
    void delayedInstallerCommandDoesNotUseUnsupportedInnoFlags() {
        List<String> command = Main.delayedInstallerCommand(Path.of("C:/Temp/Localization-Editor-SC2-KSP-2.2-setup.exe"));
        String joined = String.join(" ", command);

        assertFalse(joined.contains("/VERYSILENT"));
        assertFalse(joined.contains("/SUPPRESSMSGBOXES"));
        assertTrue(joined.contains("Localization-Editor-SC2-KSP-2.2-setup.exe"));
    }

    @Test
    void msiInstallerUsesMsiexecOnWindows() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        List<String> command = Main.installerProcessBuilder(Path.of("C:/Temp/app-update.msi")).command();

        assertEqualsIgnoreCase("msiexec.exe", command.get(0));
        assertTrue(command.contains("/i"));
    }

    private static void assertEqualsIgnoreCase(String expected, String actual) {
        assertTrue(expected.equalsIgnoreCase(actual), "expected " + expected + " but was " + actual);
    }
}
