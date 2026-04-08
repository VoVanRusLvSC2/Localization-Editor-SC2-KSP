package lv.lenc;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class AppLauncher {
    public static void main(String[] args) {
        AppLog.init();
        AppLog.info("[APP] Starting application");
        AppLog.info("[APP] Log directory: " + AppLog.getLogDirectory());
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                AppLog.error("[APP] Uncaught exception in thread " + thread.getName(), throwable));

        // Prefer hardware acceleration, but keep software fallback on machines with unstable GPU drivers.
        System.setProperty("prism.order", "d3d,sw");
        System.setProperty("prism.verbose", "false");
        System.setProperty("sun.java2d.noddraw", "false");
        System.setProperty("javafx.animation.fullspeed", "true");
        if (isForceGpuRequested()) {
            System.setProperty("prism.forceGPU", "true");
            System.setProperty("sun.java2d.d3d", "true");
            System.setProperty("sun.java2d.opengl", "true");
            AppLog.info("[GPU] LE_FORCE_GPU=true, forcing hardware pipeline");
        }

        // On Windows, request "High Performance" GPU profile for java/javaw executables.
        // This writes per-user preference and does not require admin rights.
        configureWindowsHighPerformanceGpuPreference();

        Main.main(args);
    }

    private static boolean isForceGpuRequested() {
        String value = System.getenv("LE_FORCE_GPU");
        return value != null && Boolean.parseBoolean(value.trim());
    }

    private static void configureWindowsHighPerformanceGpuPreference() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return;
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return;
        }

        setGpuPreferenceIfExists(new File(javaHome, "bin\\java.exe"));
        setGpuPreferenceIfExists(new File(javaHome, "bin\\javaw.exe"));
    }

    private static void setGpuPreferenceIfExists(File exe) {
        if (exe == null || !exe.exists()) {
            return;
        }

        String path = exe.getAbsolutePath();
        try {
            Process p = new ProcessBuilder(
                    "reg", "add",
                    "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
                    "/v", path,
                    "/t", "REG_SZ",
                    "/d", "GpuPreference=2;",
                    "/f"
            )
                    .redirectErrorStream(true)
                    .start();

            int exit = p.waitFor();
            if (exit == 0) {
                AppLog.info("[GPU] High-performance GPU preference enabled for: " + path);
            } else {
                AppLog.warn("[GPU] Failed to set Windows GPU preference for: " + path + " (exit=" + exit + ")");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AppLog.warn("[GPU] Unable to configure Windows GPU preference: interrupted");
        } catch (IOException ex) {
            AppLog.warn("[GPU] Unable to configure Windows GPU preference: " + ex.getMessage());
        }
    }
}
