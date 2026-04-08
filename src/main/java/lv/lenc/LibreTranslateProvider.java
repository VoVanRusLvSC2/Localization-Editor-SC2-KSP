package lv.lenc;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import retrofit2.Call;

final class LibreTranslateProvider {
    @FunctionalInterface
    interface JsonCallExecutor {
        retrofit2.Response<JsonElement> execute(Call<JsonElement> call) throws IOException;
    }

    private LibreTranslateProvider() {
    }

    static List<String> translatePreparedTexts(
            List<String> uncachedInputs,
            String source,
            String target,
            LibreTranslateApi effectiveApi,
            JsonCallExecutor executor
    ) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("q", uncachedInputs);
        body.put("source", source);
        body.put("target", target);
        body.put("format", "text");

        retrofit2.Response<JsonElement> resp = executor.execute(effectiveApi.translateAny(body));
        int code = (resp != null ? resp.code() : -1);
        if (resp == null || !resp.isSuccessful() || resp.body() == null) {
            String err = safeErrorBody(resp);
            throw new IOException("[LT] HTTP " + code + ": " + err);
        }

        return TranslationService.extractTranslations(resp.body(), uncachedInputs.size());
    }

    private static String safeErrorBody(retrofit2.Response<?> resp) {
        if (resp == null || resp.errorBody() == null) return "(no error body)";
        try {
            String text = resp.errorBody().string();
            return (text == null || text.isBlank()) ? "(empty error body)" : text;
        } catch (Exception ex) {
            return "(errorBody read failed: " + ex.getMessage() + ")";
        }
    }
}

