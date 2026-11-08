package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeSpotifyAuth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/** Favoritos: um album ou uma faixa e guardado com o seu URI do Spotify (PUT /me/library). */
public class SpotifyFavouriteServicesTest {

    private FakeHttpServer server;
    private SpotifyApiClient client;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        client = new SpotifyApiClient(new FakeSpotifyAuth(), server.baseUrl());
        server.reply("PUT", "/me/library", 200, "");
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void addAlbum_savesTheAlbumUri() throws Exception {
        new SpotifyFavouriteAlbumService(client).addAlbum("4m2880jivSbbyEGAKfITCa");

        assertEquals("spotify:album:4m2880jivSbbyEGAKfITCa", server.requests().get(0).queryParam("uris"));
    }

    @Test
    public void addTrack_savesTheTrackUri() throws Exception {
        new SpotifyFavouriteTrackService(client).addTrack("0DiWol3AO6WpXZgp0goxAV");

        assertEquals("spotify:track:0DiWol3AO6WpXZgp0goxAV", server.requests().get(0).queryParam("uris"));
    }

    @Test
    public void bothServices_reportThePermissionErrorOf403() {
        server.reply("PUT", "/me/library", 403, "{}");

        assertThrows(SpotifyApiClient.ApiException.class, () -> new SpotifyFavouriteAlbumService(client).addAlbum("x"));
        assertThrows(SpotifyApiClient.ApiException.class, () -> new SpotifyFavouriteTrackService(client).addTrack("x"));
    }
}
