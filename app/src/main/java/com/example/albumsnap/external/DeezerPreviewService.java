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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Previews de 30s através da API pública da Deezer (sem chave nem login).
 */
public class DeezerPreviewService implements PreviewService {

    private static final String SEARCH_URL = "https://api.deezer.com/search";

    @Override
    public Map<String, String> fetchAlbumPreviews(String artist, String albumTitle) throws IOException {
        Map<String, String> previewsMap = new HashMap<>();

        String cleanedAlbum = cleanTitle(albumTitle);
        if (cleanedAlbum.isEmpty()) return previewsMap;

        String query = "album:\"" + cleanedAlbum.replace('"', ' ') + "\"";
        if (artist != null && !artist.trim().isEmpty()) {
            query = "artist:\"" + artist.replace('"', ' ') + "\" " + query;
        }

        String requestUrl = SEARCH_URL + "?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=50";
        JSONObject json = get(requestUrl);

        JSONArray data = json.optJSONArray("data");
        if (data == null) return previewsMap;

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;

            String preview = item.optString("preview", "");
            String trackTitle = item.optString("title", "");

            if (!preview.isEmpty() && !trackTitle.isEmpty()) {
                String normalizedKey = normalize(cleanTitle(trackTitle));
                previewsMap.put(normalizedKey, secure(preview));
            }
        }

        return previewsMap;
    }

    @Override
    public String findPreviewUrl(String artist, String title) throws IOException {
        String wanted = cleanTitle(title);
        if (wanted.isEmpty()) return null;

        // 1ª Tentativa: Artista + Faixa
        String query = "track:\"" + wanted.replace('"', ' ') + "\"";
        if (artist != null && !artist.trim().isEmpty()) {
            query = "artist:\"" + artist.replace('"', ' ') + "\" " + query;
        }

        String url = searchDeezer(query, wanted);
        if (url != null) return url;

        // 2ª Tentativa (Fallback): Pesquisa simples sem aspas nem prefixos
        String fallbackQuery = artist + " " + wanted;
        return searchDeezer(fallbackQuery, wanted);
    }

    private String searchDeezer(String query, String wantedTitle) throws IOException {
        JSONObject json = get(SEARCH_URL + "?q=" + URLEncoder.encode(query, "UTF-8"));
        JSONArray data = json.optJSONArray("data");
        if (data == null) return null;

        String wantedKey = normalize(wantedTitle);
        String partialMatchUrl = null;

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;

            String preview = item.optString("preview", "");
            if (preview.isEmpty()) continue;

            String key = normalize(cleanTitle(item.optString("title", "")));

            // Correspondência exata
            if (key.equals(wantedKey)) {
                return secure(preview);
            }

            // Guardar correspondência parcial se não encontrar exata
            if (partialMatchUrl == null && !key.isEmpty() && (key.contains(wantedKey) || wantedKey.contains(key))) {
                partialMatchUrl = preview;
            }
        }

        return partialMatchUrl != null ? secure(partialMatchUrl) : null;
    }

    public static String cleanTitle(String title) {
        if (title == null) return "";
        String cleaned = title
                .replaceAll("(?i)\\s*\\(feat\\..*?\\)", "")        // Remove (feat. ...)
                .replaceAll("(?i)\\s*\\[feat\\..*?\\]", "")        // Remove [feat. ...]
                .replaceAll("(?i)\\s*-\\s*.*remaster.*$", "")     // Remove - Remastered
                .replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "")        // Remove o que estiver entre parênteses
                .replaceAll("\\s+-\\s+.*$", "")                    // Remove sufixos com travessão
                .trim();
        return cleaned.isEmpty() ? title.trim() : cleaned;
    }

    public static String normalize(String text) {
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

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