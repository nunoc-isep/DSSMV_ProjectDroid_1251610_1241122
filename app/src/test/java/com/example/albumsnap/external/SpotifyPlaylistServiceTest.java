package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.example.albumsnap.model.Playlist;
import com.example.albumsnap.testutil.FakeHttpServer;
import com.example.albumsnap.testutil.FakeHttpServer.Reply;
import com.example.albumsnap.testutil.FakeSpotifyAuth;
import com.example.albumsnap.testutil.SpotifyJson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/** Listar as playlists onde se pode adicionar e adicionar-lhes uma faixa. */
public class SpotifyPlaylistServiceTest {

    private FakeHttpServer server;
    private SpotifyPlaylistService service;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        service = new SpotifyPlaylistService(new SpotifyApiClient(new FakeSpotifyAuth(), server.baseUrl()));
        server.reply("GET", "/me", 200, "{\"id\":\"me\"}");
    }

    @After
    public void tearDown() {
        server.close();
    }

    private static JSONObject playlist(String id, String name, String ownerId, boolean collaborative) {
        try {
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("collaborative", collaborative)
                    .put("owner", new JSONObject().put("id", ownerId));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String page(JSONArray items, boolean hasNext) {
        try {
            JSONObject page = new JSONObject().put("items", items);
            page.put("next", hasNext ? "https://api.spotify.com/v1/next" : JSONObject.NULL);
            return page.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    // -------------------------------------------------------------- listing

    @Test
    public void getEditablePlaylists_keepsOwnedAndCollaborativeOnes() throws Exception {
        JSONArray items = new JSONArray()
                .put(playlist("mine", "Minha", "me", false))
                .put(playlist("followed", "De outro", "someone", false))     // so a segue: nao pode editar
                .put(playlist("shared", "Partilhada", "someone", true))      // colaborativa: pode
                .put(JSONObject.NULL)                                        // o Spotify pode devolver null
                .put(new JSONObject().put("name", "sem id").put("owner", new JSONObject().put("id", "me")));
        server.reply("GET", "/me/playlists", 200, page(items, false));

        List<Playlist> result = service.getEditablePlaylists();

        assertEquals(2, result.size());
        assertEquals("mine", result.get(0).getSpotifyId());
        assertEquals("Minha", result.get(0).getName());
        assertEquals("shared", result.get(1).getSpotifyId());
    }

    @Test
    public void getEditablePlaylists_noPlaylists_returnsAnEmptyList() throws Exception {
        server.reply("GET", "/me/playlists", 200, page(new JSONArray(), false));

        assertEquals(0, service.getEditablePlaylists().size());
    }

    @Test
    public void getEditablePlaylists_readsEveryPageUntilThereIsNoNext() throws Exception {
        server.on("GET", "/me/playlists", request -> {
            int offset = Integer.parseInt(request.queryParam("offset"));
            JSONArray items = new JSONArray();
            int count = offset == 0 ? 50 : 3;   // pagina cheia, depois uma curta
            for (int i = 0; i < count; i++) items.put(playlist("p" + (offset + i), "P", "me", false));
            return new Reply(200, page(items, offset == 0));
        });

        List<Playlist> result = service.getEditablePlaylists();

        assertEquals(53, result.size());
        assertEquals(2, server.requests("GET", "/me/playlists").size());
        assertEquals("50", server.requests("GET", "/me/playlists").get(1).queryParam("offset"));
    }

    @Test
    public void getEditablePlaylists_stopsOnAShortPageEvenIfNextIsSet() throws Exception {
        JSONArray items = new JSONArray().put(playlist("only", "Unica", "me", false));
        server.reply("GET", "/me/playlists", 200, page(items, true));

        service.getEditablePlaylists();

        assertEquals(1, server.requests("GET", "/me/playlists").size());
    }

    @Test
    public void getEditablePlaylists_aJsonNullNameBecomesAnEmptyName() throws Exception {
        JSONObject unnamed = playlist("p1", "x", "me", false).put("name", JSONObject.NULL);
        server.reply("GET", "/me/playlists", 200, page(new JSONArray().put(unnamed), false));

        assertEquals("", service.getEditablePlaylists().get(0).getName());   // e nao o texto "null"
    }

    @Test
    public void getEditablePlaylists_withoutPermission_throwsTheApiError() {
        server.reply("GET", "/me/playlists", 403, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class,
                () -> service.getEditablePlaylists());

        assertEquals(403, e.getStatus());
    }

    // ------------------------------------------------------------ add a track

    @Test
    public void addTrack_postsTheTrackUriToThePlaylist() throws Exception {
        server.reply("POST", "/playlists/PL1/items", 201, "{\"snapshot_id\":\"s\"}");

        service.addTrack("PL1", "TRACK9");

        FakeHttpServer.RecordedRequest request = server.requests().get(0);
        assertEquals("POST", request.method);
        assertEquals("spotify:track:TRACK9", new JSONObject(request.body).getJSONArray("uris").getString(0));
    }

    @Test
    public void addTrack_toAPlaylistTheUserCannotEdit_throwsTheApiError() {
        server.reply("POST", "/playlists/PL1/items", 403, "{}");

        SpotifyApiClient.ApiException e = assertThrows(SpotifyApiClient.ApiException.class,
                () -> service.addTrack("PL1", "T"));

        assertEquals(403, e.getStatus());
    }
}
