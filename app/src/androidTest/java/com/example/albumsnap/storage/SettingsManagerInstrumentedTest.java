package com.example.albumsnap.storage;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.testutil.PrefsBackup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tema e idioma guardados e aplicados. Mexe nas definicoes reais da app, por isso guarda o que la
 * estava e repoe-o no fim.
 */
@RunWith(AndroidJUnit4.class)
public class SettingsManagerInstrumentedTest {

    private Context context;
    private PrefsBackup backup;
    private int originalNightMode;
    private String originalLanguageTags;

    private void onMainThread(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        backup = PrefsBackup.of(context, "settings");
        originalNightMode = AppCompatDelegate.getDefaultNightMode();
        originalLanguageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        backup.clear();
    }

    @After
    public void tearDown() {
        backup.restore();
        onMainThread(() -> {
            AppCompatDelegate.setDefaultNightMode(originalNightMode);
            AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(originalLanguageTags));
        });
    }

    @Test
    public void defaults_areDarkThemeAndSystemLanguage() {
        SettingsManager settings = SettingsManager.from(context);

        assertEquals(SettingsManager.THEME_DARK, settings.getTheme());
        assertEquals(SettingsManager.LANGUAGE_SYSTEM, settings.getLanguage());
    }

    @Test
    public void whatIsSet_isReadBackByAnotherInstance() {
        SettingsManager.from(context).setTheme(SettingsManager.THEME_LIGHT);
        SettingsManager.from(context).setLanguage(SettingsManager.LANGUAGE_PT);

        SettingsManager other = SettingsManager.from(context);
        assertEquals(SettingsManager.THEME_LIGHT, other.getTheme());
        assertEquals(SettingsManager.LANGUAGE_PT, other.getLanguage());
    }

    @Test
    public void applyTheme_mapsEachChoiceToTheMatchingNightMode() {
        SettingsManager settings = SettingsManager.from(context);

        settings.setTheme(SettingsManager.THEME_LIGHT);
        onMainThread(settings::applyTheme);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());

        settings.setTheme(SettingsManager.THEME_DARK);
        onMainThread(settings::applyTheme);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());

        settings.setTheme(SettingsManager.THEME_SYSTEM);
        onMainThread(settings::applyTheme);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void applyLanguage_setsTheAppLocale_andSystemClearsIt() {
        SettingsManager settings = SettingsManager.from(context);

        settings.setLanguage(SettingsManager.LANGUAGE_PT);
        onMainThread(settings::applyLanguage);
        assertEquals("pt", AppCompatDelegate.getApplicationLocales().toLanguageTags());

        settings.setLanguage(SettingsManager.LANGUAGE_EN);
        onMainThread(settings::applyLanguage);
        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags());

        settings.setLanguage(SettingsManager.LANGUAGE_SYSTEM);
        onMainThread(settings::applyLanguage);
        assertEquals("", AppCompatDelegate.getApplicationLocales().toLanguageTags());
    }

    @Test
    public void applyAll_appliesBothSavedChoices() {
        SettingsManager settings = SettingsManager.from(context);
        settings.setTheme(SettingsManager.THEME_LIGHT);
        settings.setLanguage(SettingsManager.LANGUAGE_PT);

        onMainThread(settings::applyAll);

        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
        assertEquals("pt", AppCompatDelegate.getApplicationLocales().toLanguageTags());
    }
}
