package com.example.albumsnap.external;

import com.example.albumsnap.services.TrackInfoService;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

/** GET /tracks/{id}: as faixas dentro de um album nao trazem o ISRC, so o pedido da faixa. */
public class SpotifyTrackInfoService implements TrackInfoService {

    private final SpotifyApiClient client;

    public SpotifyTrackInfoService(SpotifyApiClient client) {
        this.client = client;
    }

    @Override
    public String getIsrc(String trackSpotifyId) throws IOException {
        JSONObject json = client.get("/tracks/" + URLEncoder.encode(trackSpotifyId, "UTF-8"));
        JSONObject externalIds = json.optJSONObject("external_ids");
        if (externalIds == null || externalIds.isNull("isrc")) return "";
        return externalIds.optString("isrc", "");
    }
}
