package lv.lenc;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class StormLibBridge {
    private static final int BASE_PROVIDER_FILE = 0x00000000;
    private static final int SFILE_OPEN_FROM_MPQ = 0x00000000;
    private static final int MPQ_FILE_COMPRESS = 0x00000200;
    private static final int MPQ_FILE_REPLACEEXISTING = 0x80000000;
    private static final int MPQ_COMPRESSION_ZLIB = 0x02;
    private static final int MPQ_COMPRESSION_NEXT_SAME = 0xFFFFFFFF;

    private static volatile StormLib api;
    private static volatile IOException loadError;

    private StormLibBridge() {
    }

    interface StormLib extends StdCallLibrary {
        boolean SFileOpenArchive(WString szMpqName, int dwPriority, int dwFlags, WinNT.HANDLEByReference phMpq);

        boolean SFileCloseArchive(WinNT.HANDLE hMpq);

        boolean SFileFlushArchive(WinNT.HANDLE hMpq);

        boolean SFileCompactArchive(WinNT.HANDLE hMpq, WString szListFile, boolean bReserved);

        boolean SFileHasFile(WinNT.HANDLE hMpq, String szFileName);

        boolean SFileRemoveFile(WinNT.HANDLE hMpq, String szFileName, int dwSearchScope);

        boolean SFileExtractFile(WinNT.HANDLE hMpq,
                                 String szToExtract,
                                 WString szExtracted,
                                 int dwSearchScope);

        boolean SFileAddFileEx(WinNT.HANDLE hMpq,
                               WString szFileName,
                               String szArchivedName,
                               int dwFlags,
                               int dwCompression,
                               int dwCompressionNext);
    }

    static boolean isAvailable() {
        try {
            return api() != null;
        } catch (IOException ex) {
            AppLog.warn("[StormLib] unavailable: " + ex.getMessage());
            return false;
        }
    }

    static void upsertEntries(File archiveFile, Map<String, File> entries) throws IOException {
        if (archiveFile == null || entries == null || entries.isEmpty()) {
            return;
        }

        StormLib lib = api();
        WinNT.HANDLEByReference archiveRef = new WinNT.HANDLEByReference();
        boolean opened = lib.SFileOpenArchive(new WString(archiveFile.getAbsolutePath()), 0, BASE_PROVIDER_FILE, archiveRef);
        ensure(opened, "SFileOpenArchive", archiveFile.getAbsolutePath());

        WinNT.HANDLE archiveHandle = archiveRef.getValue();
        if (archiveHandle == null || archiveHandle.getPointer() == null) {
            throw new IOException("[StormLib] archive handle is null: " + archiveFile.getAbsolutePath());
        }

        try {
            for (Map.Entry<String, File> entry : entries.entrySet()) {
                upsertEntry(lib, archiveHandle, entry.getKey(), entry.getValue());
            }

            ensure(lib.SFileFlushArchive(archiveHandle), "SFileFlushArchive", archiveFile.getAbsolutePath());
            ensure(lib.SFileCompactArchive(archiveHandle, null, false), "SFileCompactArchive", archiveFile.getAbsolutePath());
            ensure(lib.SFileFlushArchive(archiveHandle), "SFileFlushArchive(post-compact)", archiveFile.getAbsolutePath());
        } finally {
            if (!lib.SFileCloseArchive(archiveHandle)) {
                AppLog.warn("[StormLib] SFileCloseArchive failed, code=" + Native.getLastError());
            }
        }
    }

    static void validateArchive(File archiveFile, String... requiredEntries) throws IOException {
        if (archiveFile == null) {
            throw new IOException("[StormLib] archiveFile is null");
        }

        StormLib lib = api();
        WinNT.HANDLEByReference archiveRef = new WinNT.HANDLEByReference();
        boolean opened = lib.SFileOpenArchive(new WString(archiveFile.getAbsolutePath()), 0, BASE_PROVIDER_FILE, archiveRef);
        ensure(opened, "SFileOpenArchive(validate)", archiveFile.getAbsolutePath());

        WinNT.HANDLE archiveHandle = archiveRef.getValue();
        if (archiveHandle == null || archiveHandle.getPointer() == null) {
            throw new IOException("[StormLib] validate archive handle is null: " + archiveFile.getAbsolutePath());
        }

        try {
            if (requiredEntries != null) {
                for (String entry : requiredEntries) {
                    if (entry == null || entry.isBlank()) continue;
                    if (!lib.SFileHasFile(archiveHandle, normalizeArchivedName(entry))) {
                        throw new IOException("[StormLib] archive entry missing after save: " + entry);
                    }
                }
            }
        } finally {
            if (!lib.SFileCloseArchive(archiveHandle)) {
                AppLog.warn("[StormLib] validate close failed, code=" + Native.getLastError());
            }
        }
    }

    static boolean hasFile(File archiveFile, String archivedName) throws IOException {
        if (archiveFile == null) {
            return false;
        }
        try (ArchiveHandle archive = openArchive(archiveFile, "SFileOpenArchive(hasFile)")) {
            return archive.lib.SFileHasFile(archive.handle, normalizeArchivedName(archivedName));
        }
    }

    static void extractEntry(File archiveFile, String archivedName, File outputFile) throws IOException {
        if (archiveFile == null) {
            throw new IOException("[StormLib] archiveFile is null");
        }
        if (archivedName == null || archivedName.isBlank()) {
            throw new IOException("[StormLib] archivedName is blank");
        }
        if (outputFile == null) {
            throw new IOException("[StormLib] outputFile is null");
        }

        File parent = outputFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        try (ArchiveHandle archive = openArchive(archiveFile, "SFileOpenArchive(extract)")) {
            String normalizedName = normalizeArchivedName(archivedName);
            ensure(archive.lib.SFileHasFile(archive.handle, normalizedName),
                    "SFileHasFile",
                    normalizedName);
            ensure(archive.lib.SFileExtractFile(
                            archive.handle,
                            normalizedName,
                            new WString(outputFile.getAbsolutePath()),
                            SFILE_OPEN_FROM_MPQ),
                    "SFileExtractFile",
                    normalizedName);
        }
    }

    static List<String> readListfileEntries(File archiveFile) throws IOException {
        if (archiveFile == null) {
            return Collections.emptyList();
        }
        try (ArchiveHandle archive = openArchive(archiveFile, "SFileOpenArchive(listfile)")) {
            String listfileName = "(listfile)";
            if (!archive.lib.SFileHasFile(archive.handle, listfileName)) {
                return Collections.emptyList();
            }

            Path tempFile = Files.createTempFile("le-storm-listfile-", ".txt");
            try {
                ensure(archive.lib.SFileExtractFile(
                                archive.handle,
                                listfileName,
                                new WString(tempFile.toAbsolutePath().toString()),
                                SFILE_OPEN_FROM_MPQ),
                        "SFileExtractFile",
                        listfileName);

                LinkedHashSet<String> entries = new LinkedHashSet<>();
                for (String line : Files.readAllLines(tempFile, StandardCharsets.UTF_8)) {
                    String trimmed = line == null ? "" : line.trim();
                    if (!trimmed.isEmpty()) {
                        entries.add(trimmed.replace('/', '\\'));
                    }
                }
                return new ArrayList<>(entries);
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void upsertEntry(StormLib lib, WinNT.HANDLE archiveHandle, String archivedName, File payloadFile) throws IOException {
        if (archivedName == null || archivedName.isBlank()) {
            throw new IOException("[StormLib] archivedName is blank");
        }
        if (payloadFile == null || !payloadFile.isFile()) {
            throw new IOException("[StormLib] payload file is missing: " + payloadFile);
        }

        String normalizedName = normalizeArchivedName(archivedName);
        if (lib.SFileHasFile(archiveHandle, normalizedName)) {
            ensure(lib.SFileRemoveFile(archiveHandle, normalizedName, SFILE_OPEN_FROM_MPQ),
                    "SFileRemoveFile",
                    normalizedName);
        }

        int flags = MPQ_FILE_COMPRESS | MPQ_FILE_REPLACEEXISTING;
        boolean added = lib.SFileAddFileEx(
                archiveHandle,
                new WString(payloadFile.getAbsolutePath()),
                normalizedName,
                flags,
                MPQ_COMPRESSION_ZLIB,
                MPQ_COMPRESSION_NEXT_SAME
        );
        ensure(added, "SFileAddFileEx", normalizedName);
    }

    private static String normalizeArchivedName(String archivedName) {
        return archivedName.replace('/', '\\');
    }

    private static void ensure(boolean ok, String operation, String details) throws IOException {
        if (ok) return;
        int code = Native.getLastError();
        throw new IOException("[StormLib] " + operation + " failed, code=" + code + ", target=" + details);
    }

    private static ArchiveHandle openArchive(File archiveFile, String operation) throws IOException {
        if (archiveFile == null || !archiveFile.isFile()) {
            throw new IOException("[StormLib] archive file is missing: " + archiveFile);
        }
        StormLib lib = api();
        WinNT.HANDLEByReference archiveRef = new WinNT.HANDLEByReference();
        boolean opened = lib.SFileOpenArchive(new WString(archiveFile.getAbsolutePath()), 0, BASE_PROVIDER_FILE, archiveRef);
        ensure(opened, operation, archiveFile.getAbsolutePath());

        WinNT.HANDLE archiveHandle = archiveRef.getValue();
        if (archiveHandle == null || archiveHandle.getPointer() == null) {
            throw new IOException("[StormLib] archive handle is null: " + archiveFile.getAbsolutePath());
        }
        return new ArchiveHandle(lib, archiveHandle, archiveFile.getAbsolutePath());
    }

    private static StormLib api() throws IOException {
        if (api != null) {
            return api;
        }
        if (loadError != null) {
            throw loadError;
        }
        synchronized (StormLibBridge.class) {
            if (api != null) {
                return api;
            }
            if (loadError != null) {
                throw loadError;
            }

            try {
                if (!isWindows()) {
                    throw new IOException("StormLib save backend is available only on Windows");
                }
                Path dllPath = extractBundledDll();
                // Keep the W-suffix function mapper so StormLib functions resolve correctly,
                // but remove the UNICODE type mapper: archive entry name parameters
                // (szArchivedName, szFileName in SFileHasFile etc.) are always ANSI LPCSTR
                // in StormLib, never LPWSTR.  Passing them as a wide string truncates the
                // entry name to its first character – e.g. "deDE.SC2Data\\..." becomes "d".
                java.util.HashMap<String, Object> stormOpts = new java.util.HashMap<>(W32APIOptions.UNICODE_OPTIONS);
                stormOpts.remove("type-mapper"); // String stays as char* (ANSI)
                api = Native.load(dllPath.toAbsolutePath().toString(), StormLib.class, stormOpts);
                AppLog.info("[StormLib] loaded -> " + dllPath.toAbsolutePath());
                return api;
            } catch (IOException ex) {
                loadError = ex;
                throw ex;
            } catch (Throwable ex) {
                loadError = new IOException("Failed to load StormLib: " + ex.getMessage(), ex);
                throw loadError;
            }
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static Path extractBundledDll() throws IOException {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String variant = arch.contains("64") ? "x64" : "x86";
        String resourcePath = "/native/windows/" + variant + "/StormLib.dll";

        try (InputStream in = StormLibBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Bundled StormLib resource not found: " + resourcePath);
            }
            Path dir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "le-stormlib", "v9.31", variant));
            Path out = dir.resolve("StormLib.dll");
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            out.toFile().deleteOnExit();
            return out;
        }
    }

    private static final class ArchiveHandle implements AutoCloseable {
        private final StormLib lib;
        private final WinNT.HANDLE handle;
        private final String target;

        private ArchiveHandle(StormLib lib, WinNT.HANDLE handle, String target) {
            this.lib = lib;
            this.handle = handle;
            this.target = target;
        }

        @Override
        public void close() {
            if (!lib.SFileCloseArchive(handle)) {
                AppLog.warn("[StormLib] SFileCloseArchive failed, code=" + Native.getLastError() + ", target=" + target);
            }
        }
    }
}
