package com.example.albumsnap.external;

import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.services.PreviewService;
import com.example.albumsnap.util.PreviewMatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * Previews de 30 s da API publica da Deezer (sem chave nem login).
 *
 * Ordem: 1) pelo ISRC, que identifica a gravacao exata (GET /track/isrc:CODIGO);
 * 2) pesquisa por artista + titulo; 3) pesquisa livre. Nas pesquisas so se aceita um resultado
 * com o mesmo titulo, o mesmo artista e uma duracao parecida (ver PreviewMatcher).
 */
public class DeezerPreviewService implements PreviewService {

    private static final String API = "https://api.deezer.com";
    private static final int SEARCH_LIMIT = 15;
    private static final int ERROR_QUOTA = 4;   // codigo de erro da Deezer: limite de pedidos

    private final String api;

    public DeezerPreviewService() {
        this(API);
    }

    /** So para testes: aponta o servico para um servidor local. */
    DeezerPreviewService(String api) {
        this.api = api;
    }

    @Override
    public String findPreviewUrl(PreviewQuery query) throws IOException {
        // 1. ISRC: a mesma gravacao, sem duvidas
        if (!query.getIsrc().isEmpty()) {
            JSONObject track = getOrNull(api + "/track/isrc:" + URLEncoder.encode(query.getIsrc(), "UTF-8"));
            String preview = JsonHttp.text(track, "preview");
            if (!preview.isEmpty()) return JsonHttp.secure(preview);
        }

        String title = PreviewMatcher.cleanTitle(query.getTitle());
        if (title.isEmpty()) return null;
        String artist = query.getPrimaryArtist();

        // 2. Pesquisa com campos
        String fielded = (artist.isEmpty() ? "" : "artist:\"" + artist.replace('"', ' ') + "\" ")
                + "track:\"" + title.replace('"', ' ') + "\"";
        String url = searchBest(fielded, query);
        if (url != null) return url;

        // 3. Pesquisa livre (para titulos com pontuacao que a pesquisa com campos nao apanha)
        return searchBest((artist + " " + title).trim(), query);
    }

    private String searchBest(String q, PreviewQuery query) throws IOException {
        JSONObject json = getOrNull(api + "/search?limit=" + SEARCH_LIMIT
                + "&q=" + URLEncoder.encode(q, "UTF-8"));
        JSONArray data = json != null ? json.optJSONArray("data") : null;
        if (data == null) return null;

        String bestUrl = null;
        int bestScore = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;

            String preview = JsonHttp.text(item, "preview");
            if (preview.isEmpty()) continue;

            int score = PreviewMatcher.score(query,
                    JsonHttp.text(item, "title"),
                    JsonHttp.text(item.optJSONObject("artist"), "name"),
                    JsonHttp.text(item.optJSONObject("album"), "title"),
                    item.optLong("duration", 0) * 1000L);   // a Deezer da a duracao em segundos
            if (score > bestScore) {
                bestScore = score;
                bestUrl = preview;
            }
        }
        return bestUrl != null ? JsonHttp.secure(bestUrl) : null;
    }

    /** GET; devolve null quando a Deezer responde "sem dados" (ex.: ISRC que nao conhece). */
    private JSONObject getOrNull(String address) throws IOException {
        JSONObject json = JsonHttp.get(address);
        JSONObject error = json.optJSONObject("error");
        if (error != null) {
            if (error.optInt("code", 0) == ERROR_QUOTA) {
                throw new IOException("Deezer: limite de pedidos excedido");
            }
            return null;
        }
        return json;
    }
}
