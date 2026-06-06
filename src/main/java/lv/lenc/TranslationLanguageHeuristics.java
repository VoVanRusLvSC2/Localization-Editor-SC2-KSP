package lv.lenc;

import java.util.Locale;

final class TranslationLanguageHeuristics {
    private TranslationLanguageHeuristics() {
    }

    static String inferSourceUiForText(String preferredUi, String text) {
        String normalized = normalizeUi(preferredUi);
        if (normalized == null || normalized.isBlank()) {
            normalized = "enUS";
        }
        if (containsCyrillic(text)) {
            return "ruRU";
        }
        if (containsHangul(text)) {
            return "koKR";
        }
        if (containsCjk(text)) {
            return "zhTW".equalsIgnoreCase(normalized) ? "zhTW" : "zhCN";
        }
        return normalized;
    }

    static boolean shouldUseTargetTextAsSource(String targetUi, String targetText) {
        String normalizedTarget = normalizeUi(targetUi);
        if (targetText == null || targetText.isBlank()) {
            return false;
        }
        if ("enUS".equalsIgnoreCase(normalizedTarget)) {
            return containsCyrillic(targetText) || containsHangul(targetText) || containsCjk(targetText);
        }
        if ("ruRU".equalsIgnoreCase(normalizedTarget)) {
            return !containsCyrillic(targetText) && (containsLatin(targetText) || containsHangul(targetText) || containsCjk(targetText));
        }
        if ("koKR".equalsIgnoreCase(normalizedTarget)) {
            return !containsHangul(targetText) && (containsLatin(targetText) || containsCyrillic(targetText) || containsCjk(targetText));
        }
        if ("zhCN".equalsIgnoreCase(normalizedTarget) || "zhTW".equalsIgnoreCase(normalizedTarget)) {
            return !containsCjk(targetText) && (containsLatin(targetText) || containsCyrillic(targetText) || containsHangul(targetText));
        }
        return false;
    }

    private static String normalizeUi(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace("-", "").replace("_", "");
        if (s.length() < 4) return raw.trim();

        String lang = s.substring(0, 2).toLowerCase(Locale.ROOT);
        String region = s.substring(2).toUpperCase(Locale.ROOT);
        return lang + region;
    }

    private static boolean containsCyrillic(String value) {
        return containsScript(value, Character.UnicodeScript.CYRILLIC);
    }

    private static boolean containsHangul(String value) {
        return containsScript(value, Character.UnicodeScript.HANGUL);
    }

    private static boolean containsLatin(String value) {
        return containsScript(value, Character.UnicodeScript.LATIN);
    }

    private static boolean containsCjk(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean containsScript(String value, Character.UnicodeScript expected) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == expected) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
