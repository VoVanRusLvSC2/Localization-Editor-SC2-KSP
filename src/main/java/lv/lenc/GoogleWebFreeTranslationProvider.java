package lv.lenc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class GoogleWebFreeTranslationProvider {
    static final String GOOGLE_WEB_FREE_ENDPOINT = "https://translate.googleapis.com/translate_a/single";

    private GoogleWebFreeTranslationProvider() {
    }

    static boolean isConfigured() {
        return true;
    }

    static String checkAvailability(OkHttpClient http) {
        try {
            String probe = requestSingle("ping", "en", "en", http);
            return probe == null || probe.isBlank()
                    ? "Google Translate Free (Web) returned an empty response."
                    : "";
        } catch (IOException ex) {
            return "Google Translate Free (Web) is unavailable: " + ex.getMessage();
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

        List<String> normalizedInputs = new ArrayList<>(uncachedInputs.size());
        for (String text : uncachedInputs) {
            normalizedInputs.add(text == null ? "" : text);
        }

        String splitToken = "<<<LENC_SPLIT_" + UUID.randomUUID().toString().replace("-", "") + ">>>";
        String delimiter = "\n" + splitToken + "\n";
        String joined = String.join(delimiter, normalizedInputs);

        String joinedTranslated = requestSingle(joined, source, target, http);
        List<String> split = splitJoinedTranslation(joinedTranslated, splitToken, normalizedInputs.size());
        if (split.size() == normalizedInputs.size()) {
            return split;
        }

        // Fallback path: translate one-by-one if delimiter strategy was altered by upstream.
        List<String> out = new ArrayList<>(normalizedInputs.size());
        for (String input : normalizedInputs) {
            out.add(requestSingle(input, source, target, http));
        }
        return out;
    }

    static String activeEndpointForLogs() {
        return GOOGLE_WEB_FREE_ENDPOINT;
    }

    private static String requestSingle(String text, String source, String target, OkHttpClient http) throws IOException {
        FormBody body = new FormBody.Builder(StandardCharsets.UTF_8)
                .add("client", "gtx")
                .add("sl", normalizeSourceLang(source))
                .add("tl", normalizeTargetLang(target))
                .add("dt", "t")
                .add("dj", "1")
                .add("q", text == null ? "" : text)
                .build();

        Request request = new Request.Builder()
                .url(GOOGLE_WEB_FREE_ENDPOINT)
                .post(body)
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("[Google Web Free] HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
            }
            if (responseText.isBlank()) {
                throw new IOException("[Google Web Free] Empty response");
            }
            return parseSingleResponse(responseText);
        }
    }

    private static String parseSingleResponse(String responseText) throws IOException {
        JsonElement parsed = JsonParser.parseString(responseText);
        if (!parsed.isJsonObject()) {
            throw new IOException("[Google Web Free] Unexpected response format");
        }
        JsonObject obj = parsed.getAsJsonObject();
        JsonArray sentences = obj.getAsJsonArray("sentences");
        if (sentences == null) {
            throw new IOException("[Google Web Free] Missing sentences");
        }

        StringBuilder sb = new StringBuilder();
        for (JsonElement sentence : sentences) {
            if (sentence == null || !sentence.isJsonObject()) {
                continue;
            }
            JsonObject sentenceObj = sentence.getAsJsonObject();
            JsonElement transEl = sentenceObj.get("trans");
            if (transEl != null && !transEl.isJsonNull()) {
                sb.append(transEl.getAsString());
            }
        }
        return sb.toString();
    }

    private static List<String> splitJoinedTranslation(String joinedTranslation, String splitToken, int expectedSize) {
        if (joinedTranslation == null) {
            return List.of();
        }

        String exactDelimiter = "\n" + splitToken + "\n";
        String[] exactParts = joinedTranslation.split(Pattern.quote(exactDelimiter), -1);
        if (exactParts.length == expectedSize) {
            return Arrays.asList(exactParts);
        }

        String[] fuzzyParts = joinedTranslation.split("\\s*" + Pattern.quote(splitToken) + "\\s*", -1);
        if (fuzzyParts.length == expectedSize) {
            return Arrays.asList(fuzzyParts);
        }

        return List.of();
    }

    private static String normalizeSourceLang(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return "auto";
        }
        return normalized;
    }

    private static String normalizeTargetLang(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return "en";
        }
        return normalized;
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
