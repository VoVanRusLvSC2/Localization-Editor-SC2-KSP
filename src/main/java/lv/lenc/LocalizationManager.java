package lv.lenc;

import java.util.Locale;
import java.util.ResourceBundle;

public class LocalizationManager {
    private ResourceBundle bundle;
    private String currentLanguageCode;

    public LocalizationManager(String languageCode) {
        this.currentLanguageCode = languageCode;
        Locale locale = new Locale(languageCode);
        bundle = ResourceBundle.getBundle("messages", locale); // "messages" is the base bundle name (no extension)
    }

    public String getCurrentLanguage() {
        return currentLanguageCode;
    }
    public String get(String key) {
        return bundle.getString(key);
    }

    public void changeLanguage(String languageCode) {
        this.currentLanguageCode = languageCode;
        Locale locale = new Locale(languageCode);
        bundle = ResourceBundle.getBundle("messages", locale);
    }


}
