package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Track;
import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeHttpServer.Reply;
import com.example.albumsnap.testutil.FakeSpotifyAuth;
import com.example.albumsnap.testutil.SpotifyJson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Obter um album do Spotify, incluindo albuns com mais faixas do que cabem no primeiro pedido. */
public class SpotifyAlbumRepositoryTest {

    private FakeHttpServer server;
    private SpotifyAlbumRepository repository;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        repository = new SpotifyAlbumRepository(new SpotifyApiClient(new FakeSpotifyAuth(), server.baseUrl()));
    }

    @After
    public void tearDown() {
        server.close();
    }

    /** Servidor de faixas: devolve as faixas t1..tTotal em paginas, com no maximo "pageCap" por pagina. */
    private void servePagedTracks(String albumId, int total, int pageCap) {
        server.on("GET", "/albums/" + albumId + "/tracks", request -> {
            int offset = Integer.parseInt(request.queryParam("offset"));
            int limit = Math.min(Integer.parseInt(request.queryParam("limit")), pageCap);
            int last = Math.min(total, offset + limit);
            JSONArray items = offset >= total ? new JSONArray() : SpotifyJson.tracks(offset + 1, last);
            return new Reply(200, SpotifyJson.page(items));
        });
    }

    private static void assertTracksAreOneToN(Album album, int n) {
        assertEquals(n, album.getTracks().size());
        for (int i = 0; i < n; i++) {
            assertEquals("t" + (i + 1), album.getTracks().get(i).getSpotifyId());   // ordem certa
        }
    }

    @Test
    public void albumWithAllTracksEmbedded_needsASingleRequest() throws Exception {
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Small", 10, SpotifyJson.tracks(1, 10)).toString());

        Album album = repository.findBySpotifyId("ALB");

        assertEquals("Small", album.getTitle());
        assertTracksAreOneToN(album, 10);
        assertEquals(1, server.requests().size());
    }

    @Test
    public void albumWith70Tracks_loadsTheMissingOnesFromTheNextPage() throws Exception {
        // O pedido do album so traz 40 faixas; as outras 30 vem de /albums/{id}/tracks
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Big", 70, SpotifyJson.tracks(1, 40)).toString());
        servePagedTracks("ALB", 70, 50);

        Album album = repository.findBySpotifyId("ALB");

        assertTracksAreOneToN(album, 70);
        assertEquals(2, server.requests().size());
        assertEquals("40", server.requests("GET", "/albums/ALB/tracks").get(0).queryParam("offset"));
    }

    @Test
    public void whenTheServerCapsThePageSize_keepsAskingUntilTheTotal() throws Exception {
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Big", 70, SpotifyJson.tracks(1, 40)).toString());
        servePagedTracks("ALB", 70, 20);   // so da 20 de cada vez

        Album album = repository.findBySpotifyId("ALB");

        assertTracksAreOneToN(album, 70);
        assertEquals(2, server.requests("GET", "/albums/ALB/tracks").size());   // offsets 40 e 60
    }

    @Test
    public void albumWithoutEmbeddedTracks_loadsEverythingFromPages() throws Exception {
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "NoEmbed", 55, null).toString());
        servePagedTracks("ALB", 55, 50);

        Album album = repository.findBySpotifyId("ALB");

        assertTracksAreOneToN(album, 55);
        assertEquals("0", server.requests("GET", "/albums/ALB/tracks").get(0).queryParam("offset"));
    }

    @Test
    public void nullEntriesInTheFirstPage_stillCountForTheNextOffset() throws Exception {
        // 38 faixas reais + 2 entradas null = 40 recebidas, por isso a proxima pagina comeca no offset 40
        JSONArray embedded = SpotifyJson.tracks(1, 38).put(JSONObject.NULL).put(JSONObject.NULL);
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Holes", 70, embedded).toString());
        servePagedTracks("ALB", 70, 50);

        Album album = repository.findBySpotifyId("ALB");

        assertEquals("40", server.requests("GET", "/albums/ALB/tracks").get(0).queryParam("offset"));
        Set<String> ids = new HashSet<>();
        for (Track t : album.getTracks()) assertTrue("repetida: " + t.getSpotifyId(), ids.add(t.getSpotifyId()));
    }

    @Test
    public void tracksRepeatedBetweenPages_areNotDuplicated() throws Exception {
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Overlap", 60, SpotifyJson.tracks(1, 40)).toString());
        // a pagina comeca antes do sitio certo e repete t36..t40
        server.on("GET", "/albums/ALB/tracks", request ->
                new Reply(200, SpotifyJson.page(SpotifyJson.tracks(36, 60))));

        Album album = repository.findBySpotifyId("ALB");

        assertTracksAreOneToN(album, 60);
    }

    @Test
    public void whenAPageComesBackEmpty_stopsInsteadOfLoopingForever() throws Exception {
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Liar", 100, SpotifyJson.tracks(1, 40)).toString());
        server.reply("GET", "/albums/ALB/tracks", 200, "{\"items\":[]}");

        Album album = repository.findBySpotifyId("ALB");

        assertEquals(40, album.getTracks().size());
        assertEquals(2, server.requests().size());
    }

    @Test
    public void multiDiscAlbum_keepsDiscNumbersAndTrackArtists() throws Exception {
        JSONArray tracks = new JSONArray()
                .put(SpotifyJson.track("a1", "Disc1 Song", 1, 1, SpotifyJson.artist("x", "Guest")))
                .put(SpotifyJson.track("b1", "Disc2 Song", 1, 2, SpotifyJson.artist("x", "Guest")));
        server.reply("GET", "/albums/ALB", 200, SpotifyJson.album("ALB", "Two Discs", 2, tracks).toString());

        List<Track> result = repository.findBySpotifyId("ALB").getTracks();

        assertEquals(1, result.get(0).getDiscNumber());
        assertEquals(2, result.get(1).getDiscNumber());
        assertEquals(1, result.get(1).getTrackNumber());
        assertEquals("Guest", result.get(1).getArtists().get(0).getName());
    }

    @Test
    public void unknownAlbum_throwsTheApiError() {
        server.reply("GET", "/albums/NOPE", 404, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class,
                () -> repository.findBySpotifyId("NOPE"));

        assertEquals(404, e.getStatus());
    }
}
