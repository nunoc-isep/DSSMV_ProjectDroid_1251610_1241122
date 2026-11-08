package com.example.albumsnap.views;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.util.ErrorMessages;

import org.junit.Test;

/** Que erros levam o utilizador de volta ao login. */
public class SessionExpiryTest {

    @Test
    public void anExpiredOrMissingSession_sendsTheUserToTheLogin() {
        assertTrue(SessionExpiry.isSessionLost(ErrorMessages.SESSION_EXPIRED));
        assertTrue(SessionExpiry.isSessionLost(ErrorMessages.NO_SESSION));
    }

    @Test
    public void everyOtherError_staysOnTheCurrentScreen() {
        assertFalse(SessionExpiry.isSessionLost(ErrorMessages.NETWORK));
        assertFalse(SessionExpiry.isSessionLost(ErrorMessages.SPOTIFY_FORBIDDEN));
        assertFalse(SessionExpiry.isSessionLost(ErrorMessages.SPOTIFY_NOT_FOUND));
        assertFalse(SessionExpiry.isSessionLost(ErrorMessages.SPOTIFY_RATE_LIMIT));
        assertFalse(SessionExpiry.isSessionLost(ErrorMessages.spotifyHttp(500)));
        assertFalse(SessionExpiry.isSessionLost("HTTP 400 qualquer coisa"));
        assertFalse(SessionExpiry.isSessionLost(""));
        assertFalse(SessionExpiry.isSessionLost(null));
    }
}
