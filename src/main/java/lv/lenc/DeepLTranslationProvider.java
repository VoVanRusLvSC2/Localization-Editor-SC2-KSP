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
    private static final String DEEPL_FREE_API_BASE = "https://api-free.deepl.com";
    private static final String DEEPL_PRO_API_BASE = "https://api.deepl.com";
    static final String DEEPL_FREE_TRANSLATE_ENDPOINT = DEEPL_FREE_API_BASE + "/v2/translate";
    static final String DEEPL_FREE_USAGE_ENDPOINT = DEEPL_FREE_API_BASE + "/v2/usage";
    static final String DEEPL_PRO_TRANSLATE_ENDPOINT = DEEPL_PRO_API_BASE + "/v2/translate";
    static final String DEEPL_PRO_USAGE_ENDPOINT = DEEPL_PRO_API_BASE + "/v2/usage";
    private static volatile String activeApiBase = "";

    private DeepLTranslationProvider() {
    }

    static boolean isConfigured() {
        return !resolveApiKey().isBlank();
    }

    static String checkAvailability(OkHttpClient http) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            return "DeepL API requires DEEPL_API_KEY or settings.properties deepl.api.key.";
        }

        String lastFailure = "";
        for (String apiBase : endpointOrder(apiKey)) {
            Request request = new Request.Builder()
                    .url(apiBase + "/v2/usage")
                    .get()
                    .header("Authorization", "DeepL-Auth-Key " + apiKey)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    activeApiBase = apiBase;
                    return "";
                }
                String body = response.body() == null ? "" : response.body().string();
                if (response.code() == 456) {
                    return endpointLabel(apiBase) + " quota exceeded (HTTP 456). Monthly character limit reached.";
                }
                if (response.code() == 429) {
                    return endpointLabel(apiBase) + " rate limit exceeded (HTTP 429). Try again later.";
                }
                lastFailure = endpointLabel(apiBase) + " is unavailable (HTTP " + response.code() + "): "
                        + shortenDiagnostic(body);
                if (response.code() == 403 || response.code() == 404) {
                    continue;
                }
                return lastFailure;
            } catch (IOException ex) {
                lastFailure = formatTransportFailure(endpointLabel(apiBase), "check", ex);
            }
        }
        return lastFailure.isBlank()
                ? "DeepL API check failed. Check DEEPL_API_KEY or settings.properties deepl.api.key."
                : lastFailure;
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

        IOException lastFailure = null;
        for (String apiBase : endpointOrder(apiKey)) {
            Request request = new Request.Builder()
                    .url(Objects.requireNonNull(HttpUrl.parse(apiBase + "/v2/translate")))
                    .post(body.build())
                    .header("Authorization", "DeepL-Auth-Key " + apiKey)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                String responseText = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    lastFailure = new IOException("[DeepL] " + endpointLabel(apiBase)
                            + " HTTP " + response.code() + ": " + shortenDiagnostic(responseText));
                    if (response.code() == 403 || response.code() == 404) {
                        continue;
                    }
                    throw lastFailure;
                }
                if (responseText.isBlank()) {
                    throw new IOException("[DeepL] Empty response");
                }

                List<String> out = parseResponse(responseText, uncachedInputs.size());
                if (out.size() != uncachedInputs.size()) {
                    throw new IOException("[DeepL] MISMATCH: in=" + uncachedInputs.size() + " out=" + out.size());
                }
                activeApiBase = apiBase;
                return out;
            } catch (IOException ex) {
                if (isProviderDiagnostic(ex)) {
                    throw ex;
                }
                lastFailure = new IOException("[DeepL] "
                        + formatTransportFailure(endpointLabel(apiBase), "request", ex), ex);
            }
        }
        throw lastFailure != null ? lastFailure : new IOException("[DeepL] translation request failed");
    }

    static String activeEndpointForLogs() {
        String apiBase = activeApiBase == null || activeApiBase.isBlank()
                ? preferredApiBase(resolveApiKey())
                : activeApiBase;
        return apiBase + "/v2/translate";
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
        return apiKey == null ? "" : apiKey.replace("\uFEFF", "").replaceAll("\\s+", "").trim();
    }

    private static List<String> endpointOrder(String apiKey) {
        String preferred = preferredApiBase(apiKey);
        String fallback = DEEPL_FREE_API_BASE.equals(preferred) ? DEEPL_PRO_API_BASE : DEEPL_FREE_API_BASE;
        List<String> out = new ArrayList<>(3);
        if (activeApiBase != null && !activeApiBase.isBlank()) {
            out.add(activeApiBase);
        }
        if (!out.contains(preferred)) {
            out.add(preferred);
        }
        if (!out.contains(fallback)) {
            out.add(fallback);
        }
        return out;
    }

    private static String preferredApiBase(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(":fx") ? DEEPL_FREE_API_BASE : DEEPL_PRO_API_BASE;
    }

    private static String endpointLabel(String apiBase) {
        if (DEEPL_FREE_API_BASE.equals(apiBase)) {
            return "DeepL API Free";
        }
        if (DEEPL_PRO_API_BASE.equals(apiBase)) {
            return "DeepL API Pro";
        }
        return "DeepL API";
    }

    private static String normalizeSourceLang(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return "";
        }
        return switch (normalized) {
            case "pt", "pt-br" -> "PT";
            case "zh" -> "ZH";
            case "en-us", "en-gb" -> "EN";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private static String normalizeTargetLang(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return "EN-US";
        }
        return switch (normalized) {
            case "en", "en-us" -> "EN-US";
            case "en-gb" -> "EN-GB";
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

    private static boolean isProviderDiagnostic(IOException ex) {
        String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage();
        return message.startsWith("[DeepL]");
    }

    private static String formatTransportFailure(String label, String action, IOException ex) {
        String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        boolean tlsLike = lower.contains("fatal alert")
                || lower.contains("handshake")
                || lower.contains("certificate")
                || lower.contains("pkix")
                || lower.contains("tls")
                || lower.contains("ssl");
        if (tlsLike) {
            return label + " " + action + " failed with TLS/SSL error: " + message
                    + ". Check Windows date/time, antivirus HTTPS inspection/proxy, Java 17 certificates, "
                    + "and that a DeepL Free key uses the api-free.deepl.com endpoint.";
        }
        return label + " " + action + " failed: " + message;
    }
}
