package lv.lenc;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class AppLog {
    private static final Logger LOG = Logger.getLogger(AppLog.class.getName());

    private AppLog() {
    }

    public static void info(String message) {
        LOG.info(message);
    }

    public static void warn(String message) {
        LOG.warning(message);
    }

    public static void error(String message) {
        LOG.severe(message);
    }

    public static void exception(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        LOG.log(Level.SEVERE, throwable.getMessage(), throwable);
    }

    public static void error(String message, Throwable throwable) {
        LOG.log(Level.SEVERE, message, throwable);
    }
}
