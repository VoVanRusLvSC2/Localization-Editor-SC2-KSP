package lv.lenc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlossaryServiceTest {
    @Test
    void wordGlossaryTranslatesResearchToRussian() {
        GlossaryService glossary = new GlossaryService();
        glossary.loadTxtFromResource("/glossary/sc2_word_glossary_KSP.txt");

        assertEquals("Исследование", glossary.findWordMatch("enUS", "Research", "ruRU"));
    }

    @Test
    void phraseGlossaryTranslatesRoachWarrenAsSc2Building() {
        GlossaryService glossary = new GlossaryService();
        glossary.loadTxtFromResource("/glossary/sc2_phrase_glossary_KSP.txt");

        assertEquals("Roach Warren", glossary.findTxtMatch("ruRU", "Рассадник тараканов", "enUS"));
    }

    @Test
    void wordGlossaryFreezesResearchInsidePhrase() {
        GlossaryService glossary = new GlossaryService();
        glossary.loadTxtFromResource("/glossary/sc2_word_glossary_KSP.txt");

        GlossaryService.FrozenTerms frozen = glossary.freezeTerms(
                GlossaryService.Category.ABILITY,
                "enUS",
                "ruRU",
                "Akashic Record Research"
        );

        assertTrue(frozen.preparedText().contains("__SC2_TERM_0__"), frozen.preparedText());
        assertEquals("Akashic Record Исследование",
                glossary.unfreezeTerms(frozen.preparedText(), frozen, "ruRU"));
    }
}
