package lv.lenc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class DeepLTranslationProvider {
    static final String DEEPL_FREE_TRANSLATE_ENDPOINT = "https://api-free.deepl.com/v2/translate";
    static final String DEEPL_FREE_USAGE_ENDPOINT = "https://api-free.deepl.com/v2/usage";

    private DeepLTranslationProvider() {
    }

    static boolean isConfigured() {
        return !resolveApiKey().isBlank();
    }

    static String checkAvailability(OkHttpClient http) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            return "DeepL API Free requires DEEPL_API_KEY or settings.properties deepl.api.key.";
        }

        Request request = new Request.Builder()
                .url(DEEPL_FREE_USAGE_ENDPOINT)
                .get()
                .header("Authorization", "DeepL-Auth-Key " + apiKey)
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return "";
            }
            String body = response.body() == null ? "" : response.body().string();
            if (response.code() == 456) {
                return "DeepL API Free quota exceeded (HTTP 456). Monthly character limit reached.";
            }
            if (response.code() == 403) {
                return "DeepL API Free access denied (HTTP 403). Check DEEPL_API_KEY.";
            }
            if (response.code() == 429) {
                return "DeepL API Free rate limit exceeded (HTTP 429). Try again later.";
            }
            return "DeepL API Free is unavailable (HTTP " + response.code() + "): " + shortenDiagnostic(body);
        } catch (IOException ex) {
            return "DeepL API Free check failed: " + ex.getMessage();
        }
    }

    static List<String> translatePreparedTexts(
            List<String> uncachedInputs,
            String source,
            String target,
            OkHttpClient http
    ) throws IOException {
        if (uncachedInputs == null || uncachedInputs.isEmpty()) {
            return List.of();
        }

        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("DeepL API key is not configured");
        }

        FormBody.Builder body = new FormBody.Builder(StandardCharsets.UTF_8)
                .add("target_lang", normalizeTargetLang(target));
        String sourceLang = normalizeSourceLang(source);
        if (!sourceLang.isBlank()) {
            body.add("source_lang", sourceLang);
        }
        for (String text : uncachedInputs) {
            body.add("text", text == null ? "" : text);
        }

        Request request = new Request.Builder()
                .url(Objects.requireNonNull(HttpUrl.parse(DEEPL_FREE_TRANSLATE_ENDPOINT)))
                .post(body.build())
                .header("Authorization", "DeepL-Auth-Key " + apiKey)
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("[DeepL] HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
            }
            if (responseText.isBlank()) {
                throw new IOException("[DeepL] Empty response");
            }

            List<String> out = parseResponse(responseText, uncachedInputs.size());
            if (out.size() != uncachedInputs.size()) {
                throw new IOException("[DeepL] MISMATCH: in=" + uncachedInputs.size() + " out=" + out.size());
            }
            return out;
        }
    }

    static String activeEndpointForLogs() {
        return DEEPL_FREE_TRANSLATE_ENDPOINT;
    }

    private static List<String> parseResponse(String responseText, int expectedSize) throws IOException {
        JsonElement root = JsonParser.parseString(responseText);
        JsonArray translations = root.getAsJsonObject().getAsJsonArray("translations");
        if (translations == null) {
            throw new IOException("[DeepL] Missing translations");
        }
        List<String> out = new ArrayList<>(translations.size());
        for (JsonElement item : translations) {
            JsonObject obj = item.getAsJsonObject();
            JsonElement textEl = obj.get("text");
            out.add(textEl == null || textEl.isJsonNull() ? "" : textEl.getAsString());
        }
        if (out.size() != expectedSize) {
            throw new IOException("[DeepL] Expected " + expectedSize + " items, got " + out.size());
        }
        return out;
    }

    private static String resolveApiKey() {
        String apiKey = SettingsManager.loadDeepLApiKey();
        return apiKey == null ? "" : apiKey.trim();
    }

    private static String normalizeSourceLang(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return "";
        }
        return switch (normalized) {
            case "pt" -> "PT-BR";
            case "zh" -> "ZH";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private static String normalizeTargetLang(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return "EN";
        }
        return switch (normalized) {
            case "pt" -> "PT-BR";
            case "zh" -> "ZH";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private static String shortenDiagnostic(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        final int maxLen = 320;
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen - 3) + "...";
    }
}
