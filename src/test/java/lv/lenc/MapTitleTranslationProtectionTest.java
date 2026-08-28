package lv.lenc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapTitleTranslationProtectionTest {

    @Test
    void defaultsToProtectingPublishedMapTitles() {
        assertTrue(SettingsManager.DEFAULT_PRESERVE_MAP_TITLE);
    }

    @Test
    void runsPolicyActionOnlyWhenEnabled() {
        AtomicInteger invocations = new AtomicInteger();
        MapTitleTranslationProtection.runIfEnabled(false, invocations::incrementAndGet);
        assertEquals(0, invocations.get());

        MapTitleTranslationProtection.runIfEnabled(true, invocations::incrementAndGet);
        assertEquals(1, invocations.get());
    }
}
