package com.example.albumsnap.testutil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Constroi JSON no formato da Spotify Web API para os testes. */
public final class SpotifyJson {

    private SpotifyJson() {
    }

    public static JSONObject artist(String id, String name) {
        try {
            return new JSONObject().put("id", id).put("name", name);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Faixa simplificada, como vem dentro de um album. */
    public static JSONObject track(String id, String name, int trackNumber, int discNumber, JSONObject... artists) {
        try {
            JSONArray artistArray = new JSONArray();
            for (JSONObject a : artists) artistArray.put(a);
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("duration_ms", 200_000)
                    .put("track_number", trackNumber)
                    .put("disc_number", discNumber)
                    .put("artists", artistArray);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pagina de resultados: {"items": [...]} como texto JSON. */
    public static String page(JSONArray items) {
        try {
            return new JSONObject().put("items", items).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Faixas "t1".."tN" de um unico disco, do mesmo artista. */
    public static JSONArray tracks(int from, int toInclusive) {
        JSONArray array = new JSONArray();
        for (int n = from; n <= toInclusive; n++) {
            array.put(track("t" + n, "Song " + n, n, 1, artist("ar1", "The Artist")));
        }
        return array;
    }

    /**
     * Album completo. Se "embeddedTracks" for null, o JSON nao traz o objeto "tracks";
     * senao traz "tracks": {"total": totalTracks, "items": [...]}.
     */
    public static JSONObject album(String id, String name, int totalTracks, JSONArray embeddedTracks) {
        try {
            JSONObject album = new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("release_date", "2020-05-01")
                    .put("total_tracks", totalTracks)
                    .put("album_type", "album")
                    .put("images", new JSONArray()
                            .put(new JSONObject().put("url", "https://img.example/big.jpg"))
                            .put(new JSONObject().put("url", "https://img.example/small.jpg")))
                    .put("artists", new JSONArray().put(artist("ar1", "The Artist")));
            if (embeddedTracks != null) {
                album.put("tracks", new JSONObject().put("total", totalTracks).put("items", embeddedTracks));
            }
            return album;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
