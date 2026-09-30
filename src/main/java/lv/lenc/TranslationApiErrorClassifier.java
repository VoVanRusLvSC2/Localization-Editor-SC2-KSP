package lv.lenc;

import java.util.Locale;

final class TranslationApiErrorClassifier {
    enum Issue { NONE, MISSING_CREDENTIALS, INVALID_CREDENTIALS, QUOTA, UNAVAILABLE }

    private TranslationApiErrorClassifier() {}

    static Issue classify(String errorMessage, String availabilityHint) {
        String text = ((errorMessage == null ? "" : errorMessage) + " "
                + (availabilityHint == null ? "" : availabilityHint)).toLowerCase(Locale.ROOT);
        if (text.contains("not configured") || text.contains("credentials are missing")
                || text.contains("api key is missing") || text.contains("api token is missing")
                || (text.contains("requires ") && (text.contains("api_key")
                || text.contains("api key") || text.contains("api_token")
                || text.contains("api token")))) {
            return Issue.MISSING_CREDENTIALS;
        }
        if (text.contains("http 401") || text.contains("http 403")
                || text.contains("invalid key") || text.contains("invalid token")
                || text.contains("unauthorized") || text.contains("authentication failed")) {
            return Issue.INVALID_CREDENTIALS;
        }
        if (text.contains("http 429") || text.contains("quota exceeded")
                || text.contains("rate limit") || text.contains("too many requests")) {
            return Issue.QUOTA;
        }
        if (text.contains("http 5") || text.contains("unreachable")
                || text.contains("connection refused") || text.contains("timed out")
                || text.contains("availability check failed")) {
            return Issue.UNAVAILABLE;
        }
        return Issue.NONE;
    }
}
