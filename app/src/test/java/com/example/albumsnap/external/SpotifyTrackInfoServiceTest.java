package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeSpotifyAuth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class SpotifyTrackInfoServiceTest {

    private FakeHttpServer server;
    private SpotifyTrackInfoService service;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        service = new SpotifyTrackInfoService(new SpotifyApiClient(new FakeSpotifyAuth(), server.baseUrl()));
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void getIsrc_readsItFromExternalIds() throws Exception {
        server.reply("GET", "/tracks/T1", 200, "{\"id\":\"T1\",\"external_ids\":{\"isrc\":\"USUM71703861\"}}");

        assertEquals("USUM71703861", service.getIsrc("T1"));
    }

    @Test
    public void getIsrc_isEmptyWhenSpotifyDoesNotGiveOne() throws Exception {
        server.reply("GET", "/tracks/NO_IDS", 200, "{\"id\":\"x\"}");
        server.reply("GET", "/tracks/NO_ISRC", 200, "{\"external_ids\":{}}");
        server.reply("GET", "/tracks/NULL_ISRC", 200, "{\"external_ids\":{\"isrc\":null}}");

        assertEquals("", service.getIsrc("NO_IDS"));
        assertEquals("", service.getIsrc("NO_ISRC"));
        assertEquals("", service.getIsrc("NULL_ISRC"));   // e nao o texto "null"
    }

    @Test
    public void getIsrc_unknownTrack_throwsTheApiError() {
        server.reply("GET", "/tracks/NOPE", 404, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class,
                () -> service.getIsrc("NOPE"));

        assertEquals(404, e.getStatus());
    }
}
