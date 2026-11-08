package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeHttpServer.Reply;
import com.example.albumsnap.util.ErrorMessages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Login Authorization Code + PKCE: o desafio, a validacao do redirect, a troca do codigo e a
 * renovacao do token. Usa uma instancia isolada (outro ficheiro de preferencias) e um servidor
 * de tokens falso, por isso nao mexe no login real do telemovel. O HTTP local so e permitido na
 * variante debug (src/debug/AndroidManifest.xml).
 */
@RunWith(AndroidJUnit4.class)
public class SpotifyAuthManagerInstrumentedTest {

    private static final String PREFS = "test_spotify_auth";

    private Context context;
    private FakeHttpServer server;
    private SpotifyAuthManager auth;
    private SharedPreferences prefs;

    /** Callback de login que guarda o resultado; o teste espera por ele. */
    private static final class Login implements SpotifyAuthManager.LoginCallback {
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean success;
        volatile String error;

        @Override
        public void onSuccess() {
            success = true;
            done.countDown();
        }

        @Override
        public void onError(String message) {
            error = message;
            done.countDown();
        }

        Login await() throws InterruptedException {
            assertTrue("o login nao terminou em 10 s", done.await(10, TimeUnit.SECONDS));
            return this;
        }
    }

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteSharedPreferences(PREFS);
        server = new FakeHttpServer();
        auth = SpotifyAuthManager.createForTests(context, PREFS, server.baseUrl() + "/api/token");
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @After
    public void tearDown() {
        server.close();
        context.deleteSharedPreferences(PREFS);
    }

    private void tokenServerReplies(int status, String body) {
        server.reply("POST", "/api/token", status, body);
    }

