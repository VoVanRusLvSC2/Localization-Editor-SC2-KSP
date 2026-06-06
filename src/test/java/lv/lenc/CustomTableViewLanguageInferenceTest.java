package lv.lenc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomTableViewLanguageInferenceTest {
    @Test
    void cyrillicTextOverridesWrongDetectedSourceLanguage() {
        assertEquals("ruRU", TranslationLanguageHeuristics.inferSourceUiForText("enUS", "\u041d\u0438\u0434\u0443\u0441"));
        assertEquals("ruRU", TranslationLanguageHeuristics.inferSourceUiForText("enUS", "\u0412\u0440\u0430\u0436\u0435\u0441\u043a\u0430\u044f \u0433\u0440\u0443\u043f\u043f\u0430"));
    }

    @Test
    void englishTargetWithCyrillicTextIsTreatedAsUntranslated() {
        assertTrue(TranslationLanguageHeuristics.shouldUseTargetTextAsSource("enUS", "\u0421\u043a\u043e\u043b\u044c\u043a\u043e \u043d\u0438\u0434\u0443\u0441\u043e\u0432?"));
        assertFalse(TranslationLanguageHeuristics.shouldUseTargetTextAsSource("enUS", "Terazin Resource"));
        assertFalse(TranslationLanguageHeuristics.shouldUseTargetTextAsSource("enUS", "Player 2"));
    }
}
