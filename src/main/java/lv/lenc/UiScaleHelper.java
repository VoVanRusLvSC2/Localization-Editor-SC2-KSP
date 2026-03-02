package lv.lenc;

import javafx.stage.Screen;

public class UiScaleHelper {
    public static final double SCREEN_WIDTH;
    public static final double SCREEN_HEIGHT;
    public static final double BASE_W = 1920.0;
    public static final double BASE_H = 1080.0;

    public static double sx(double v, double w) { return v * (w / BASE_W); }
    public static double sy(double v, double h) { return v * (h / BASE_H); }
    static {
        SCREEN_WIDTH = Screen.getPrimary().getBounds().getWidth();
        SCREEN_HEIGHT = Screen.getPrimary().getBounds().getHeight();
    }

    // scales by width (relative to Full HD)
    public static double scaleX(double pxFullHD) {
        return SCREEN_WIDTH * pxFullHD / 1920.0;
    }
    // scales by height
    public static double scaleY(double pxFullHD) {
        return SCREEN_HEIGHT * pxFullHD / 1080.0;
    }
    public static double scale(double pxFullHD) {
        double scaleX = Math.round(SCREEN_WIDTH / 1920.0);
        double scaleY = Math.round(SCREEN_HEIGHT / 1080.0);
        return pxFullHD * Math.round(Math.sqrt((scaleX * scaleX + scaleY * scaleY) / 2));
    }
    public static double s(double v, double w, double h) {
        double k = Math.min(w / BASE_W, h / BASE_H);
        return v * k;
    }

    public static double SCREEN_WIDTH(int i) {
        return 0;
    }
}
