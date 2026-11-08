package com.example.albumsnap.external;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Quando e que o Spotify recusou o refresh token "de vez" (e a sessao tem de acabar). */
public class TokenErrorsTest {

    @Test
    public void invalidGrant_withHttp400_meansTheSessionIsRevoked() {
        assertTrue(TokenErrors.isRevokedGrant(400, "{\"error\":\"invalid_grant\"}"));
        assertTrue(TokenErrors.isRevokedGrant(400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}"));
        assertTrue(TokenErrors.isRevokedGrant(400, " { \"error\" : \"invalid_grant\" } "));
    }

    @Test
    public void otherOauthErrors_doNotEndTheSession() {
        assertFalse(TokenErrors.isRevokedGrant(400, "{\"error\":\"invalid_client\"}"));
        assertFalse(TokenErrors.isRevokedGrant(400, "{\"error\":\"invalid_request\"}"));
        assertFalse(TokenErrors.isRevokedGrant(400, "{\"error\":\"unsupported_grant_type\"}"));
    }

    @Test
    public void otherStatusCodes_doNotEndTheSession_evenWithTheSameError() {
        assertFalse(TokenErrors.isRevokedGrant(401, "{\"error\":\"invalid_grant\"}"));
        assertFalse(TokenErrors.isRevokedGrant(429, "{\"error\":\"invalid_grant\"}"));
        assertFalse(TokenErrors.isRevokedGrant(500, "{\"error\":\"invalid_grant\"}"));
        assertFalse(TokenErrors.isRevokedGrant(200, "{\"error\":\"invalid_grant\"}"));
    }

    @Test
    public void aBodyThatIsNotAUsableJson_doesNotEndTheSession() {
        assertFalse(TokenErrors.isRevokedGrant(400, null));
        assertFalse(TokenErrors.isRevokedGrant(400, ""));
        assertFalse(TokenErrors.isRevokedGrant(400, "<html>Bad Request</html>"));
        assertFalse(TokenErrors.isRevokedGrant(400, "{}"));
        assertFalse(TokenErrors.isRevokedGrant(400, "{\"error\":null}"));
        assertFalse(TokenErrors.isRevokedGrant(400, "{\"error\":{\"status\":400}}"));
    }
}
