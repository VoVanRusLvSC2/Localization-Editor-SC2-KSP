package lv.lenc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileUtilSaveFailureTest {
    @TempDir
    Path tempDir;

    @Test
    void missingSourceArchiveReportsActionableSaveFailure() throws Exception {
        Path openedFile = tempDir.resolve("GameStrings.txt");
        Files.writeString(openedFile, "DocInfo/Name=Test");

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

    @Test
    void sc2MapDirectorySavesAsFolderProjectInsteadOfMpqArchive() throws Exception {
        Path mapDir = tempDir.resolve("Test.SC2Map");
        Path openedFile = mapDir.resolve("enUS.SC2Data").resolve("LocalizedData").resolve("GameStrings.txt");
        Files.createDirectories(openedFile.getParent());
        Files.writeString(openedFile, "DocInfo/Name=Original");

        boolean ok = FileUtil.saveToTargetLanguage(
                openedFile.toFile(),
                mapDir.toFile(),
                mapDir.toFile(),
                "GameStrings.txt",
                "ruRU",
                "DocInfo/Name=Перевод"
        );

        assertTrue(ok, FileUtil.getLastSaveFailureMessage());
        Path translatedFile = mapDir.resolve("ruRU.SC2Data").resolve("LocalizedData").resolve("GameStrings.txt");
        assertTrue(Files.readString(translatedFile).contains("Перевод"));
    }

    @Test
    void writeUtf8AtomicClearsWindowsReadOnlyOnFoldersAndFiles() throws Exception {
        Path dir = tempDir.resolve("readonly-folder");
        Files.createDirectories(dir);
        Path file = dir.resolve("GameStrings.txt");
        Files.writeString(file, "old");

        DosFileAttributeView dirView = Files.getFileAttributeView(dir, DosFileAttributeView.class);
        DosFileAttributeView fileView = Files.getFileAttributeView(file, DosFileAttributeView.class);
        assumeTrue(dirView != null && fileView != null);
        dirView.setReadOnly(true);
        fileView.setReadOnly(true);

        FileUtil.writeUtf8Atomic(file.toFile(), "new");

        assertTrue(Files.readString(file).contains("new"));
        assertFalse(dirView.readAttributes().isReadOnly());
        assertFalse(fileView.readAttributes().isReadOnly());
    }
}
