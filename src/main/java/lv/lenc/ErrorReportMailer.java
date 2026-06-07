package lv.lenc;

import java.io.PrintWriter;
import java.io.StringWriter;
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

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

final class ErrorReportMailer {
    static final String REPORT_EMAIL = "vlencmanissc@gmail.com";
    static final String SUPPORT_URL = "https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/issues";
    private static final int LOG_TAIL_LINES = 45;
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
            if (reportFile != null) {
                AppLog.warn("[ErrorReport] local report created: " + reportFile.toAbsolutePath()
                        + ". Send this file manually to " + REPORT_EMAIL + " if support is needed.");
                showSupportDialog(reportFile);
            }
        } catch (Exception ex) {
            AppLog.warn("[ErrorReport] failed to write local error report: " + ex.getMessage());
        }
    }

    static boolean opensExternalMailClientAutomatically() {
        return false;
    }

    static String supportMessage(Path reportFile) {
        String path = reportFile == null ? "(report file was not created)" : reportFile.toAbsolutePath().toString();
        return "Если у вас есть баги, напишите на почту:\n"
                + REPORT_EMAIL + "\n\n"
                + "GitHub issues:\n"
                + SUPPORT_URL + "\n\n"
                + "Файл отчёта:\n"
                + path + "\n\n"
                + "Отправьте этот файл вместе с описанием проблемы. Outlook и браузер автоматически не запускаются.";
    }

    private static void showSupportDialog(Path reportFile) {
        try {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Ошибка приложения");
                alert.setHeaderText("Создан локальный отчёт об ошибке");

                TextArea text = new TextArea(supportMessage(reportFile));
                text.setEditable(false);
                text.setWrapText(true);
                text.setPrefColumnCount(60);
                text.setPrefRowCount(11);
                alert.getDialogPane().setContent(text);
                alert.setOnShown(event -> AppStyles.applyAlertStyles(alert.getDialogPane().getScene()));
                alert.show();
            });
        } catch (IllegalStateException ex) {
            AppLog.warn("[ErrorReport] UI is not ready for support dialog: " + ex.getMessage());
        } catch (Exception ex) {
            AppLog.warn("[ErrorReport] failed to show support dialog: " + ex.getMessage());
        }
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

}
