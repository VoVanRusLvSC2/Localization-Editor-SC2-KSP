package lv.lenc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationApiErrorClassifierTest {
    @Test
    void distinguishesMissingInvalidAndExhaustedApiAccess() {
        assertEquals(TranslationApiErrorClassifier.Issue.MISSING_CREDENTIALS,
                TranslationApiErrorClassifier.classify("Cloudflare is not available",
                        "Cloudflare requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN"));
        assertEquals(TranslationApiErrorClassifier.Issue.INVALID_CREDENTIALS,
                TranslationApiErrorClassifier.classify("[Cloudflare] HTTP 401", null));
        assertEquals(TranslationApiErrorClassifier.Issue.QUOTA,
                TranslationApiErrorClassifier.classify("HTTP 429: rate limit", null));
        assertEquals(TranslationApiErrorClassifier.Issue.UNAVAILABLE,
                TranslationApiErrorClassifier.classify("Connection timed out", null));
        assertEquals(TranslationApiErrorClassifier.Issue.NONE,
                TranslationApiErrorClassifier.classify("Invalid source language", null));
    }
}
