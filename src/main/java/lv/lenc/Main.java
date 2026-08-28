package lv.lenc;

import javafx.application.Application;

/**
 * Stable, minimal application entry point. UI and translation logic live in
 * {@link LocalizationEditorApplication}.
 */
public final class Main extends LocalizationEditorApplication {
    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
