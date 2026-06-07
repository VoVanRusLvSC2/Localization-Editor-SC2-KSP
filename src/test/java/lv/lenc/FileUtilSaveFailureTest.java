package lv.lenc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilSaveFailureTest {
    @TempDir
    Path tempDir;

    @Test
    void missingSourceArchiveReportsActionableSaveFailure() throws Exception {
        Path openedFile = tempDir.resolve("GameStrings.txt");
        java.nio.file.Files.writeString(openedFile, "DocInfo/Name=Test");

        File missingArchive = tempDir.resolve("missing.SC2Mod").toFile();
        boolean ok = FileUtil.saveToTargetLanguage(
                openedFile.toFile(),
                null,
                missingArchive,
                "GameStrings.txt",
                "ruRU",
                "DocInfo/Name=Test"
        );

        assertFalse(ok);
        String message = FileUtil.getLastSaveFailureMessage();
        assertTrue(message.contains("source map/mod archive is missing"), message);
        assertTrue(message.contains("reopen the map from a local writable copy"), message);
    }
}
