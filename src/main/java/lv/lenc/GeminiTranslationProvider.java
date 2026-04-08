package lv.lenc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class GeminiTranslationProvider {
    static final String MODEL_ID = "gemini-2.5-flash-lite";
    static final String GEMINI_API_ENDPOINT = "https://aiplatform.googleapis.com/v1/publishers/google/models/"
            + MODEL_ID + ":generateContent";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private GeminiTranslationProvider() {
    }

    static boolean isConfigured() {
        if (resolveApiKey().isBlank()) {
            return false;
        }
        return true;
    }

    static String checkAvailability(OkHttpClient http) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            AppLog.info("[GEMINI] key source=missing");
            return "Gemini API requires GEMINI_API_KEY or settings.properties gemini.api.key.";
        }
        AppLog.info("[GEMINI] key source=" + resolveApiKeySource() + ", key=" + maskKey(apiKey));

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(GEMINI_API_ENDPOINT))
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        JsonObject payload = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userMessage = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", "ping");
        parts.add(textPart);
        userMessage.addProperty("role", "user");
        userMessage.add("parts", parts);
        contents.add(userMessage);
        payload.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.0);
        generationConfig.addProperty("maxOutputTokens", 1);
        payload.add("generationConfig", generationConfig);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .header("Accept", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return "";
            }
            String body = response.body() == null ? "" : response.body().string();
            if (response.code() == 429) {
                return "Gemini (Vertex AI) quota exceeded (HTTP 429). Check Vertex AI quotas and budget.";
            }
            if (response.code() == 403) {
                return "Gemini (Vertex AI) access denied (HTTP 403). Check API key restrictions and billing.";
            }
            if (response.code() == 401) {
                return "Gemini (Vertex AI) unauthorized (HTTP 401). Check that Gemini key is valid, saved, and allowed for Vertex AI API.";
            }
            return "Gemini (Vertex AI) is unavailable (HTTP " + response.code() + "): " + shortenDiagnostic(body);
        } catch (IOException ex) {
            return "Gemini (Vertex AI) check failed: " + ex.getMessage();
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
            throw new IOException("Gemini API key is not configured");
        }

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(GEMINI_API_ENDPOINT))
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        String prompt = buildTranslationPrompt(
                uncachedInputs,
                source,
                target,
                TranslationService.currentRuntimeGlossaryHints()
        );
        JsonObject payload = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userMessage = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);
        userMessage.addProperty("role", "user");
        userMessage.add("parts", parts);
        contents.add(userMessage);
        payload.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.0);
        generationConfig.addProperty("responseMimeType", "application/json");
        payload.add("generationConfig", generationConfig);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("[Gemini] HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
            }
            List<String> translated = parseGeminiTranslationArray(responseText, uncachedInputs.size());
            if (translated.size() != uncachedInputs.size()) {
                throw new IOException("[Gemini] MISMATCH: in=" + uncachedInputs.size() + " out=" + translated.size());
            }
            return translated;
        }
    }

    static List<String> inflectGlossaryPlaceholders(
            List<String> textsWithTokens,
            List<Map<String, String>> tokenMaps,
            String targetLang,
            OkHttpClient http
    ) throws IOException {
        if (textsWithTokens == null || textsWithTokens.isEmpty()) {
            return List.of();
        }
        if (tokenMaps == null || tokenMaps.size() != textsWithTokens.size()) {
            throw new IOException("[Gemini] invalid placeholder payload");
        }
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Gemini API key is not configured");
        }

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(GEMINI_API_ENDPOINT))
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        String prompt = buildInflectionPrompt(textsWithTokens, tokenMaps, targetLang);
        JsonObject payload = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userMessage = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);
        userMessage.addProperty("role", "user");
        userMessage.add("parts", parts);
        contents.add(userMessage);
        payload.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.0);
        generationConfig.addProperty("responseMimeType", "application/json");
        payload.add("generationConfig", generationConfig);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON))
                .header("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("[Gemini] inflection HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
            }
            return parseGeminiTranslationArray(responseText, textsWithTokens.size());
        }
    }

    static String activeEndpointForLogs() {
        return GEMINI_API_ENDPOINT;
    }

    private static List<String> parseGeminiTranslationArray(String responseText, int expectedSize) throws IOException {
        if (responseText == null || responseText.isBlank()) {
            throw new IOException("[Gemini] Empty response");
        }
        JsonElement root = JsonParser.parseString(responseText);
        JsonArray candidates = root.getAsJsonObject().getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("[Gemini] Missing candidates");
        }
        JsonObject first = candidates.get(0).getAsJsonObject();
        JsonObject content = first.getAsJsonObject("content");
        if (content == null) {
            throw new IOException("[Gemini] Missing content");
        }
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IOException("[Gemini] Missing parts");
        }
        String text = parts.get(0).getAsJsonObject().get("text").getAsString();
        String normalized = stripMarkdownJsonFence(text);
        JsonElement parsed = JsonParser.parseString(normalized);
        if (!parsed.isJsonArray()) {
            throw new IOException("[Gemini] Expected JSON array response");
        }

        List<String> out = new ArrayList<>();
        for (JsonElement item : parsed.getAsJsonArray()) {
            out.add(item == null || item.isJsonNull() ? "" : item.getAsString());
        }
        if (out.size() != expectedSize) {
            throw new IOException("[Gemini] Expected " + expectedSize + " items, got " + out.size());
        }
        return out;
    }

    private static String buildTranslationPrompt(List<String> uncachedInputs,
                                                 String source,
                                                 String target,
                                                 Map<String, String> glossaryHints) {
        JsonArray inputArray = new JsonArray();
        for (String text : uncachedInputs) {
            inputArray.add(text == null ? "" : text);
        }
        String sourceValue = (source == null || source.isBlank()) ? "auto" : source;
        Map<String, String> relevantGlossary = filterGlossaryHintsForInputs(uncachedInputs, glossaryHints, 24);
        String glossarySection = buildGlossaryHintSection(relevantGlossary);

        return "You are an expert StarCraft II localization translator.\n"
                + "Context: these strings are from SC2 UI (units, abilities, buttons, tooltips).\n"
                + "Translate each string from source language '" + sourceValue + "' to target language '" + target + "'.\n"
                + "Rules:\n"
                + "1) Preserve placeholders/tokens exactly.\n"
                + "2) Keep input order and number of items unchanged.\n"
                + "3) Return ONLY a JSON array of strings, no markdown, no comments.\n"
                + "4) Use provided SC2 glossary mappings as preferred terminology.\n"
                + "5) Adapt endings/case/gender only when grammar requires it.\n"
                + glossarySection
                + "Input JSON array:\n"
                + inputArray;
    }

    private static String buildInflectionPrompt(List<String> textsWithTokens,
                                                List<Map<String, String>> tokenMaps,
                                                String targetLang) {
        JsonArray items = new JsonArray();
        for (int i = 0; i < textsWithTokens.size(); i++) {
            JsonObject obj = new JsonObject();
            obj.addProperty("text", textsWithTokens.get(i) == null ? "" : textsWithTokens.get(i));
            JsonObject terms = new JsonObject();
            Map<String, String> map = tokenMaps.get(i);
            if (map != null) {
                for (Map.Entry<String, String> e : map.entrySet()) {
                    if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank()) {
                        continue;
                    }
                    terms.addProperty(e.getKey(), e.getValue());
                }
            }
            obj.add("terms", terms);
            items.add(obj);
        }
        String target = (targetLang == null || targetLang.isBlank()) ? "auto" : targetLang;
        return "You are a morphology post-processor for StarCraft II localization.\n"
                + "Task: for each input item, replace placeholders like __SC2_TERM_0__ using provided base terms.\n"
                + "Language of text: '" + target + "'.\n"
                + "Rules:\n"
                + "1) Replace placeholders only.\n"
                + "2) Keep all other text unchanged as much as possible.\n"
                + "3) You MAY inflect base terms (case/number/gender) to fit grammar.\n"
                + "4) If unsure, use base term unchanged.\n"
                + "5) Return ONLY JSON array of final strings in the same order.\n"
                + "Input JSON array:\n"
                + items;
    }

    private static String buildGlossaryHintSection(Map<String, String> glossaryHints) {
        if (glossaryHints == null || glossaryHints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("SC2 glossary for this batch:\n");
        int count = 0;
        for (Map.Entry<String, String> e : glossaryHints.entrySet()) {
            if (count >= 24) {
                break;
            }
            String sourceTerm = e.getKey();
            String targetTerm = e.getValue();
            if (sourceTerm == null || sourceTerm.isBlank() || targetTerm == null || targetTerm.isBlank()) {
                continue;
            }
            sb.append("- ").append(sourceTerm).append(" -> ").append(targetTerm).append("\n");
            count++;
        }
        if (count == 0) {
            return "";
        }
        return sb.toString();
    }

    private static Map<String, String> filterGlossaryHintsForInputs(List<String> inputs,
                                                                    Map<String, String> glossaryHints,
                                                                    int limit) {
        if (inputs == null || inputs.isEmpty() || glossaryHints == null || glossaryHints.isEmpty() || limit <= 0) {
            return Map.of();
        }

        List<String> normalizedInputs = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            normalizedInputs.add((input == null ? "" : input).toLowerCase(Locale.ROOT));
        }

        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : glossaryHints.entrySet()) {
            if (filtered.size() >= limit) {
                break;
            }
            String sourceTerm = entry.getKey();
            String targetTerm = entry.getValue();
            if (sourceTerm == null || sourceTerm.isBlank() || targetTerm == null || targetTerm.isBlank()) {
                continue;
            }
            String needle = sourceTerm.toLowerCase(Locale.ROOT);
            for (String input : normalizedInputs) {
                if (input.contains(needle)) {
                    filtered.putIfAbsent(sourceTerm, targetTerm);
                    break;
                }
            }
        }
        return filtered;
    }

    private static String stripMarkdownJsonFence(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        return trimmed.trim();
    }

    private static String resolveApiKey() {
        String apiKey = SettingsManager.loadGeminiApiKey();
        return apiKey == null ? "" : apiKey.trim();
    }

    private static String resolveApiKeySource() {
        String envValue = System.getenv("GEMINI_API_KEY");
        if (envValue != null && !envValue.trim().isEmpty()) {
            return "env:GEMINI_API_KEY";
        }
        return "settings:gemini.api.key";
    }

    private static String maskKey(String key) {
        if (key == null) return "empty";
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return "empty";
        if (trimmed.length() <= 8) return "***";
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
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
