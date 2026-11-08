package com.example.albumsnap.views;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.R;
import com.example.albumsnap.storage.SettingsManager;
import com.example.albumsnap.testutil.PrefsBackup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Ecra das Definicoes no ecra: trocar o idioma muda os textos, trocar o tema fica guardado.
 * Guarda e repoe as definicoes reais da app no fim de cada teste.
 */
@RunWith(AndroidJUnit4.class)
public class SettingsActivityEspressoTest {

    private Context context;
    private PrefsBackup backup;
    private int originalNightMode;
    private String originalLanguageTags;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        backup = PrefsBackup.of(context, "settings");
        originalNightMode = AppCompatDelegate.getDefaultNightMode();
        originalLanguageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        backup.clear();   // comeca em: tema escuro, idioma do sistema
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")));   // textos em ingles
    }

    @After
    public void tearDown() {
        backup.restore();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(originalNightMode);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(originalLanguageTags));
        });
    }

    @Test
    public void opensWithTheSavedChoicesSelected() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.radioThemeDark)).check(matches(isChecked()));       // por omissao: escuro
            onView(withId(R.id.radioLangSystem)).check(matches(isChecked()));
            onView(withId(R.id.btnLogout)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void choosingPortuguese_changesTheTextsOfTheScreenAndIsSaved() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.radioLangPt)).perform(click());

            onView(withId(R.id.btnLogout)).check(matches(withText("Terminar sessão")));
            onView(withId(R.id.radioLangPt)).check(matches(isChecked()));
        }
        assertEquals(SettingsManager.LANGUAGE_PT, SettingsManager.from(context).getLanguage());
    }

    @Test
    public void choosingEnglishAfterPortuguese_bringsTheEnglishTextsBack() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.radioLangPt)).perform(click());
            onView(withId(R.id.btnLogout)).check(matches(withText("Terminar sessão")));

            onView(withId(R.id.radioLangEn)).perform(click());

            onView(withId(R.id.btnLogout)).check(matches(withText("Log out")));
        }
    }

    @Test
    public void choosingTheLightTheme_isSavedAndStaysSelected() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.radioThemeLight)).perform(click());

            onView(withId(R.id.radioThemeLight)).check(matches(isChecked()));
        }
        assertEquals(SettingsManager.THEME_LIGHT, SettingsManager.from(context).getTheme());
    }
}
