package lv.lenc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlossaryServiceTest {
    @Test
    void csvGlossaryUsesExactKeyAndFreezesTermInsideTooltip() throws Exception {
        GlossaryService glossary = new GlossaryService();
        String csv = "key;enus;ruru\nButton/Name/Marine;Marine;Морпех\n";
        glossary.loadFromStream(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                GlossaryService.Category.BUTTON, "test.csv");

        assertEquals("Морпех", glossary.findExact("Button/Name/Marine", "enUS", "Marine", "ruRU"));
        GlossaryService.FrozenTerms frozen = glossary.freezeTerms(
                GlossaryService.Category.BUTTON, "enUS", "ruRU", "Train Marine now");
        assertTrue(frozen.preparedText().contains("__SC2_TERM_0__"));
        assertEquals("Train Морпех now", glossary.unfreezeTerms(frozen.preparedText(), frozen, "ruRU"));
    }

    @Test
    void phraseGlossaryFreezesWholeNameInsideSentence() throws Exception {
        GlossaryService glossary = new GlossaryService();
        glossary.loadTxtFromResource("/glossary/sc2_phrase_glossary_KSP.txt");
        GlossaryService.FrozenTerms frozen = glossary.freezeTerms(
                null, "enUS", "ruRU", "Build a Roach Warren now");
        assertTrue(frozen.preparedText().contains("__SC2_TERM_0__"), frozen.preparedText());
        assertEquals("Build a Рассадник тараканов now",
                glossary.unfreezeTerms(frozen.preparedText(), frozen, "ruRU"));
    }
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
