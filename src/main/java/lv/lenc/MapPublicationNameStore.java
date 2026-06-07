package lv.lenc;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class MapPublicationNameStore {
    private static final String APP_NAME = "Localization Editor SC2 KSP";
    private static final String STORAGE_DIR = "publication-names";
    private static final String TITLE_KEY = "DocInfo/Name";
    private static final String XML_ROOT = "map-publication-names";
    private static final List<String> SUPPORTED_LANGS = List.of(
            "ruRU", "deDE", "enUS", "esMX", "esES",
            "frFR", "itIT", "plPL", "ptBR", "koKR", "zhCN", "zhTW"
    );

    private MapPublicationNameStore() {
    }

    static void rememberOpenedNames(File sourceInput,
                                    String archiveRelativePath,
                                    CustomTableView tableView,
                                    String sourceUi) {
        update(sourceInput, archiveRelativePath, tableView, sourceUi, true);
    }

    static void protectBeforeSave(File sourceInput,
                                  String archiveRelativePath,
                                  CustomTableView tableView,
                                  String sourceUi) {
        update(sourceInput, archiveRelativePath, tableView, sourceUi, true);
    }

    private static void update(File sourceInput,
                               String archiveRelativePath,
                               CustomTableView tableView,
                               String sourceUi,
                               boolean applyStoredTitles) {
        if (tableView == null || tableView.getItems() == null || tableView.getItems().isEmpty()) {
            return;
        }

        LocalizationData titleRow = findTitleRow(tableView);
        if (titleRow == null) {
            return;
        }

        try {
            Path cachePath = cachePath(sourceInput, archiveRelativePath);
            if (cachePath == null) {
                return;
            }

            Properties props = load(cachePath);
            boolean changedTable = false;
            if (applyStoredTitles && hasAnyLockedTitle(props)) {
                changedTable = applyLockedTitles(props, titleRow);
            }

            recordTitles(props, sourceInput, archiveRelativePath, titleRow, sourceUi);
            store(cachePath, props);

            if (changedTable) {
                tableView.refresh();
            }
        } catch (Exception ex) {
            AppLog.warn("[PublicationName] failed to update cache: " + ex.getMessage());
            AppLog.exception(ex);
        }
    }

    private static LocalizationData findTitleRow(CustomTableView tableView) {
        for (LocalizationData row : tableView.getItems()) {
            if (row != null && TITLE_KEY.equalsIgnoreCase(row.getKey())) {
                return row;
            }
        }
        return null;
    }

    private static boolean applyLockedTitles(Properties props, LocalizationData titleRow) {
        boolean changed = false;
        for (String lang : SUPPORTED_LANGS) {
            String locked = clean(props.getProperty("locked.title." + lang));
            if (locked.isBlank()) {
                continue;
            }
            String current = clean(titleRow.getByLang(lang));
            if (!locked.equals(current)) {
                titleRow.setByLang(lang, locked);
                changed = true;
            }
        }
        if (changed) {
            AppLog.info("[PublicationName] restored locked DocInfo/Name values before save/open");
        }
        return changed;
    }

    private static void recordTitles(Properties props,
                                     File sourceInput,
                                     String archiveRelativePath,
                                     LocalizationData titleRow,
                                     String sourceUi) {
        String now = Instant.now().toString();
        setIfAbsent(props, "created.at", now);
        props.setProperty("updated.at", now);
        props.setProperty("title.key", TITLE_KEY);
        if (sourceInput != null) {
            props.setProperty("source.file.name", clean(sourceInput.getName()));
            props.setProperty("source.file.path", sourceInput.getAbsolutePath());
        }
        if (archiveRelativePath != null && !archiveRelativePath.isBlank()) {
            props.setProperty("archive.relative.path", archiveRelativePath.replace('\\', '/'));
        }

        String normalizedSource = normalizeUi(sourceUi);
        if (!normalizedSource.isBlank()) {
            String sourceTitle = clean(titleRow.getByLang(normalizedSource));
            if (!sourceTitle.isBlank()) {
                setIfAbsent(props, "original.language", normalizedSource);
                setIfAbsent(props, "original.title", sourceTitle);
            }
        }

        for (String lang : SUPPORTED_LANGS) {
            String title = clean(titleRow.getByLang(lang));
            if (title.isBlank()) {
                continue;
            }
            setIfAbsent(props, "original.title." + lang, title);
            setIfAbsent(props, "locked.title." + lang, title);
            props.setProperty("latest.title." + lang, title);
        }
    }

    private static boolean hasAnyLockedTitle(Properties props) {
        if (props == null || props.isEmpty()) {
            return false;
        }
        for (String lang : SUPPORTED_LANGS) {
            if (!clean(props.getProperty("locked.title." + lang)).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static Properties load(Path path) {
        Properties props = new Properties();
        if (path == null || !Files.isRegularFile(path)) {
            return props;
        }
        if (loadStructuredXml(path, props)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.loadFromXML(in);
        } catch (Exception ex) {
            AppLog.warn("[PublicationName] failed to read cache " + path + ": " + ex.getMessage());
        }
        return props;
    }

    private static void store(Path path, Properties props) throws Exception {
        if (path == null || props == null || props.isEmpty()) {
            return;
        }
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            storeStructuredXml(out, props);
        }
        AppLog.info("[PublicationName] cache saved: " + path.toAbsolutePath());
    }

    private static boolean loadStructuredXml(Path path, Properties props) {
        try (InputStream in = Files.newInputStream(path)) {
            Document document = newDocumentBuilder().parse(in);
            Element root = document.getDocumentElement();
            if (root == null || !XML_ROOT.equals(root.getTagName())) {
                return false;
            }

            Element source = firstChild(root, "source");
            if (source != null) {
                setIfNotBlank(props, "created.at", source.getAttribute("createdAt"));
                setIfNotBlank(props, "updated.at", source.getAttribute("updatedAt"));
                setIfNotBlank(props, "title.key", source.getAttribute("titleKey"));
                setIfNotBlank(props, "source.file.name", source.getAttribute("fileName"));
                setIfNotBlank(props, "source.file.path", source.getAttribute("filePath"));
                setIfNotBlank(props, "archive.relative.path", source.getAttribute("archiveRelativePath"));
            }

            Element mainLanguage = firstChild(root, "main-language");
            if (mainLanguage != null) {
                setIfNotBlank(props, "original.language", mainLanguage.getAttribute("code"));
                Element original = firstChild(mainLanguage, "original");
                if (original != null) {
                    setIfNotBlank(props, "original.title", original.getTextContent());
                }
            }

            Element firstSeenTitles = firstChild(root, "first-seen-titles");
            if (firstSeenTitles != null) {
                NodeList nodes = firstSeenTitles.getElementsByTagName("title");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    if (!(node instanceof Element title)) {
                        continue;
                    }
                    String lang = normalizeUi(title.getAttribute("language"));
                    if (!lang.isBlank()) {
                        setIfNotBlank(props, "original.title." + lang, title.getTextContent());
                    }
                }
            }

            Element translations = firstChild(root, "translations");
            if (translations != null) {
                setIfNotBlank(props, "title.key", translations.getAttribute("key"));
                NodeList nodes = translations.getElementsByTagName("translation");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    if (!(node instanceof Element translation)) {
                        continue;
                    }
                    String lang = normalizeUi(translation.getAttribute("language"));
                    if (lang.isBlank()) {
                        continue;
                    }
                    setIfNotBlank(props, "original.title." + lang, textOf(translation, "original"));
                    setIfNotBlank(props, "locked.title." + lang, textOf(translation, "locked"));
                    setIfNotBlank(props, "latest.title." + lang, textOf(translation, "latest"));
                }
            }

            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void storeStructuredXml(OutputStream out, Properties props) throws Exception {
        Document document = newDocumentBuilder().newDocument();
        Element root = document.createElement(XML_ROOT);
        root.setAttribute("version", "2");
        document.appendChild(root);

        Element source = document.createElement("source");
        setAttributeIfPresent(source, "createdAt", props.getProperty("created.at"));
        setAttributeIfPresent(source, "updatedAt", props.getProperty("updated.at"));
        setAttributeIfPresent(source, "titleKey", props.getProperty("title.key", TITLE_KEY));
        setAttributeIfPresent(source, "fileName", props.getProperty("source.file.name"));
        setAttributeIfPresent(source, "filePath", props.getProperty("source.file.path"));
        setAttributeIfPresent(source, "archiveRelativePath", props.getProperty("archive.relative.path"));
        root.appendChild(source);

        String originalLanguage = clean(props.getProperty("original.language"));
        String originalTitle = clean(props.getProperty("original.title"));
        Element mainLanguage = document.createElement("main-language");
        setAttributeIfPresent(mainLanguage, "code", originalLanguage);
        Element mainOriginal = document.createElement("original");
        mainOriginal.setAttribute("key", props.getProperty("title.key", TITLE_KEY));
        mainOriginal.setTextContent(originalTitle);
        mainLanguage.appendChild(mainOriginal);
        root.appendChild(mainLanguage);

        Element firstSeenTitles = document.createElement("first-seen-titles");
        firstSeenTitles.setAttribute("key", props.getProperty("title.key", TITLE_KEY));
        setAttributeIfPresent(firstSeenTitles, "createdAt", props.getProperty("created.at"));
        for (String lang : SUPPORTED_LANGS) {
            String original = clean(props.getProperty("original.title." + lang));
            if (!original.isBlank()) {
                Element title = document.createElement("title");
                title.setAttribute("language", lang);
                title.setTextContent(original);
                firstSeenTitles.appendChild(title);
            }
        }
        root.appendChild(firstSeenTitles);

        Element translations = document.createElement("translations");
        translations.setAttribute("key", props.getProperty("title.key", TITLE_KEY));
        for (String lang : SUPPORTED_LANGS) {
            String original = clean(props.getProperty("original.title." + lang));
            String locked = clean(props.getProperty("locked.title." + lang));
            String latest = clean(props.getProperty("latest.title." + lang));
            if (original.isBlank() && locked.isBlank() && latest.isBlank()) {
                continue;
            }

            Element translation = document.createElement("translation");
            translation.setAttribute("language", lang);
            appendText(document, translation, "original", original);
            appendText(document, translation, "locked", locked);
            appendText(document, translation, "latest", latest);
            translations.appendChild(translation);
        }
        root.appendChild(translations);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(document), new StreamResult(out));
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static Element firstChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String textOf(Element parent, String childName) {
        Element child = firstChild(parent, childName);
        return child == null ? "" : clean(child.getTextContent());
    }

    private static void appendText(Document document, Element parent, String name, String value) {
        Element child = document.createElement(name);
        child.setTextContent(clean(value));
        parent.appendChild(child);
    }

    private static void setAttributeIfPresent(Element element, String name, String value) {
        String cleanValue = clean(value);
        if (!cleanValue.isBlank()) {
            element.setAttribute(name, cleanValue);
        }
    }

    private static void setIfNotBlank(Properties props, String key, String value) {
        String cleanValue = clean(value);
        if (!cleanValue.isBlank()) {
            props.setProperty(key, cleanValue);
        }
    }

    private static Path cachePath(File sourceInput, String archiveRelativePath) throws Exception {
        if (sourceInput == null) {
            return null;
        }
        String sourceName = sourceInput.getName();
        String safeName = safeFileStem(sourceName);
        String stableKey = sourceInput.getAbsolutePath().toLowerCase(Locale.ROOT)
                + "::"
                + (archiveRelativePath == null ? "" : archiveRelativePath.replace('\\', '/').toLowerCase(Locale.ROOT));
        String hash = shortSha256(stableKey);
        return storageDir().resolve(safeName + "-" + hash + ".xml");
    }

    private static Path storageDir() throws Exception {
        Path appDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path preferred = appDir.resolve(STORAGE_DIR);
        try {
            Files.createDirectories(preferred);
            if (Files.isDirectory(preferred) && Files.isWritable(preferred)) {
                return preferred;
            }
        } catch (Exception ex) {
            AppLog.warn("[PublicationName] cannot use app directory cache: " + ex.getMessage());
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path fallback = (localAppData == null || localAppData.isBlank())
                ? Path.of(System.getProperty("user.home"), "." + APP_NAME.replace(' ', '_'), STORAGE_DIR)
                : Path.of(localAppData, APP_NAME, STORAGE_DIR);
        Files.createDirectories(fallback);
        return fallback;
    }

    private static String safeFileStem(String value) {
        String stem = value == null ? "map" : value.trim();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        stem = stem.replaceAll("[^A-Za-z0-9._-]+", "_");
        stem = stem.replaceAll("_+", "_");
        if (stem.isBlank()) {
            stem = "map";
        }
        return stem.length() > 70 ? stem.substring(0, 70) : stem;
    }

    private static String shortSha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashed, 0, 8);
    }

    private static void setIfAbsent(Properties props, String key, String value) {
        String cleanValue = clean(value);
        if (cleanValue.isBlank() || props.getProperty(key) != null) {
            return;
        }
        props.setProperty(key, cleanValue);
    }

    private static String normalizeUi(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace("-", "").replace("_", "");
        if (s.length() < 4) return "";
        String lang = s.substring(0, 2).toLowerCase(Locale.ROOT);
        String region = s.substring(2).toUpperCase(Locale.ROOT);
        String normalized = lang + region;
        return SUPPORTED_LANGS.contains(normalized) ? normalized : "";
    }

    private static String clean(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("null")) return "";
        return trimmed;
    }
}
