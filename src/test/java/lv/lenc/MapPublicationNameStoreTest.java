package lv.lenc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapPublicationNameStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesReadableStructuredXmlWithFirstSeenTitles() throws Exception {
        Properties props = new Properties();
        props.setProperty("created.at", "2026-06-07T10:00:00Z");
        props.setProperty("updated.at", "2026-06-07T11:00:00Z");
        props.setProperty("title.key", "DocInfo/Name");
        props.setProperty("source.file.name", "Nydus Conspiracy KSP.SC2Map");
        props.setProperty("source.file.path", "C:\\Maps\\Nydus Conspiracy KSP.SC2Map");
        props.setProperty("archive.relative.path", "GameStrings.txt");
        props.setProperty("original.language", "ruRU");
        props.setProperty("original.title", "Нидус-Заговор KSP");
        props.setProperty("original.title.ruRU", "Нидус-Заговор KSP");
        props.setProperty("original.title.enUS", "Nydus Conspiracy KSP");
        props.setProperty("locked.title.ruRU", "Нидус-Заговор KSP");
        props.setProperty("locked.title.enUS", "Nydus Conspiracy KSP");
        props.setProperty("latest.title.enUS", "Nydus Conspiracy KSP");

        Path cacheFile = tempDir.resolve("publication.xml");
        invokeStore(cacheFile, props);

        String xml = java.nio.file.Files.readString(cacheFile);
        assertTrue(xml.contains("<map-publication-names version=\"2\">"));
        assertTrue(xml.contains("<main-language code=\"ruRU\">"));
        assertTrue(xml.contains("<first-seen-titles createdAt=\"2026-06-07T10:00:00Z\" key=\"DocInfo/Name\">"));
        assertTrue(xml.contains("<title language=\"ruRU\">Нидус-Заговор KSP</title>"));
        assertTrue(xml.contains("<translation language=\"enUS\">"));

        Properties loaded = invokeLoad(cacheFile);
        assertEquals("ruRU", loaded.getProperty("original.language"));
        assertEquals("Нидус-Заговор KSP", loaded.getProperty("original.title"));
        assertEquals("Nydus Conspiracy KSP", loaded.getProperty("original.title.enUS"));
        assertEquals("Nydus Conspiracy KSP", loaded.getProperty("locked.title.enUS"));
    }

    private static void invokeStore(Path path, Properties props) throws Exception {
        Method method = MapPublicationNameStore.class.getDeclaredMethod("store", Path.class, Properties.class);
        method.setAccessible(true);
        method.invoke(null, path, props);
    }

    private static Properties invokeLoad(Path path) throws Exception {
        Method method = MapPublicationNameStore.class.getDeclaredMethod("load", Path.class);
        method.setAccessible(true);
        return (Properties) method.invoke(null, path);
    }
}
