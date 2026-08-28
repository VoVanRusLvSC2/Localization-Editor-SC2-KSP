package lv.lenc;

import java.io.File;

/** Applies the optional publication-safe map-title policy around file I/O. */
final class MapTitleTranslationProtection {
    private MapTitleTranslationProtection() {
    }

    static void rememberOpenedNames(
            File sourceInput,
            String archiveRelativePath,
            CustomTableView tableView,
            String sourceUi
    ) {
        runIfEnabled(() -> MapPublicationNameStore.rememberOpenedNames(
                sourceInput, archiveRelativePath, tableView, sourceUi));
    }

    static void protectBeforeSave(
            File sourceInput,
            String archiveRelativePath,
            CustomTableView tableView,
            String sourceUi
    ) {
        runIfEnabled(() -> MapPublicationNameStore.protectBeforeSave(
                sourceInput, archiveRelativePath, tableView, sourceUi));
    }

    static void runIfEnabled(Runnable action) {
        runIfEnabled(SettingsManager.loadPreserveMapTitle(), action);
    }

    static void runIfEnabled(boolean enabled, Runnable action) {
        if (enabled) {
            action.run();
        }
    }
}
