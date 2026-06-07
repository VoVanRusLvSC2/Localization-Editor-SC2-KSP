package lv.lenc;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorReportMailerTest {
    @Test
    void errorReportDoesNotOpenMailClientOrBrowserAutomatically() {
        assertFalse(ErrorReportMailer.opensExternalMailClientAutomatically());
    }

    @Test
    void supportMessageContainsManualContactDetails() {
        String message = ErrorReportMailer.supportMessage(Path.of("C:\\logs\\error-report.txt"));

        assertTrue(message.contains(ErrorReportMailer.REPORT_EMAIL));
        assertTrue(message.contains(ErrorReportMailer.SUPPORT_URL));
        assertTrue(message.contains("Outlook"));
        assertTrue(message.contains("браузер"));
    }
}
