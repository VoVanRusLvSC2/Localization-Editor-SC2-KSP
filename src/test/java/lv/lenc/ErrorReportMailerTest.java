package lv.lenc;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorReportMailerTest {
    @Test
    void mailtoUriEncodesRecipientSubjectAndBody() {
        URI uri = ErrorReportMailer.buildMailtoUri(
                "vlencmanissc@gmail.com",
                "SC2 error report",
                "line one\nline two"
        );

        assertEquals("mailto", uri.getScheme());
        String raw = uri.toString();
        assertTrue(raw.startsWith("mailto:vlencmanissc@gmail.com?"));
        assertTrue(raw.contains("subject=SC2%20error%20report"));
        assertTrue(raw.contains("body=line%20one%0Aline%20two"));
    }
}
