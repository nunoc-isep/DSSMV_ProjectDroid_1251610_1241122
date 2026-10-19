package com.example.albumsnap.external;

import com.example.albumsnap.services.FavouriteAlbumService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * Adiciona um album a biblioteca do Spotify (PUT /me/library com o URI do album).
 * Precisa do scope user-library-modify (ja esta em SpotifyAuthManager.SCOPES).
 */
public class SpotifyFavouriteAlbumService implements FavouriteAlbumService {

    private final SpotifyApiClient client;

    public SpotifyFavouriteAlbumService(SpotifyApiClient client) {
        this.client = client;
    }

    @Override
    public void addAlbum(String spotifyId) throws IOException {
        String uri = "spotify:album:" + spotifyId;
        try {
            // Forma documentada: URIs no parametro "uris" do URL
            client.put("/me/library?uris=" + URLEncoder.encode(uri, "UTF-8"), null);
        } catch (SpotifyApiClient.ApiException e) {
            if (e.getStatus() != 400) throw e;
            // Alternativa: URIs num corpo JSON {"uris": [...]}
            try {
                JSONObject body = new JSONObject().put("uris", new JSONArray().put(uri));
                client.put("/me/library", body);
            } catch (JSONException je) {
                throw new IOException(je.getMessage(), je);
            }
        }
    }
}
