package lv.lenc;

import javafx.stage.Screen;

public class UiScaleHelper {
    public static final double REAL_SCREEN_WIDTH;
    public static final double REAL_SCREEN_HEIGHT;
    public static final double SCREEN_WIDTH;
    public static final double SCREEN_HEIGHT;
    public static final double BASE_W = 1920.0;
    public static final double BASE_H = 1080.0;
    public static final double UNIFORM_SCALE;
    public static final double CONTENT_OFFSET_X;
    public static final double CONTENT_OFFSET_Y;

    static {
        REAL_SCREEN_WIDTH = Screen.getPrimary().getBounds().getWidth();
        REAL_SCREEN_HEIGHT = Screen.getPrimary().getBounds().getHeight();

        UNIFORM_SCALE = Math.min(REAL_SCREEN_WIDTH / BASE_W, REAL_SCREEN_HEIGHT / BASE_H);
        SCREEN_WIDTH = BASE_W * UNIFORM_SCALE;
        SCREEN_HEIGHT = BASE_H * UNIFORM_SCALE;

        CONTENT_OFFSET_X = (REAL_SCREEN_WIDTH - SCREEN_WIDTH) * 0.5;
        CONTENT_OFFSET_Y = (REAL_SCREEN_HEIGHT - SCREEN_HEIGHT) * 0.5;
    }

    public static double sx(double v, double w) { return v * (w / BASE_W); }
    public static double sy(double v, double h) { return v * (h / BASE_H); }

    // Keep Full-HD proportions on any aspect ratio (no X/Y stretching).
    public static double scaleX(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE;
    }

    public static double scaleY(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE;
    }

    public static double scale(double pxFullHD) {
        return pxFullHD * UNIFORM_SCALE;
    }

    public static double s(double v, double w, double h) {
        double k = Math.min(w / BASE_W, h / BASE_H);
        return v * k;
    }

    public static double SCREEN_WIDTH(int i) {
        return SCREEN_WIDTH;
    }
}
