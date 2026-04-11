package lv.lenc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/VoVanRusLvSC2/Localization-Editor-SC2-KSP/releases/latest";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);

    private UpdateChecker() {
    }

    static Optional<UpdateInfo> checkLatest() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(DEFAULT_TIMEOUT)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_API))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LocalizationEditorSC2KSP/UpdateChecker")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonElement parsed = JsonParser.parseString(res.body());
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject root = parsed.getAsJsonObject();
            String latestTag = readAsText(root, "tag_name");
            String htmlUrl = readAsText(root, "html_url");
            if (latestTag == null || latestTag.isBlank() || htmlUrl == null || htmlUrl.isBlank()) {
                return Optional.empty();
            }

            String current = currentVersion();
            if (current == null || current.isBlank()) {
                AppLog.warn("[Update] current version is unknown");
                return Optional.empty();
            }
            DownloadAsset preferredAsset = pickPreferredAsset(root);
            boolean hasUpdate = compareVersionTokens(normalizeVersion(latestTag), normalizeVersion(current)) > 0;
            return Optional.of(new UpdateInfo(
                    current,
                    latestTag,
                    htmlUrl,
                    preferredAsset == null ? null : preferredAsset.downloadUrl,
                    preferredAsset == null ? null : preferredAsset.fileName,
                    hasUpdate
            ));
        } catch (Exception ex) {
            AppLog.warn("[Update] check failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    static String currentVersion() {
        try {
            String impl = trimToNull(UpdateChecker.class.getPackage().getImplementationVersion());
            if (impl != null) {
                return impl;
            }
        } catch (Exception ignored) {
        }

        // Runtime fallback: parse version from bundled jar name.
        try {
            URL location = UpdateChecker.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                String name = Path.of(location.toURI()).getFileName().toString();
                Matcher m = Pattern.compile("-(\\d+(?:\\.\\d+){1,3})\\.jar$", Pattern.CASE_INSENSITIVE).matcher(name);
                if (m.find()) {
                    String v = trimToNull(m.group(1));
                    if (v != null) {
                        return v;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Runtime fallback: read Maven pom.properties bundled in jar.
        try (InputStream in = UpdateChecker.class.getClassLoader()
                .getResourceAsStream("META-INF/maven/lv.lenc/Localization_Editor_SC2_KSP/pom.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = trimToNull(props.getProperty("version"));
                if (v != null) {
                    return v;
                }
            }
        } catch (Exception ignored) {
        }

        // Dev mode fallback: read version from pom.xml in workspace.
        try {
            Path pom = Path.of("pom.xml");
            if (Files.isRegularFile(pom)) {
                for (String line : Files.readAllLines(pom, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("<version>") && trimmed.endsWith("</version>")) {
                        String v = trimmed.replace("<version>", "").replace("</version>", "").trim();
                        if (!v.isBlank()) {
                            return v;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return null;
    }

    private static String normalizeVersion(String raw) {
        String s = trimToNull(raw);
        if (s == null) {
            return "0.0.0";
        }
        s = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("v")) {
            s = s.substring(1);
        }
        return s;
    }

    private static int compareVersionTokens(String left, String right) {
        String[] a = left.split("[^0-9]+");
        String[] b = right.split("[^0-9]+");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int ai = (i < a.length && !a[i].isBlank()) ? parseSafe(a[i]) : 0;
            int bi = (i < b.length && !b[i].isBlank()) ? parseSafe(b[i]) : 0;
            int cmp = Integer.compare(ai, bi);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int parseSafe(String n) {
        try {
            return Integer.parseInt(n);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String readAsText(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) {
                return null;
            }
            return trimToNull(el.getAsString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static DownloadAsset pickPreferredAsset(JsonObject releaseRoot) {
        if (releaseRoot == null || !releaseRoot.has("assets") || !releaseRoot.get("assets").isJsonArray()) {
            return null;
        }
        return releaseRoot.getAsJsonArray("assets")
                .asList()
                .stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(UpdateChecker::assetFromJson)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparingInt(a -> a.priority))
                .orElse(null);
    }

    private static Optional<DownloadAsset> assetFromJson(JsonObject asset) {
        String name = readAsText(asset, "name");
        String url = readAsText(asset, "browser_download_url");
        if (name == null || url == null) {
            return Optional.empty();
        }
        int priority = scoreAsset(name);
        if (priority <= 0) {
            return Optional.empty();
        }
        return Optional.of(new DownloadAsset(name, url, priority));
    }

    private static int scoreAsset(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.contains("-setup.exe")) {
            return n.endsWith(".zip") ? 90 : 100;
        }
        if (n.endsWith(".exe")) {
            return 70;
        }
        if (n.endsWith(".exe.zip")) {
            return 60;
        }
        if (n.endsWith(".msi")) {
            return 50;
        }
        return 0;
    }

    static final class UpdateInfo {
        final String currentVersion;
        final String latestVersion;
        final String releaseUrl;
        final String downloadUrl;
        final String downloadFileName;
        final boolean hasUpdate;

        UpdateInfo(
                String currentVersion,
                String latestVersion,
                String releaseUrl,
                String downloadUrl,
                String downloadFileName,
                boolean hasUpdate
        ) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
            this.downloadUrl = downloadUrl;
            this.downloadFileName = downloadFileName;
            this.hasUpdate = hasUpdate;
        }
    }

    private static final class DownloadAsset {
        final String fileName;
        final String downloadUrl;
        final int priority;

        DownloadAsset(String fileName, String downloadUrl, int priority) {
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.priority = priority;
        }
    }
}

