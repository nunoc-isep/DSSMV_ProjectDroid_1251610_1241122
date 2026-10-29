package com.example.albumsnap.external;

import com.example.albumsnap.services.PreviewService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Previews de 30 s atraves da API publica da Deezer (sem chave nem login).
 * Pesquisa por artista + titulo e escolhe o resultado cujo titulo coincide.
 */
public class DeezerPreviewService implements PreviewService {

    private static final String SEARCH_URL = "https://api.deezer.com/search";

    @Override
    public String findPreviewUrl(String artist, String title) throws IOException {
        String wanted = cleanTitle(title);
        if (wanted.isEmpty()) return null;

        String query = "track:\"" + wanted.replace('"', ' ') + "\"";
        if (artist != null && !artist.trim().isEmpty()) {
            query = "artist:\"" + artist.replace('"', ' ') + "\" " + query;
        }

        JSONObject json = get(SEARCH_URL + "?limit=10&q=" + URLEncoder.encode(query, "UTF-8"));
        JSONArray data = json.optJSONArray("data");
        if (data == null) return null;

        String wantedKey = normalize(wanted);
        String partial = null;   // melhor alternativa se nenhum titulo for igual
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String preview = item.optString("preview", "");
            if (preview.isEmpty()) continue;

            String key = normalize(cleanTitle(item.optString("title", "")));
            if (key.equals(wantedKey)) return secure(preview);
            if (partial == null && !key.isEmpty()
                    && (key.contains(wantedKey) || wantedKey.contains(key))) {
                partial = preview;
            }
        }
        return partial != null ? secure(partial) : null;
    }

    /** Tira sufixos como "- Remastered 2011" e parenteses, que variam entre servicos. */
    static String cleanTitle(String title) {
        if (title == null) return "";
        String cleaned = title.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "")
                .replaceAll("\\s+-\\s+.*$", "")
                .trim();
        return cleaned.isEmpty() ? title.trim() : cleaned;
    }

    /** Minusculas, sem acentos nem pontuacao, para comparar titulos. */
    static String normalize(String text) {
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    /** O Android bloqueia trafego http (cleartext) por omissao. */
    private static String secure(String url) {
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }

    private JSONObject get(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readAll(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Deezer error " + status);
            }
            try {
                JSONObject json = new JSONObject(text);
                // A Deezer responde 200 com {"error": {...}} em alguns casos (ex.: limite de pedidos)
                if (json.has("error")) {
                    throw new IOException("Deezer error: " + json.optJSONObject("error"));
                }
                return json;
            } catch (JSONException e) {
                throw new IOException("Unexpected response from Deezer.", e);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
