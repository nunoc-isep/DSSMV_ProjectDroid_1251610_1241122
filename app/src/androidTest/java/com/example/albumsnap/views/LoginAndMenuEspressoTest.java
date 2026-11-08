package com.example.albumsnap.views;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.R;
import com.example.albumsnap.testutil.PrefsBackup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Entrada na app: sem sessao aparece o botao de login; com sessao abre o menu e este navega para
 * a Biblioteca e para as Definicoes. Guarda e repoe o login real do telemovel no fim de cada teste
 * (a sessao "com login" e simulada com um token falso: nao ha pedidos ao Spotify).
 */
@RunWith(AndroidJUnit4.class)
public class LoginAndMenuEspressoTest {

    private PrefsBackup loginBackup;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        loginBackup = PrefsBackup.of(context, "spotify_auth");
    }

    @After
    public void tearDown() {
        loginBackup.restore();
    }

    private void fakeLogin() {
        context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE).edit()
                .putString("access_token", "token-falso-dos-testes")
                .putLong("expires_at", System.currentTimeMillis() + 3_600_000)
                .commit();
    }

    @Test
    public void withoutASession_theLoginButtonIsShown() {
        loginBackup.clear();

        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btnLogin)).check(matches(allOf(isDisplayed(), isEnabled())));
        }
    }

    @Test
    public void withASession_theLoginScreenGoesStraightToTheMenu() {
        fakeLogin();

        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btnCamera)).check(matches(isDisplayed()));
            onView(withId(R.id.btnLibrary)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSettings)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void theMenu_opensTheLibraryAndGoesBack() {
        fakeLogin();

        try (ActivityScenario<MainMenuActivity> scenario = ActivityScenario.launch(MainMenuActivity.class)) {
            onView(withId(R.id.btnLibrary)).perform(click());

            onView(withId(R.id.libraryRoot)).check(matches(isDisplayed()));
            onView(withId(R.id.btnBack)).perform(click());
            onView(withId(R.id.btnCamera)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void theMenu_opensTheSettings() {
        fakeLogin();

        try (ActivityScenario<MainMenuActivity> scenario = ActivityScenario.launch(MainMenuActivity.class)) {
            onView(withId(R.id.btnSettings)).perform(click());

            onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()));
            onView(withId(R.id.radioLanguage)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void loggingOutFromTheSettings_returnsToTheLoginScreen() {
        fakeLogin();

        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.btnLogout)).perform(click());

            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
        // o logout apagou o token falso (e o teste repoe o real em tearDown)
        org.junit.Assert.assertTrue(context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
                .getString("access_token", null) == null);
    }
}
