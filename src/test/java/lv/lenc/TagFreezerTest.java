package lv.lenc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TagFreezerTest {

    @Test
    void freezeRich_preservesBracketTagAttributesButAllowsInnerTextTranslation() {
        String original = "[d ref='Kicker_Refinery' color='00FFFF']Refinery bonus[/d]";

        TagFreezer.Frozen frozen = TagFreezer.freezeRich(original);
        String translatedVisible = frozen.protectedText.replace("Refinery bonus", "Бонус переработки");
        String restored = TagFreezer.unfreezeRich(translatedVisible, frozen);

        assertEquals("[d ref='Kicker_Refinery' color='00FFFF']Бонус переработки[/d]", restored);
    }

    @Test
    void freezeRich_preservesNestedBracketTagsAndOnlyChangesReadableText() {
        String original = "[d ref='Commander' color='00FFFF'][s val='Hotkey']Press ability[/s][/d]";

        TagFreezer.Frozen frozen = TagFreezer.freezeRich(original);
        String translatedVisible = frozen.protectedText.replace("Press ability", "Нажмите способность");
        String restored = TagFreezer.unfreezeRich(translatedVisible, frozen);

        assertEquals("[d ref='Commander' color='00FFFF'][s val='Hotkey']Нажмите способность[/s][/d]", restored);
    }

    @Test
    void containsRichMarkup_detectsSquareTagsWithQuotedAttributes() {
        assertTrue(KeyFilterWindow.containsRichMarkup(
                "[d time='00:10' ref='CommanderAlpha' color='00FFFF']Ready[/d]"
        ));
    }

    @Test
    void containsRichMarkup_detectsAngleTagsWithQuotedAttributes() {
        assertTrue(KeyFilterWindow.containsRichMarkup(
                "<IMG path=\"Assets\\\\Textures\\\\icon-mineral-protoss.dds\" height=\"30\" width=\"30\"/>"
        ));
    }

    @Test
    void containsRichMarkup_ignoresPlainSquareBracketText() {
        assertFalse(KeyFilterWindow.containsRichMarkup("Use [1] to continue"));
    }

    @Test
    void containsRichMarkup_ignoresEscapedRegexLikeBracketTags() {
        assertFalse(KeyFilterWindow.containsRichMarkup(
                "let ref = expressionReference.replace(/\\[d\\s+(?:time|ref)\\s*=\\s*'(.+?)'\\]/gi, handler)"
        ));
    }
}
