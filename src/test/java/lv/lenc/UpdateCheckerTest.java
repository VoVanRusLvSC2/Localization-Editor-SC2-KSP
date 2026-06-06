package lv.lenc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void comparesGithubReleaseTagsAgainstPlainVersions() {
        assertTrue(UpdateChecker.compareVersionTokens(
                UpdateChecker.normalizeVersion("v2.2"),
                UpdateChecker.normalizeVersion("2.1")) > 0);
        assertEquals(0, UpdateChecker.compareVersionTokens(
                UpdateChecker.normalizeVersion("v2.1"),
                UpdateChecker.normalizeVersion("2.1")));
    }

    @Test
    void prefersCurrentGithubSetupZipAsset() {
        assertEquals(90, UpdateChecker.scoreAsset("Localization-Editor-SC2-KSP-2.1-setup.exe.zip"));
        assertTrue(UpdateChecker.scoreAsset("Localization-Editor-SC2-KSP-2.1-setup.exe.zip")
                > UpdateChecker.scoreAsset("Localization-Editor-SC2-KSP-portable.exe"));
    }
}
