package lv.lenc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RussianGlossaryInflector {
    private static final Pattern WORD_PATTERN = Pattern.compile("(?iu)\\p{L}[\\p{L}\\p{N}_'-]*");
    private static final Pattern CYRILLIC_PATTERN = Pattern.compile("(?iu).*\\p{IsCyrillic}.*");

    private enum RussianCase {
        NOMINATIVE,
        GENITIVE,
        DATIVE,
        ACCUSATIVE,
        INSTRUMENTAL,
        PREPOSITIONAL
    }

    private static final Set<String> RU_PREP_GENITIVE = Set.of(
            "\u0431\u0435\u0437", "\u0434\u043b\u044f", "\u0434\u043e", "\u0438\u0437", "\u0438\u0437\u043e",
            "\u043e\u0442", "\u043e\u0442\u043e", "\u0443", "\u043e\u043a\u043e\u043b\u043e",
            "\u043f\u043e\u0441\u043b\u0435", "\u0432\u043e\u043a\u0440\u0443\u0433"
    );
    private static final Set<String> RU_PREP_DATIVE = Set.of(
            "\u043a", "\u043a\u043e", "\u043f\u043e"
    );
    private static final Set<String> RU_PREP_INSTRUMENTAL = Set.of(
            "\u0441", "\u0441\u043e", "\u043d\u0430\u0434", "\u043f\u043e\u0434",
            "\u043f\u0435\u0440\u0435\u0434", "\u043c\u0435\u0436\u0434\u0443"
    );
    private static final Set<String> RU_PREP_PREPOSITIONAL = Set.of(
            "\u043e", "\u043e\u0431", "\u043e\u0431\u043e", "\u043f\u0440\u0438"
    );
    private static final Set<String> RU_FORCE_GENITIVE_HEADS = Set.of(
            "\u043a\u043e\u043a\u043e\u043d", "\u044f\u0439\u0446\u043e",
            "\u043b\u0438\u0447\u0438\u043d\u043a\u0430", "\u043e\u0442\u043c\u0435\u043d\u0430"
    );
    private static final Set<String> RU_STOP_WORDS = Set.of(
            "\u0438", "\u0438\u043b\u0438", "\u043d\u043e", "\u0430", "\u0432", "\u043d\u0430",
            "\u0441", "\u0441\u043e", "\u043f\u043e", "\u043d\u0435", "\u0434\u043b\u044f",
            "\u0434\u043e", "\u0438\u0437", "\u043e\u0442", "\u0443", "\u043e", "\u043e\u0431", "\u043f\u0440\u0438"
    );

    private RussianGlossaryInflector() {
    }

    static String normalizeWordForLookup(String lang, String word) {
        if (isBlank(word)) {
            return "";
        }

        String normLang = normalizeLangCode(lang);
        String lower = word.toLowerCase(Locale.ROOT);

        lower = removeCommonPossessiveSuffixes(normLang, lower);
        String singular = normalizePluralLikeForm(normLang, lower);
        if (!isBlank(singular)) {
            lower = singular;
        }

        if ("ruru".equals(normLang)) {
            String adj = normalizeRussianAdjectiveForLookup(lower);
            if (!adj.equals(lower)) {
                return adj;
            }
            String noun = normalizeRussianNounForLookup(lower);
            if (!noun.equals(lower)) {
                return noun;
            }
        }

        return lower;
    }

    static String normalizeRussianAdjectiveForLookup(String word) {
        if (isBlank(word) || word.length() < 4) {
            return word;
        }

        String[] suffixes = {
                "ыми", "ими", "ого", "его", "ему", "ому",
                "ая", "яя", "ое", "ее", "ые", "ие",
                "ой", "ей", "ую", "юю", "ых", "их",
                "ым", "им", "ом", "ем", "ый", "ий"
        };

        for (String suffix : suffixes) {
            if (word.length() > suffix.length() + 2 && word.endsWith(suffix)) {
                String stem = word.substring(0, word.length() - suffix.length());
                if (suffix.equals("яя") || suffix.equals("ее") || suffix.equals("ие")
                        || suffix.equals("его") || suffix.equals("ему")
                        || suffix.equals("ей") || suffix.equals("их")
                        || suffix.equals("ими") || suffix.equals("им")
                        || suffix.equals("ем") || suffix.equals("ий")) {
                    return stem + "ий";
                }
                return stem + "ый";
            }
        }

        return word;
    }

    static String normalizeRussianNounForLookup(String word) {
        if (isBlank(word) || word.length() < 4) {
            return word;
        }

        if (word.endsWith("ами") || word.endsWith("ями")) {
            return word.substring(0, word.length() - 3);
        }
        if (word.endsWith("ов") || word.endsWith("ев") || word.endsWith("ом") || word.endsWith("ем")
                || word.endsWith("ах") || word.endsWith("ях")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("а") || word.endsWith("я") || word.endsWith("у") || word.endsWith("ю")
                || word.endsWith("ы") || word.endsWith("и") || word.endsWith("е") || word.endsWith("о")) {
            return word.substring(0, word.length() - 1);
        }

        return word;
    }

    private static String removeCommonPossessiveSuffixes(String lang, String word) {
        if ("enus".equals(lang) && word.endsWith("'s") && word.length() > 3) {
            return word.substring(0, word.length() - 2);
        }
        return word;
    }

    // Lightweight stemming-like normalization for glossary lookup.
    private static String normalizePluralLikeForm(String lang, String word) {
        if (word.length() < 4) {
            return word;
        }

        switch (lang) {
            case "enus":
                if (word.endsWith("ies") && word.length() > 4) return word.substring(0, word.length() - 3) + "y";
                if (word.endsWith("es") && word.length() > 4) return word.substring(0, word.length() - 2);
                if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) return word.substring(0, word.length() - 1);
                return word;

            case "dede":
                if (word.endsWith("en") && word.length() > 4) return word.substring(0, word.length() - 2);
                if (word.endsWith("e") && word.length() > 4) return word.substring(0, word.length() - 1);
                if (word.endsWith("er") && word.length() > 4) return word.substring(0, word.length() - 2);
                if (word.endsWith("s") && word.length() > 4) return word.substring(0, word.length() - 1);
                return word;

            case "eses":
            case "esmx":
                if (word.endsWith("es") && word.length() > 4) return word.substring(0, word.length() - 2);
                if (word.endsWith("s") && word.length() > 3) return word.substring(0, word.length() - 1);
                return word;

            case "frfr":
                if (word.endsWith("aux") && word.length() > 5) return word.substring(0, word.length() - 3) + "al";
                if (word.endsWith("eux") && word.length() > 5) return word;
                if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) return word.substring(0, word.length() - 1);
                if (word.endsWith("x") && word.length() > 4) return word.substring(0, word.length() - 1);
                return word;

            case "itit":
                if (word.endsWith("i") && word.length() > 4) return word.substring(0, word.length() - 1) + "o";
                if (word.endsWith("e") && word.length() > 4) return word.substring(0, word.length() - 1) + "a";
                return word;

            case "ptbr":
                if (word.endsWith("\u00F5es") && word.length() > 5) return word.substring(0, word.length() - 3) + "\u00E3o";
                if (word.endsWith("ais") && word.length() > 5) return word.substring(0, word.length() - 3) + "al";
                if (word.endsWith("eis") && word.length() > 5) return word.substring(0, word.length() - 3) + "el";
                if (word.endsWith("is") && word.length() > 4) return word.substring(0, word.length() - 2) + "il";
                if (word.endsWith("s") && word.length() > 3) return word.substring(0, word.length() - 1);
                return word;

            case "plpl":
                if (word.endsWith("owie") && word.length() > 6) return word.substring(0, word.length() - 4);
                if (word.endsWith("ami") && word.length() > 5) return word.substring(0, word.length() - 3);
                if (word.endsWith("ach") && word.length() > 5) return word.substring(0, word.length() - 3);
                if (word.endsWith("y") && word.length() > 4) return word.substring(0, word.length() - 1);
                if (word.endsWith("i") && word.length() > 4) return word.substring(0, word.length() - 1);
                return word;

            case "kokr":
            case "zhcn":
            case "zhtw":
            case "ruru":
                return word;

            default:
                return word;
        }
    }

    private static String normalizeLangCode(String lang) {
        return lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
    }

    static String replaceGlossaryToken(String text,
                                       String token,
                                       String baseTerm,
                                       boolean useRussianInflection) {
        if (isBlank(text) || isBlank(token) || isBlank(baseTerm)) {
            return text;
        }
        if (!useRussianInflection || !containsCyrillic(baseTerm)) {
            return text.replace(token, baseTerm);
        }

        StringBuilder out = new StringBuilder(text.length() + 16);
        int pos = 0;
        while (true) {
            int idx = text.indexOf(token, pos);
            if (idx < 0) {
                out.append(text, pos, text.length());
                break;
            }

            out.append(text, pos, idx);
            RussianCase russianCase = detectRussianCaseBeforeToken(text, idx);
            String replacement = inflectRussianPhrase(baseTerm, russianCase);
            out.append(replacement);
            pos = idx + token.length();
        }
        return out.toString();
    }

    private static RussianCase detectRussianCaseBeforeToken(String text, int tokenIndex) {
        List<String> prevWords = collectPreviousWords(text, tokenIndex, 3);
        if (prevWords.isEmpty()) {
            return RussianCase.NOMINATIVE;
        }

        String prev1 = ruLower(prevWords.get(0));
        if (RU_PREP_GENITIVE.contains(prev1)) return RussianCase.GENITIVE;
        if (RU_PREP_DATIVE.contains(prev1)) return RussianCase.DATIVE;
        if (RU_PREP_INSTRUMENTAL.contains(prev1)) return RussianCase.INSTRUMENTAL;
        if (RU_PREP_PREPOSITIONAL.contains(prev1)) return RussianCase.PREPOSITIONAL;
        if (RU_FORCE_GENITIVE_HEADS.contains(prev1)) return RussianCase.GENITIVE;

        if (looksLikeRussianNounHead(prev1)) {
            return RussianCase.GENITIVE;
        }
        return RussianCase.NOMINATIVE;
    }

    private static List<String> collectPreviousWords(String text, int tokenIndex, int limit) {
        if (isBlank(text) || tokenIndex <= 0 || limit <= 0) {
            return Collections.emptyList();
        }

        List<String> words = new ArrayList<>(limit);
        int i = Math.min(tokenIndex - 1, text.length() - 1);

        while (i >= 0 && words.size() < limit) {
            while (i >= 0 && !Character.isLetter(text.charAt(i))) {
                i--;
            }
            if (i < 0) break;

            int end = i;
            while (i >= 0) {
                char ch = text.charAt(i);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '\'') {
                    i--;
                } else {
                    break;
                }
            }
            String word = text.substring(i + 1, end + 1);
            if (!isBlank(word) && !word.startsWith("__SC2_TERM_")) {
                words.add(word);
            }
        }
        return words;
    }

    private static boolean looksLikeRussianNounHead(String word) {
        if (isBlank(word)) {
            return false;
        }
        String w = ruLower(word);
        if (RU_STOP_WORDS.contains(w)) {
            return false;
        }
        if (!containsCyrillic(w)) {
            return false;
        }
        if (isLikelyRussianAdjective(w) || isLikelyRussianVerb(w)) {
            return false;
        }
        return w.length() >= 3;
    }

    private static boolean isLikelyRussianVerb(String lowerWord) {
        return lowerWord.endsWith("\u0442\u044c")
                || lowerWord.endsWith("\u0442\u044c\u0441\u044f")
                || lowerWord.endsWith("\u0435\u0442")
                || lowerWord.endsWith("\u044e\u0442")
                || lowerWord.endsWith("\u0438\u0442")
                || lowerWord.endsWith("\u0430\u0442")
                || lowerWord.endsWith("\u044f\u0442");
    }

    private static String inflectRussianPhrase(String phrase, RussianCase russianCase) {
        if (isBlank(phrase) || russianCase == RussianCase.NOMINATIVE) {
            return phrase;
        }

        Matcher matcher = WORD_PATTERN.matcher(phrase);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String word = matcher.group();
            String inflected = inflectRussianWord(word, russianCase);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(inflected));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String inflectRussianWord(String word, RussianCase russianCase) {
        if (isBlank(word) || !containsCyrillic(word) || russianCase == RussianCase.NOMINATIVE) {
            return word;
        }

        String lower = ruLower(word);
        String inflected = inflectRussianAdjective(lower, russianCase);
        if (inflected == null) {
            inflected = inflectRussianNoun(lower, russianCase);
        }
        if (inflected == null) {
            inflected = lower;
        }
        return applySourceCaseStyle(word, inflected);
    }

    private static String inflectRussianAdjective(String lower, RussianCase russianCase) {
        if (!isLikelyRussianAdjective(lower)) {
            return null;
        }
        return switch (russianCase) {
            case GENITIVE, ACCUSATIVE -> replaceAdjEnding(lower, "\u043e\u0433\u043e", "\u0435\u0433\u043e", "\u043e\u0433\u0430", "\u043e\u0439", "\u0435\u0439", "\u043e\u0433\u043e", "\u0435\u0433\u043e", "\u044b\u0445", "\u0438\u0445");
            case DATIVE -> replaceAdjEnding(lower, "\u043e\u043c\u0443", "\u0435\u043c\u0443", "\u043e\u043c\u0443", "\u043e\u0439", "\u0435\u0439", "\u043e\u043c\u0443", "\u0435\u043c\u0443", "\u044b\u043c", "\u0438\u043c");
            case INSTRUMENTAL -> replaceAdjEnding(lower, "\u044b\u043c", "\u0438\u043c", "\u044b\u043c", "\u043e\u0439", "\u0435\u0439", "\u044b\u043c", "\u0438\u043c", "\u044b\u043c\u0438", "\u0438\u043c\u0438");
            case PREPOSITIONAL -> replaceAdjEnding(lower, "\u043e\u043c", "\u0435\u043c", "\u043e\u043c", "\u043e\u0439", "\u0435\u0439", "\u043e\u043c", "\u0435\u043c", "\u044b\u0445", "\u0438\u0445");
            case NOMINATIVE -> lower;
        };
    }

    private static String replaceAdjEnding(String lower,
                                           String mascY,
                                           String mascI,
                                           String mascO,
                                           String femA,
                                           String femYa,
                                           String neuO,
                                           String neuE,
                                           String plY,
                                           String plI) {
        if (lower.endsWith("\u044b\u0439")) return lower.substring(0, lower.length() - 2) + mascY;
        if (lower.endsWith("\u0438\u0439")) return lower.substring(0, lower.length() - 2) + mascI;
        if (lower.endsWith("\u043e\u0439")) return lower.substring(0, lower.length() - 2) + mascO;
        if (lower.endsWith("\u0430\u044f")) return lower.substring(0, lower.length() - 2) + femA;
        if (lower.endsWith("\u044f\u044f")) return lower.substring(0, lower.length() - 2) + femYa;
        if (lower.endsWith("\u043e\u0435")) return lower.substring(0, lower.length() - 2) + neuO;
        if (lower.endsWith("\u0435\u0435")) return lower.substring(0, lower.length() - 2) + neuE;
        if (lower.endsWith("\u044b\u0435")) return lower.substring(0, lower.length() - 2) + plY;
        if (lower.endsWith("\u0438\u0435")) return lower.substring(0, lower.length() - 2) + plI;
        return lower;
    }

    private static boolean isLikelyRussianAdjective(String lower) {
        return lower.endsWith("\u044b\u0439")
                || lower.endsWith("\u0438\u0439")
                || lower.endsWith("\u043e\u0439")
                || lower.endsWith("\u0430\u044f")
                || lower.endsWith("\u044f\u044f")
                || lower.endsWith("\u043e\u0435")
                || lower.endsWith("\u0435\u0435")
                || lower.endsWith("\u044b\u0435")
                || lower.endsWith("\u0438\u0435");
    }

    private static String inflectRussianNoun(String lower, RussianCase russianCase) {
        if (russianCase == RussianCase.NOMINATIVE) {
            return lower;
        }
        if (lower.length() < 2) {
            return lower;
        }

        if (lower.endsWith("\u0430")) {
            String stem = lower.substring(0, lower.length() - 1);
            return switch (russianCase) {
                case GENITIVE -> stem + (needsIAfter(stem) ? "\u0438" : "\u044b");
                case DATIVE, PREPOSITIONAL -> stem + "\u0435";
                case ACCUSATIVE -> stem + "\u0443";
                case INSTRUMENTAL -> stem + "\u043e\u0439";
                case NOMINATIVE -> lower;
            };
        }
        if (lower.endsWith("\u044f")) {
            String stem = lower.substring(0, lower.length() - 1);
            return switch (russianCase) {
                case GENITIVE -> stem + "\u0438";
                case DATIVE, PREPOSITIONAL -> stem + "\u0435";
                case ACCUSATIVE -> stem + "\u044e";
                case INSTRUMENTAL -> stem + "\u0435\u0439";
                case NOMINATIVE -> lower;
            };
        }
        if (lower.endsWith("\u043e")) {
            String stem = lower.substring(0, lower.length() - 1);
            return switch (russianCase) {
                case GENITIVE -> stem + "\u0430";
                case DATIVE -> stem + "\u0443";
                case ACCUSATIVE -> lower;
                case INSTRUMENTAL -> stem + "\u043e\u043c";
                case PREPOSITIONAL -> stem + "\u0435";
                case NOMINATIVE -> lower;
            };
        }
        if (lower.endsWith("\u0435")) {
            String stem = lower.substring(0, lower.length() - 1);
            return switch (russianCase) {
                case GENITIVE -> stem + "\u044f";
                case DATIVE -> stem + "\u044e";
                case ACCUSATIVE -> lower;
                case INSTRUMENTAL -> stem + "\u0435\u043c";
                case PREPOSITIONAL -> stem + "\u0435";
                case NOMINATIVE -> lower;
            };
        }
        if (lower.endsWith("\u0439")) {
            String stem = lower.substring(0, lower.length() - 1);
            return switch (russianCase) {
                case GENITIVE, ACCUSATIVE -> stem + "\u044f";
                case DATIVE -> stem + "\u044e";
                case INSTRUMENTAL -> stem + "\u0435\u043c";
                case PREPOSITIONAL -> stem + "\u0435";
                case NOMINATIVE -> lower;
            };
        }
        if (lower.endsWith("\u044c")) {
            String stem = lower.substring(0, lower.length() - 1);
            return switch (russianCase) {
                case GENITIVE, ACCUSATIVE -> stem + "\u044f";
                case DATIVE -> stem + "\u044e";
                case INSTRUMENTAL -> stem + "\u0435\u043c";
                case PREPOSITIONAL -> stem + "\u0435";
                case NOMINATIVE -> lower;
            };
        }

        String stem = lower;
        return switch (russianCase) {
            case GENITIVE, ACCUSATIVE -> stem + "\u0430";
            case DATIVE -> stem + "\u0443";
            case INSTRUMENTAL -> stem + "\u043e\u043c";
            case PREPOSITIONAL -> stem + "\u0435";
            case NOMINATIVE -> lower;
        };
    }

    private static boolean needsIAfter(String stem) {
        if (isBlank(stem)) {
            return false;
        }
        char last = stem.charAt(stem.length() - 1);
        return "\u0433\u043a\u0445\u0436\u0447\u0448\u0449\u0446".indexOf(last) >= 0;
    }

    private static boolean containsCyrillic(String value) {
        return !isBlank(value) && CYRILLIC_PATTERN.matcher(value).matches();
    }

    private static String ruLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.forLanguageTag("ru"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String applySourceCaseStyle(String source, String translated) {
        if (isBlank(source) || isBlank(translated)) {
            return translated;
        }

        String plain = stripEdgePunctuation(source);
        if (isBlank(plain)) {
            return translated;
        }

        if (plain.equals(plain.toUpperCase(Locale.ROOT))) {
            return translated.toUpperCase(Locale.ROOT);
        }

        if (Character.isUpperCase(plain.codePointAt(0))) {
            if (translated.length() == 1) {
                return translated.toUpperCase(Locale.ROOT);
            }
            return translated.substring(0, 1).toUpperCase(Locale.ROOT) + translated.substring(1);
        }

        return translated;
    }

    private static String stripEdgePunctuation(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int start = 0;
        int end = value.length();
        while (start < end && !Character.isLetterOrDigit(value.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }
}
