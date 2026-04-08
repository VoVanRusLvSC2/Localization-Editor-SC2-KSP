package lv.lenc;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class LanguageDetectorService {

    private static final Object LOCK = new Object();
    private static volatile boolean LOADED = false;
    private static volatile Path extractedProfilesDir;

    private final Map<String, String> supportedLanguages = new HashMap<>();

    public LanguageDetectorService() throws LangDetectException {
        ensureProfilesLoaded();

        supportedLanguages.put("ru", "ruRU");
        supportedLanguages.put("de", "deDE");
        supportedLanguages.put("en", "enUS");
        supportedLanguages.put("es", "esES");
        supportedLanguages.put("fr", "frFR");
        supportedLanguages.put("it", "itIT");
        supportedLanguages.put("pl", "plPL");
        supportedLanguages.put("pt", "ptBR");
        supportedLanguages.put("ko", "koKR");
        supportedLanguages.put("zh-cn", "zhCN");
        supportedLanguages.put("zh-tw", "zhTW");
    }

    private static void ensureProfilesLoaded() throws LangDetectException {
        if (LOADED) return;
        synchronized (LOCK) {
            if (LOADED) return;
            Path devProfiles = Paths.get("src", "main", "resources", "profiles");
            if (Files.isDirectory(devProfiles)) {
                DetectorFactory.loadProfile(devProfiles.toString());
                LOADED = true;
                return;
            }

            try {
                Path dirFromResources = extractProfilesToTempDir();
                DetectorFactory.loadProfile(dirFromResources.toString());
                LOADED = true;
            } catch (IOException ex) {
                throw new RuntimeException("Failed to prepare language profiles: " + ex.getMessage(), ex);
            }
        }
    }

    private static Path extractProfilesToTempDir() throws IOException {
        if (extractedProfilesDir != null && Files.isDirectory(extractedProfilesDir)) {
            return extractedProfilesDir;
        }

        Path tempDir = Files.createTempDirectory("langdetect-profiles-");
        tempDir.toFile().deleteOnExit();

        String[] profileNames = {
                "af", "ar", "bg", "bn", "cs", "da", "de", "el", "en", "es", "et", "fa", "fi", "fr",
                "gu", "he", "hi", "hr", "hu", "id", "it", "ja", "kn", "ko", "lt", "lv", "mk", "ml",
                "mr", "ne", "nl", "no", "pa", "pl", "pt", "ro", "ru", "sk", "sl", "so", "sq", "sv",
                "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh-cn", "zh-tw"
        };

        for (String name : profileNames) {
            String resourcePath = "/profiles/" + name;
            try (InputStream in = LanguageDetectorService.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    continue;
                }
                Path out = tempDir.resolve(name);
                Files.copy(in, out);
                out.toFile().deleteOnExit();
            }
        }

        try (Stream<Path> files = Files.list(tempDir)) {
            if (files.findAny().isEmpty()) {
                throw new IOException("Language profiles are not bundled in resources: /profiles/*");
            }
        }

        extractedProfilesDir = tempDir;
        return tempDir;
    }

    public String detectLanguage(String text) throws LangDetectException {
        Detector detector = DetectorFactory.create();
        detector.append(text);
        String lang = detector.detect();
        return supportedLanguages.getOrDefault(lang, "unknown");
    }
}
