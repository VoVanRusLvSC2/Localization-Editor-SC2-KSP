package lv.lenc;

import java.awt.Desktop;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

final class ErrorReportMailer {
    static final String REPORT_EMAIL = "vlencmanissc@gmail.com";
    private static final int LOG_TAIL_LINES = 45;
    private static final int MAX_MAIL_BODY_CHARS = 6500;
    private static final AtomicBoolean REPORT_STARTED = new AtomicBoolean(false);

    private ErrorReportMailer() {
    }

    static void reportAsync(String context, Throwable throwable) {
        if (throwable == null || !REPORT_STARTED.compareAndSet(false, true) || isDisabled()) {
            return;
        }
        Thread thread = new Thread(() -> reportNow(context, throwable), "error-report-mailer");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isDisabled() {
        String env = System.getenv("LE_ERROR_REPORT");
        if (env != null && (env.equalsIgnoreCase("false") || env.equals("0"))) {
            return true;
        }
        return Boolean.getBoolean("le.error.report.disabled");
    }

    private static void reportNow(String context, Throwable throwable) {
        try {
            String report = buildReport(context, throwable);
            Path reportFile = writeReportFile(report);
            String body = report;
            if (reportFile != null) {
                body = "Full local report file: " + reportFile.toAbsolutePath() + "\n\n" + body;
            }
            body = trimForMailBody(body);

            URI mailto = buildMailtoUri(
                    REPORT_EMAIL,
                    "Localization Editor SC2 KSP error report",
                    body
            );
            openMailClient(mailto);
        } catch (Exception ex) {
            AppLog.warn("[ErrorReport] failed to prepare email report: " + ex.getMessage());
        }
    }

    static URI buildMailtoUri(String recipient, String subject, String body) {
        String safeRecipient = recipient == null ? "" : recipient.trim();
        String query = "subject=" + encode(subject) + "&body=" + encode(body);
        return URI.create("mailto:" + safeRecipient + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String buildReport(String context, Throwable throwable) {
        StringBuilder out = new StringBuilder();
        out.append("Localization Editor SC2 KSP error report\n");
        out.append("Version: ").append(UpdateChecker.currentVersion()).append('\n');
        out.append("Time: ").append(LocalDateTime.now()).append('\n');
        out.append("OS: ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version")).append('\n');
        out.append("Java: ").append(System.getProperty("java.version")).append('\n');
        out.append("Log directory: ").append(AppLog.getLogDirectory()).append('\n');
        out.append("Context: ").append(context == null || context.isBlank() ? "(none)" : context).append("\n\n");
        out.append("Stack trace:\n").append(stackTrace(throwable)).append('\n');

        List<String> tail = latestLogTail();
        if (!tail.isEmpty()) {
            out.append("\nLatest log tail:\n");
            for (String line : tail) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static Path writeReportFile(String report) {
        try {
            Path dir = Path.of(AppLog.getLogDirectory());
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("error-report-" + stamp + ".txt");
            Files.writeString(file, report, StandardCharsets.UTF_8);
            return file;
        } catch (Exception ex) {
            AppLog.warn("[ErrorReport] failed to write local report: " + ex.getMessage());
            return null;
        }
    }

    private static List<String> latestLogTail() {
        try {
            Path dir = Path.of(AppLog.getLogDirectory());
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            Path latest;
            try (Stream<Path> paths = Files.list(dir)) {
                latest = paths
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).matches("app-\\d+\\.log"))
                        .max(Comparator.comparing(ErrorReportMailer::lastModifiedSafe))
                        .orElse(null);
            }
            if (latest == null) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(latest, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - LOG_TAIL_LINES);
            return lines.subList(from, lines.size());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static Long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static String trimForMailBody(String body) {
        if (body == null || body.length() <= MAX_MAIL_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_MAIL_BODY_CHARS)
                + "\n\n[mail body trimmed; see local error-report file for full details]";
    }

    private static void openMailClient(URI mailto) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            Desktop.getDesktop().mail(mailto);
            return;
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(mailto);
        }
    }
}
