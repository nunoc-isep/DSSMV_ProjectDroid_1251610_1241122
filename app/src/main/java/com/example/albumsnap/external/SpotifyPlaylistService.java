package com.example.albumsnap.external;

import com.example.albumsnap.model.Playlist;
import com.example.albumsnap.services.PlaylistService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Playlists do utilizador com a Spotify Web API:
 *  - GET  /me/playlists               (scopes playlist-read-private e playlist-read-collaborative)
 *  - POST /playlists/{id}/items       (scopes playlist-modify-public / playlist-modify-private)
 */
public class SpotifyPlaylistService implements PlaylistService {

    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;   // limite de seguranca: 500 playlists

    private final SpotifyApiClient client;

    public SpotifyPlaylistService(SpotifyApiClient client) {
        this.client = client;
    }

    @Override
    public List<Playlist> getEditablePlaylists() throws IOException {
        String userId = text(client.get("/me"), "id");

        List<Playlist> playlists = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            JSONObject json = client.get("/me/playlists?limit=" + PAGE_SIZE + "&offset=" + (page * PAGE_SIZE));
            JSONArray items = json.optJSONArray("items");
            if (items == null) break;

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);   // o Spotify pode devolver entradas null
                if (item == null) continue;

                String id = text(item, "id");
                if (id.isEmpty()) continue;

                // So se pode adicionar a playlists proprias ou colaborativas
                boolean collaborative = item.optBoolean("collaborative", false);
                boolean owned = userId.equals(text(item.optJSONObject("owner"), "id"));
                if (owned || collaborative) {
                    playlists.add(new Playlist(id, text(item, "name")));
                }
            }
            if (items.length() < PAGE_SIZE || json.isNull("next")) break;
        }
        return playlists;
    }

    @Override
    public void addTrack(String playlistId, String trackSpotifyId) throws IOException {
        try {
            JSONObject body = new JSONObject()
                    .put("uris", new JSONArray().put("spotify:track:" + trackSpotifyId));
            client.post("/playlists/" + URLEncoder.encode(playlistId, "UTF-8") + "/items", body);
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Campo de texto sem o "null" que o org.json devolve para valores JSON null. */
    private static String text(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key, "");
    }
}
