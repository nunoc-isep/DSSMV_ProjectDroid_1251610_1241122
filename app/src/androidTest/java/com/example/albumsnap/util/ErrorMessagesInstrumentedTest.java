package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Traducao das mensagens de erro e dos textos da app nos dois idiomas. */
@RunWith(AndroidJUnit4.class)
public class ErrorMessagesInstrumentedTest {

    private Context en;
    private Context pt;

    private static Context withLocale(Context base, String language) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(new Locale(language));
        return base.createConfigurationContext(configuration);
    }

    @Before
    public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        en = withLocale(target, "en");
        pt = withLocale(target, "pt");
    }

    private static Map<String, Integer> codesAndTexts() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(ErrorMessages.NETWORK, R.string.error_network);
        map.put(ErrorMessages.NO_SESSION, R.string.error_no_session);
        map.put(ErrorMessages.LOGIN_CANCELLED, R.string.login_error_cancelled);
        map.put(ErrorMessages.LOGIN_INVALID, R.string.login_error_invalid);
        map.put(ErrorMessages.LOGIN_TOKEN_FAILED, R.string.login_error_token);
        map.put(ErrorMessages.UNEXPECTED_RESPONSE, R.string.error_unexpected_response);
        map.put(ErrorMessages.SESSION_EXPIRED, R.string.error_session_expired);
        map.put(ErrorMessages.SPOTIFY_FORBIDDEN, R.string.error_spotify_forbidden);
        map.put(ErrorMessages.SPOTIFY_NOT_FOUND, R.string.error_spotify_not_found);
        map.put(ErrorMessages.SPOTIFY_RATE_LIMIT, R.string.error_spotify_rate_limit);
        return map;
    }

    @Test
    public void everyCode_isTranslatedIntoTheLanguageOfTheContext() {
        for (Map.Entry<String, Integer> e : codesAndTexts().entrySet()) {
            String english = ErrorMessages.localize(en, e.getKey());
            String portuguese = ErrorMessages.localize(pt, e.getKey());

            assertEquals(en.getString(e.getValue()), english);
            assertEquals(pt.getString(e.getValue()), portuguese);
            assertNotEquals("sem traducao para " + e.getKey(), english, portuguese);
            assertFalse("o codigo chegou ao utilizador", english.startsWith("error."));
            assertFalse("o codigo chegou ao utilizador", portuguese.startsWith("error."));
        }
    }

    @Test
    public void genericSpotifyHttpError_showsTheStatusInBothLanguages() {
        String code = ErrorMessages.spotifyHttp(503);

        assertEquals(en.getString(R.string.error_spotify_http, 503), ErrorMessages.localize(en, code));
        assertTrue(ErrorMessages.localize(en, code).contains("503"));
        assertTrue(ErrorMessages.localize(pt, code).contains("503"));
    }

    @Test
    public void unknownMessages_passThroughUnchanged() {
        assertEquals("HTTP 400 {\"error\":\"x\"}", ErrorMessages.localize(en, "HTTP 400 {\"error\":\"x\"}"));
        assertEquals("", ErrorMessages.localize(en, null));
    }

    @Test
    public void everyStringOfTheAppResolvesInBothLanguages() throws Exception {
        for (Field field : R.string.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
            int id = field.getInt(null);

            assertFalse("EN vazia: " + field.getName(), en.getString(id).isEmpty());
            assertFalse("PT vazia: " + field.getName(), pt.getString(id).isEmpty());
        }
    }

    @Test
    public void portugueseTextsReallyDifferFromEnglishOnes() {
        assertEquals("Cancel", en.getString(R.string.action_cancel));
        assertEquals("Cancelar", pt.getString(R.string.action_cancel));
        assertEquals("Disc 2", en.getString(R.string.album_disc, 2));
        assertEquals("Disco 2", pt.getString(R.string.album_disc, 2));
    }
}