    private static Map<String, String> form(String body) throws UnsupportedEncodingException {
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            fields.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                    URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
        }
        return fields;
    }

    private void pendingLogin(String verifier, String state) {
        prefs.edit().putString("code_verifier", verifier).putString("state", state).commit();
    }

    private void storeTokens(String access, String refresh, long expiresAt) {
        SharedPreferences.Editor editor = prefs.edit();
        if (access != null) editor.putString("access_token", access);
        if (refresh != null) editor.putString("refresh_token", refresh);
        editor.putLong("expires_at", expiresAt).commit();
    }

    /** String.repeat so existe a partir do Android 14: evita-se para os testes correrem em qualquer versao. */
    private static String repeat(String text, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(text);
        return sb.toString();
    }

    // ------------------------------------------------------------------ PKCE

    @Test
    public void challengeFor_matchesTheRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                SpotifyAuthManager.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    public void challengeFor_isAlways43UrlSafeCharactersWithoutPadding() {
        String[] verifiers = {"a", repeat("x", 43), repeat("ABCabc123-._~", 5), repeat("z", 128)};
        Pattern urlSafe = Pattern.compile("[A-Za-z0-9_-]{43}");

        for (String verifier : verifiers) {
            assertTrue(verifier, urlSafe.matcher(SpotifyAuthManager.challengeFor(verifier)).matches());
        }
    }

    // ------------------------------------------------------- redirect handling

    @Test
    public void handleRedirect_ignoresUrisThatAreNotTheAppRedirect() {
        Login login = new Login();

        assertFalse(auth.handleRedirect(null, login));
        assertFalse(auth.handleRedirect(Uri.parse("https://example.com/callback?code=x"), login));
        assertFalse(auth.handleRedirect(Uri.parse("albumsnap://outro?code=x"), login));
        assertFalse(auth.handleRedirect(Uri.parse("outra://callback?code=x"), login));

        assertEquals(1, login.done.getCount());   // nenhum callback
        assertEquals(0, server.requests().size());
    }

    @Test
    public void handleRedirect_userDeniedAccess_isReportedAsCancelled() {
        Login login = new Login();

        assertTrue(auth.handleRedirect(Uri.parse("albumsnap://callback?error=access_denied&state=s"), login));

        assertEquals(ErrorMessages.LOGIN_CANCELLED, login.error);
        assertEquals(0, server.requests().size());
    }

    @Test
    public void handleRedirect_withoutAPendingLogin_isInvalid() {
        Login login = new Login();

        assertTrue(auth.handleRedirect(Uri.parse("albumsnap://callback?code=abc&state=xyz"), login));

        assertEquals(ErrorMessages.LOGIN_INVALID, login.error);
        assertEquals(0, server.requests().size());
    }

    @Test
    public void handleRedirect_aDifferentState_isRejected_andNothingIsSent() {
        pendingLogin("VERIFIER", "RIGHT");
        Login login = new Login();

        assertTrue(auth.handleRedirect(Uri.parse("albumsnap://callback?code=abc&state=WRONG"), login));

        assertEquals(ErrorMessages.LOGIN_INVALID, login.error);
        assertEquals(0, server.requests().size());
        assertEquals("VERIFIER", prefs.getString("code_verifier", null));   // continua pendente
    }

    @Test
    public void handleRedirect_withoutACode_isInvalid() {
        pendingLogin("VERIFIER", "RIGHT");
        Login login = new Login();

        assertTrue(auth.handleRedirect(Uri.parse("albumsnap://callback?state=RIGHT"), login));

        assertEquals(ErrorMessages.LOGIN_INVALID, login.error);
    }

    // ----------------------------------------------------------- code exchange

    @Test
    public void handleRedirect_validCode_isExchangedForTokens() throws Exception {
        pendingLogin("VERIFIER123", "STATE123");
        tokenServerReplies(200, "{\"access_token\":\"ACCESS\",\"refresh_token\":\"REFRESH\",\"expires_in\":3600}");
        Login login = new Login();

        assertTrue(auth.handleRedirect(Uri.parse("albumsnap://callback?code=CODE&state=STATE123"), login));
        login.await();

        assertTrue(login.success);
        FakeHttpServer.RecordedRequest request = server.requests().get(0);
        assertEquals("POST", request.method);
        assertTrue(request.header("Content-Type").startsWith("application/x-www-form-urlencoded"));
        Map<String, String> sent = form(request.body);
        assertEquals("authorization_code", sent.get("grant_type"));
        assertEquals("CODE", sent.get("code"));
        assertEquals(SpotifyAuthManager.REDIRECT_URI, sent.get("redirect_uri"));
        assertEquals(SpotifyAuthManager.CLIENT_ID, sent.get("client_id"));
        assertEquals("VERIFIER123", sent.get("code_verifier"));   // PKCE: o segredo so sai agora

        assertTrue(auth.isAuthenticated());
        assertEquals("ACCESS", auth.getAccessToken());
        assertEquals(1, server.requests().size());                // o token ainda e valido: sem novo pedido
    }

    @Test
    public void handleRedirect_aRedirectCanOnlyBeUsedOnce() throws Exception {
        pendingLogin("VERIFIER", "STATE");
        tokenServerReplies(200, "{\"access_token\":\"A\",\"refresh_token\":\"R\",\"expires_in\":3600}");
        Login first = new Login();
        auth.handleRedirect(Uri.parse("albumsnap://callback?code=C&state=STATE"), first);
        first.await();

        Login second = new Login();
        assertTrue(auth.handleRedirect(Uri.parse("albumsnap://callback?code=C&state=STATE"), second));

        assertEquals(ErrorMessages.LOGIN_INVALID, second.error);
        assertNull(prefs.getString("code_verifier", null));
        assertNull(prefs.getString("state", null));
    }

    @Test
    public void handleRedirect_whenTheTokenServerRejects_reportsTheTokenFailure() throws Exception {
        pendingLogin("VERIFIER", "STATE");
        tokenServerReplies(400, "{\"error\":\"invalid_grant\"}");
        Login login = new Login();

        auth.handleRedirect(Uri.parse("albumsnap://callback?code=C&state=STATE"), login);
        login.await();

        assertEquals(ErrorMessages.LOGIN_TOKEN_FAILED, login.error);
        assertFalse(auth.isAuthenticated());
    }

    // ----------------------------------------------------------- using the token

    @Test
    public void getAccessToken_aValidToken_isReturnedWithoutTouchingTheNetwork() throws Exception {
        storeTokens("CACHED", "R", System.currentTimeMillis() + 3_600_000);

        assertEquals("CACHED", auth.getAccessToken());

        assertEquals(0, server.requests().size());
    }

    @Test
    public void getAccessToken_anExpiredToken_isRefreshedWithTheRefreshToken() throws Exception {
        storeTokens("OLD", "REFRESH1", System.currentTimeMillis() - 1000);
        tokenServerReplies(200, "{\"access_token\":\"NEW\",\"expires_in\":3600}");   // sem refresh_token novo

        assertEquals("NEW", auth.getAccessToken());

        Map<String, String> sent = form(server.requests().get(0).body);
        assertEquals("refresh_token", sent.get("grant_type"));
        assertEquals("REFRESH1", sent.get("refresh_token"));
        assertEquals(SpotifyAuthManager.CLIENT_ID, sent.get("client_id"));
        assertEquals("REFRESH1", prefs.getString("refresh_token", null));   // mantem o antigo
    }

    @Test
    public void refresh_aNewRefreshTokenReplacesTheOldOne() throws Exception {
        storeTokens("OLD", "REFRESH1", 0);
        tokenServerReplies(200, "{\"access_token\":\"NEW\",\"refresh_token\":\"REFRESH2\",\"expires_in\":3600}");

        auth.getAccessToken();

        assertEquals("REFRESH2", prefs.getString("refresh_token", null));
    }

    @Test
    public void refresh_expiryKeepsAOneMinuteSafetyMargin() throws Exception {
        storeTokens("OLD", "R", 0);
        tokenServerReplies(200, "{\"access_token\":\"NEW\",\"expires_in\":3600}");
        long before = System.currentTimeMillis();

        auth.getAccessToken();

        long expiresAt = prefs.getLong("expires_at", 0);
        assertTrue(expiresAt >= before + 3_540_000 - 1000);   // 3600 s - 60 s
        assertTrue(expiresAt <= System.currentTimeMillis() + 3_540_000);
    }

    @Test
    public void getAccessToken_withoutAnyToken_saysThereIsNoSession() {
        IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());

        assertEquals(ErrorMessages.NO_SESSION, e.getMessage());
    }

    // ---------------------------------------------------- sessao revogada

    private static final String REVOKED =
            "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}";

    private void assertSessionStillStored() {
        assertTrue(auth.isAuthenticated());
        assertEquals("R", prefs.getString("refresh_token", null));
        assertEquals("OLD", prefs.getString("access_token", null));
    }

    @Test
    public void refresh_aRevokedRefreshToken_endsTheSessionInsteadOfLeavingTheUserStuck() {
        storeTokens("OLD", "R", 0);
        tokenServerReplies(400, REVOKED);

        IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());

        assertEquals(ErrorMessages.SESSION_EXPIRED, e.getMessage());
        assertFalse(auth.isAuthenticated());   // o ecra de login volta a aparecer
        assertNull(prefs.getString("access_token", null));
        assertNull(prefs.getString("refresh_token", null));
        assertEquals(0, prefs.getLong("expires_at", 0));
    }

    @Test
    public void refresh_aRevokedSession_doesNotLeakTheServerBodyToTheMessage() {
        storeTokens("OLD", "R", 0);
        tokenServerReplies(400, REVOKED);

        IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());

        assertFalse(e.getMessage(), e.getMessage().contains("revoked"));
        assertFalse(e.getMessage(), e.getMessage().contains("HTTP"));
    }

    @Test
    public void afterARevocation_theNextCallSaysThereIsNoSession() {
        storeTokens("OLD", "R", 0);
        tokenServerReplies(400, REVOKED);
        assertThrows(IOException.class, () -> auth.getAccessToken());

        IOException second = assertThrows(IOException.class, () -> auth.getAccessToken());

        assertEquals(ErrorMessages.NO_SESSION, second.getMessage());
        assertEquals(1, server.requests().size());   // nao insiste num token que ja se sabe morto
    }

    @Test
    public void afterARevocation_aNewLoginWorksNormally() throws Exception {
        storeTokens("OLD", "R", 0);
        tokenServerReplies(400, REVOKED);
        assertThrows(IOException.class, () -> auth.getAccessToken());

        pendingLogin("VERIFIER", "STATE");
        tokenServerReplies(200, "{\"access_token\":\"FRESH\",\"refresh_token\":\"R2\",\"expires_in\":3600}");
        Login login = new Login();
        auth.handleRedirect(Uri.parse("albumsnap://callback?code=C&state=STATE"), login);
        login.await();

        assertTrue(login.success);
        assertEquals("FRESH", auth.getAccessToken());
    }

    @Test
    public void refresh_otherRejectionsOfTheTokenServer_keepTheSession() {
        for (String body : new String[]{"{\"error\":\"invalid_client\"}", "{\"error\":\"invalid_request\"}", "nao e json"}) {
            storeTokens("OLD", "R", 0);
            tokenServerReplies(400, body);

            IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());

            assertEquals(ErrorMessages.spotifyHttp(400), e.getMessage());
            assertSessionStillStored();
        }
    }

    @Test
    public void refresh_serverErrorsAndRateLimits_keepTheSession() {
        for (int status : new int[]{401, 429, 500, 503}) {
            storeTokens("OLD", "R", 0);
            tokenServerReplies(status, REVOKED);   // mesmo com o corpo "invalid_grant": so o 400 acaba a sessao

            IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());

            assertEquals(ErrorMessages.spotifyHttp(status), e.getMessage());
            assertSessionStillStored();
        }
    }

    @Test
    public void refresh_networkFailure_keepsTheSession() {
        storeTokens("OLD", "R", 0);
        server.close();   // ninguem a escutar: ligacao recusada

        IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());

        assertFalse(ErrorMessages.SESSION_EXPIRED.equals(e.getMessage()));
        assertSessionStillStored();
    }

    @Test
    public void refreshAccessToken_forcesANewTokenEvenIfTheCurrentOneLooksValid() throws Exception {
        storeTokens("STILL_VALID", "R", System.currentTimeMillis() + 3_600_000);
        tokenServerReplies(200, "{\"access_token\":\"FORCED\",\"expires_in\":3600}");

        assertEquals("FORCED", auth.refreshAccessToken());

        assertEquals(1, server.requests().size());
        assertEquals("FORCED", auth.getAccessToken());
    }

    @Test
    public void isAuthenticated_needsAtLeastAnAccessOrRefreshToken() {
        assertFalse(auth.isAuthenticated());

        storeTokens(null, "R", 0);
        assertTrue(auth.isAuthenticated());

        prefs.edit().clear().commit();
        storeTokens("A", null, 0);
        assertTrue(auth.isAuthenticated());
    }

    @Test
    public void disconnect_forgetsEverything() {
        storeTokens("A", "R", System.currentTimeMillis() + 3_600_000);
        pendingLogin("V", "S");

        auth.disconnect();

        assertFalse(auth.isAuthenticated());
        assertTrue(prefs.getAll().isEmpty());
        IOException e = assertThrows(IOException.class, () -> auth.getAccessToken());
        assertEquals(ErrorMessages.NO_SESSION, e.getMessage());
    }
}
