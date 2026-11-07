package com.example.albumsnap.external;

import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.services.PreviewService;
import com.example.albumsnap.util.PreviewMatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * Previews de 30 s da iTunes Search API da Apple (publica, sem chave nem login; cerca de 20
 * pedidos por minuto). Serve de segunda fonte quando a Deezer nao tem a faixa. Procura por
 * artista + titulo e so aceita a mesma gravacao (ver PreviewMatcher).
 */
public class ItunesPreviewService implements PreviewService {

    private static final String SEARCH_URL = "https://itunes.apple.com/search";
    private static final int SEARCH_LIMIT = 25;

    private final String searchUrl;

    public ItunesPreviewService() {
        this(SEARCH_URL);
    }

    /** So para testes: aponta o servico para um servidor local. */
    ItunesPreviewService(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    @Override
    public String findPreviewUrl(PreviewQuery query) throws IOException {
        String title = PreviewMatcher.cleanTitle(query.getTitle());
        if (title.isEmpty()) return null;

        String term = (query.getPrimaryArtist() + " " + title).trim();
        JSONObject json = JsonHttp.get(searchUrl + "?media=music&entity=song&limit=" + SEARCH_LIMIT
                + "&term=" + URLEncoder.encode(term, "UTF-8"));
        JSONArray results = json.optJSONArray("results");
        if (results == null) return null;

        String bestUrl = null;
        int bestScore = 0;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;

            String preview = JsonHttp.text(item, "previewUrl");
            if (preview.isEmpty()) continue;

            int score = PreviewMatcher.score(query,
                    JsonHttp.text(item, "trackName"),
                    JsonHttp.text(item, "artistName"),
                    JsonHttp.text(item, "collectionName"),
                    item.optLong("trackTimeMillis", 0));
            if (score > bestScore) {
                bestScore = score;
                bestUrl = preview;
            }
        }
        return bestUrl != null ? JsonHttp.secure(bestUrl) : null;
    }
}
