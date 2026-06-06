package lv.lenc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void comparesGithubReleaseTagsAgainstPlainVersions() {
        assertTrue(UpdateChecker.compareVersionTokens(
                UpdateChecker.normalizeVersion("v2.3"),
                UpdateChecker.normalizeVersion("2.2")) > 0);
        assertEquals(0, UpdateChecker.compareVersionTokens(
                UpdateChecker.normalizeVersion("v2.2"),
                UpdateChecker.normalizeVersion("2.2")));
    }

    @Test
    void prefersCurrentGithubSetupZipAsset() {
        assertEquals(90, UpdateChecker.scoreAsset("Localization-Editor-SC2-KSP-2.2-setup.exe.zip"));
        assertTrue(UpdateChecker.scoreAsset("Localization-Editor-SC2-KSP-2.2-setup.exe.zip")
                > UpdateChecker.scoreAsset("Localization-Editor-SC2-KSP-portable.exe"));
    }
}
