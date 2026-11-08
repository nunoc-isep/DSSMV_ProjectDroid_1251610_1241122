package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeHttpServer.RecordedRequest;
import com.example.albumsnap.testutil.FakeHttpServer.Reply;
import com.example.albumsnap.testutil.FakeSpotifyAuth;
import com.example.albumsnap.util.ErrorMessages;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** Pedidos HTTP do cliente do Spotify, contra um servidor local. */
public class SpotifyApiClientTest {

    private FakeHttpServer server;
    private FakeSpotifyAuth auth;
    private SpotifyApiClient client;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        auth = new FakeSpotifyAuth();
        client = new SpotifyApiClient(auth, server.baseUrl());
    }

    @After
    public void tearDown() {
        server.close();
    }

    private SpotifyApiClient.ApiException statusOf(int status) {
        server.reply("GET", "/boom", status, "{\"error\":{\"message\":\"detalhe tecnico\"}}");
        return assertThrows(SpotifyApiClient.ApiException.class, () -> client.get("/boom"));
    }

    // -------------------------------------------------------------------- GET

    @Test
    public void get_sendsTheBearerTokenAndParsesTheJson() throws Exception {
        server.reply("GET", "/me", 200, "{\"id\":\"user1\"}");

        JSONObject json = client.get("/me");

        assertEquals("user1", json.getString("id"));
        RecordedRequest request = server.requests().get(0);
        assertEquals("Bearer " + FakeSpotifyAuth.TOKEN, request.header("Authorization"));
        assertEquals("application/json", request.header("Accept"));
    }

    @Test
    public void get_keepsTheQueryString() throws Exception {
        server.reply("GET", "/albums/x/tracks", 200, "{}");

        client.get("/albums/x/tracks?limit=50&offset=40");

        RecordedRequest request = server.requests().get(0);
        assertEquals("50", request.queryParam("limit"));
        assertEquals("40", request.queryParam("offset"));
    }

    @Test
    public void get_invalidJson_isReportedAsUnexpectedResponse() {
        server.reply("GET", "/me", 200, "isto nao e json");

        IOException e = assertThrows(IOException.class, () -> client.get("/me"));

        assertEquals(ErrorMessages.UNEXPECTED_RESPONSE, e.getMessage());
    }

    @Test
    public void httpErrors_becomeApiExceptionsWithATranslatableCode() {
        assertEquals(401, statusOf(401).getStatus());
        assertEquals(ErrorMessages.SESSION_EXPIRED, statusOf(401).getMessage());
        assertEquals(ErrorMessages.SPOTIFY_FORBIDDEN, statusOf(403).getMessage());
        assertEquals(ErrorMessages.SPOTIFY_NOT_FOUND, statusOf(404).getMessage());
        assertEquals(ErrorMessages.SPOTIFY_RATE_LIMIT, statusOf(429).getMessage());
        assertEquals(ErrorMessages.spotifyHttp(500), statusOf(500).getMessage());
        assertEquals(500, statusOf(500).getStatus());
    }

    @Test
    public void httpErrors_doNotLeakTheServerErrorBodyToTheMessage() {
        assertTrue(!statusOf(500).getMessage().contains("detalhe tecnico"));
    }

    // ------------------------------------------------------------ POST and PUT

    @Test
    public void post_sendsTheJsonBody() throws Exception {
        server.reply("POST", "/playlists/p1/items", 201, "{\"snapshot_id\":\"s\"}");

        client.post("/playlists/p1/items", new JSONObject().put("uris", new org.json.JSONArray().put("spotify:track:t1")));

        RecordedRequest request = server.requests().get(0);
        assertEquals("POST", request.method);
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("spotify:track:t1", new JSONObject(request.body).getJSONArray("uris").getString(0));
    }

    @Test
    public void put_withoutBody_sendsAnEmptyBody() throws Exception {
        server.reply("PUT", "/me/library", 200, "");

        client.put("/me/library?uris=x", null);

        RecordedRequest request = server.requests().get(0);
        assertEquals("PUT", request.method);
        assertEquals("", request.body);
        assertEquals("0", request.header("Content-Length"));   // o Spotify exige Content-Length: 0
    }

    // ------------------------------------------------------------ saveToLibrary

    @Test
    public void saveToLibrary_putsTheUriInTheUrisParameter() throws Exception {
        server.reply("PUT", "/me/library", 200, "");

        client.saveToLibrary("spotify:track:abc123");

        assertEquals(1, server.requests().size());
        RecordedRequest request = server.requests().get(0);
        assertEquals("spotify:track:abc123", request.queryParam("uris"));
        assertEquals("", request.body);
    }

    @Test
    public void saveToLibrary_on400_retriesWithTheUriInAJsonBody() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.on("PUT", "/me/library", request ->
                calls.getAndIncrement() == 0 ? new Reply(400, "{}") : new Reply(200, ""));

        client.saveToLibrary("spotify:album:alb1");

        assertEquals(2, server.requests().size());
        RecordedRequest retry = server.requests().get(1);
        assertEquals("spotify:album:alb1", new JSONObject(retry.body).getJSONArray("uris").getString(0));
    }

    @Test
    public void saveToLibrary_onOtherErrors_doesNotRetry() {
        server.reply("PUT", "/me/library", 403, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class,
                () -> client.saveToLibrary("spotify:track:abc"));

        assertEquals(403, e.getStatus());
        assertEquals(1, server.requests().size());
    }

    // ------------------------------------------------- sessao revogada (401)

    @Test
    public void a401_refreshesTheTokenOnceAndRetriesTheRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.on("GET", "/me", request -> calls.getAndIncrement() == 0
                ? new Reply(401, "{\"error\":{\"message\":\"The access token expired\"}}")
                : new Reply(200, "{\"id\":\"user1\"}"));

        JSONObject json = client.get("/me");

        assertEquals("user1", json.getString("id"));
        assertEquals(1, auth.refreshCalls);
        assertEquals(2, server.requests().size());
        assertEquals("Bearer " + FakeSpotifyAuth.TOKEN, server.requests().get(0).header("Authorization"));
        assertEquals("Bearer " + FakeSpotifyAuth.REFRESHED_TOKEN, server.requests().get(1).header("Authorization"));
    }

    @Test
    public void a401_retryKeepsTheMethodAndTheBody() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.on("POST", "/playlists/p1/items", request -> calls.getAndIncrement() == 0
                ? new Reply(401, "{}") : new Reply(201, "{}"));
        JSONObject body = new JSONObject().put("uris", new org.json.JSONArray().put("spotify:track:t1"));

        client.post("/playlists/p1/items", body);

        assertEquals(2, server.requests().size());
        assertEquals("POST", server.requests().get(1).method);
        assertEquals(server.requests().get(0).body, server.requests().get(1).body);
    }

    @Test
    public void ifTheRetryIsStillRefused_itGivesUpAfterASingleRefresh() {
        server.reply("GET", "/me", 401, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class, () -> client.get("/me"));

        assertEquals(401, e.getStatus());
        assertEquals(ErrorMessages.SESSION_EXPIRED, e.getMessage());
        assertEquals(1, auth.refreshCalls);
        assertEquals(2, server.requests().size());   // nunca em ciclo
    }

    @Test
    public void ifTheRefreshTokenWasRevoked_theSessionErrorIsPassedOnAndNothingIsRetried() {
        server.reply("GET", "/me", 401, "{}");
        auth.refreshFailure = new IOException(ErrorMessages.SESSION_EXPIRED);

        IOException e = assertThrows(IOException.class, () -> client.get("/me"));

        assertEquals(ErrorMessages.SESSION_EXPIRED, e.getMessage());
        assertEquals(1, server.requests().size());
    }

    @Test
    public void ifTheRefreshFailsForAnotherReason_thatErrorIsPassedOn() {
        server.reply("GET", "/me", 401, "{}");
        auth.refreshFailure = new IOException("sem rede");

        IOException e = assertThrows(IOException.class, () -> client.get("/me"));

        assertEquals("sem rede", e.getMessage());
    }

    @Test
    public void otherErrors_neverTriggerATokenRefresh() {
        for (int status : new int[]{400, 403, 404, 429, 500}) {
            statusOf(status);
        }

        assertEquals(0, auth.refreshCalls);
    }

    @Test
    public void aSuccessfulRequest_neverRefreshes() throws Exception {
        server.reply("GET", "/me", 200, "{}");

        client.get("/me");

        assertEquals(0, auth.refreshCalls);
    }
}
