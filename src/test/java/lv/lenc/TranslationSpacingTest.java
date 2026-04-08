package lv.lenc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TranslationSpacingTest {

    @Test
    void unfreezeTerms_restoresMissingSpacesWhenTheyExistedInSource() {
        GlossaryService glossaryService = new GlossaryService();
        Map<String, String> tokenToTarget = new LinkedHashMap<>();
        tokenToTarget.put("__SC2_TERM_0__", "Нет");
        tokenToTarget.put("__SC2_TERM_1__", "Нод");
        tokenToTarget.put("__SC2_TERM_2__", "Коммандос");

        GlossaryService.FrozenTerms frozen = new GlossaryService.FrozenTerms(
                "__SC2_TERM_0__ __SC2_TERM_1__ __SC2_TERM_2__",
                tokenToTarget
        );

        String restored = glossaryService.unfreezeTerms(
                "__SC2_TERM_0____SC2_TERM_1__ __SC2_TERM_2__",
                frozen
        );

        assertEquals("Нет Нод Коммандос", restored);
    }

    @Test
    void unfreezeTerms_doesNotInsertSpacesWhenSourceHadNone() {
        GlossaryService glossaryService = new GlossaryService();
        Map<String, String> tokenToTarget = new LinkedHashMap<>();
        tokenToTarget.put("__SC2_TERM_0__", "Нет");
        tokenToTarget.put("__SC2_TERM_1__", "Нод");
        tokenToTarget.put("__SC2_TERM_2__", "Коммандос");

        GlossaryService.FrozenTerms frozen = new GlossaryService.FrozenTerms(
                "__SC2_TERM_0____SC2_TERM_1____SC2_TERM_2__",
                tokenToTarget
        );

        String restored = glossaryService.unfreezeTerms(
                "__SC2_TERM_0____SC2_TERM_1____SC2_TERM_2__",
                frozen
        );

        assertEquals("НетНодКоммандос", restored);
    }

    @Test
    void normalizePunctuationSpacing_keepsMissingSpaceAfterCommaIfSourceHasNone() {
        assertEquals("Damage,Amount", TranslationService.normalizePunctuationSpacing("Damage,Amount"));
        assertEquals("Damage,Amount", TranslationService.sanitizeVisible("Damage,Amount"));
    }
}
