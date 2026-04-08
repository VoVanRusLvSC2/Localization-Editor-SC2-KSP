package lv.lenc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jsoup.parser.Parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class GoogleCloudTranslationProvider {
    private static final String GOOGLE_TRANSLATE_BASE_URL = "https://translation.googleapis.com/language/translate/v2";

    private GoogleCloudTranslationProvider() {
    }

    static boolean isConfigured() {
        return !resolveApiKey().isBlank();
    }

    static List<String> translatePreparedTexts(
            List<String> uncachedInputs,
            String source,
            String target,
            OkHttpClient http
    ) throws IOException {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Google Cloud Translate is not configured");
        }

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(GOOGLE_TRANSLATE_BASE_URL))
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        FormBody.Builder body = new FormBody.Builder(StandardCharsets.UTF_8)
                .add("target", target)
                .add("format", "text")
                .add("model", "nmt");
        if (source != null && !source.isBlank() && !"auto".equalsIgnoreCase(source)) {
            body.add("source", source);
        }
        for (String text : uncachedInputs) {
            body.add("q", text == null ? "" : text);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(body.build())
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("[Google] HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
            }
            if (responseText.isBlank()) {
                throw new IOException("[Google] Empty response");
            }

            JsonElement parsed = JsonParser.parseString(responseText);
            List<String> translated = TranslationService.extractTranslations(parsed, uncachedInputs.size());
            if (translated.size() != uncachedInputs.size()) {
                throw new IllegalStateException(
                        "[Google] MISMATCH: in=" + uncachedInputs.size() + " out=" + translated.size());
            }

            List<String> normalized = new ArrayList<>(translated.size());
            for (String value : translated) {
                normalized.add(Parser.unescapeEntities(value == null ? "" : value, true));
            }
            return normalized;
        }
    }

    private static String resolveApiKey() {
        String apiKey = SettingsManager.loadGoogleTranslateApiKey();
        return apiKey == null ? "" : apiKey.trim();
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

