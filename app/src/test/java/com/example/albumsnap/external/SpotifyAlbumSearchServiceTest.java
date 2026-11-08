package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.repository.AlbumRepository;
import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeSpotifyAuth;
import com.example.albumsnap.testutil.SpotifyJson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/** Pesquisa de albuns no Spotify (GET /search?type=album). */
public class SpotifyAlbumSearchServiceTest {

    private FakeHttpServer server;
    private SpotifyAlbumSearchService service;
    private Album detailed;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        detailed = com.example.albumsnap.testutil.TestAlbums.album("det", "Detalhado", "Alguem");
        AlbumRepository repository = spotifyId -> detailed;
        service = new SpotifyAlbumSearchService(new SpotifyApiClient(new FakeSpotifyAuth(), server.baseUrl()), repository);
    }

    @After
    public void tearDown() {
        server.close();
    }

    private static String searchResponse(JSONObject... albums) throws Exception {
        JSONArray items = new JSONArray();
        for (JSONObject a : albums) items.put(a);
        return new JSONObject().put("albums", new JSONObject().put("items", items)).toString();
    }

    @Test
    public void searchAlbums_parsesTheAlbumsInOrder() throws Exception {
        server.reply("GET", "/search", 200, searchResponse(
                SpotifyJson.album("a1", "Primeiro", 10, null), SpotifyJson.album("a2", "Segundo", 12, null)));

        List<Album> albums = service.searchAlbums("daft punk");

        assertEquals(2, albums.size());
        assertEquals("a1", albums.get(0).getSpotifyId());
        assertEquals("Segundo", albums.get(1).getTitle());
        assertEquals("The Artist", albums.get(1).getArtistNames());
    }

    @Test
    public void searchAlbums_asksForAlbumsWithAtMostTenResultsAndTheTrimmedQuery() throws Exception {
        server.reply("GET", "/search", 200, searchResponse());

        service.searchAlbums("  Random Access Memories & Daft Punk  ");

        FakeHttpServer.RecordedRequest request = server.requests().get(0);
        assertEquals("album", request.queryParam("type"));
        assertEquals("10", request.queryParam("limit"));
        assertEquals("Random Access Memories & Daft Punk", request.queryParam("q"));   // com o & bem codificado
    }

    @Test
    public void searchAlbums_blankOrNullQuery_isEmptyWithoutAnyRequest() throws Exception {
        assertTrue(service.searchAlbums(null).isEmpty());
        assertTrue(service.searchAlbums("").isEmpty());
        assertTrue(service.searchAlbums("   ").isEmpty());
        assertEquals(0, server.requests().size());
    }

    @Test
    public void searchAlbums_responseWithoutAlbums_isEmpty() throws Exception {
        server.reply("GET", "/search", 200, "{}");
        assertTrue(service.searchAlbums("x").isEmpty());

        server.reply("GET", "/search", 200, "{\"albums\":{\"items\":[]}}");
        assertTrue(service.searchAlbums("x").isEmpty());
    }

    @Test
    public void searchAlbums_skipsNullEntries() throws Exception {
        JSONArray items = new JSONArray().put(JSONObject.NULL).put(SpotifyJson.album("a1", "Unico", 1, null));
        server.reply("GET", "/search", 200,
                new JSONObject().put("albums", new JSONObject().put("items", items)).toString());

        List<Album> albums = service.searchAlbums("x");

        assertEquals(1, albums.size());
        assertEquals("a1", albums.get(0).getSpotifyId());
    }

    @Test
    public void searchAlbums_apiErrors_arePropagated() {
        server.reply("GET", "/search", 429, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class,
                () -> service.searchAlbums("x"));

        assertEquals(429, e.getStatus());
    }

    @Test
    public void getAlbumDetails_usesTheRepository() throws Exception {
        assertSame(detailed, service.getAlbumDetails("qualquer"));
        assertEquals(0, server.requests().size());
    }
}
