package com.example.albumsnap.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Preferencias da app (tema e idioma) em SharedPreferences, e a sua aplicacao. */
public class SettingsManager {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_PT = "pt";

    private static final String PREFS = "settings";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LANGUAGE = "language";

    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static SettingsManager from(Context context) {
        return new SettingsManager(context);
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    /** Aplica o tema guardado (recria os ecras abertos se mudou). */
    public void applyTheme() {
        String theme = getTheme();
        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (THEME_LIGHT.equals(theme)) mode = AppCompatDelegate.MODE_NIGHT_NO;
        if (THEME_DARK.equals(theme)) mode = AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Aplica o idioma guardado (recria os ecras abertos se mudou). */
    public void applyLanguage() {
        String language = getLanguage();
        LocaleListCompat locales = LANGUAGE_SYSTEM.equals(language)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(language);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public void applyAll() {
        applyTheme();
        applyLanguage();
    }
}
