package com.example.albumsnap.views;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.R;
import com.example.albumsnap.testutil.PrefsBackup;
import com.example.albumsnap.util.ErrorMessages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Sessao do Spotify revogada: o ecra onde o erro aparece fecha e abre o login. Guarda e repoe o
 * login real do telemovel no fim de cada teste.
 */
@RunWith(AndroidJUnit4.class)
public class SessionExpiryEspressoTest {

    private PrefsBackup loginBackup;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        loginBackup = PrefsBackup.of(context, "spotify_auth");
        // A sessao ja foi apagada pelo SpotifyAuthManager quando o erro chega ao ecra
        loginBackup.clear();
    }

    @After
    public void tearDown() {
        loginBackup.restore();
    }

    @Test
    public void anExpiredSession_closesTheScreenAndShowsTheLogin() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            final boolean[] handled = {false};
            scenario.onActivity(activity -> handled[0] = SessionExpiry.handle(activity, ErrorMessages.SESSION_EXPIRED));

            assertTrue(handled[0]);
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aMissingSession_alsoGoesToTheLogin() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> SessionExpiry.handle(activity, ErrorMessages.NO_SESSION));

            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void anotherError_leavesTheScreenAlone() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            final boolean[] handled = {true};
            scenario.onActivity(activity -> handled[0] = SessionExpiry.handle(activity, ErrorMessages.NETWORK));

            assertFalse(handled[0]);
            onView(withId(R.id.btnLogout)).check(matches(isDisplayed()));   // continua nas Definicoes
        }
    }
}
