package com.example.albumsnap.external;

import android.util.Log;

import com.example.albumsnap.services.SpotifyAuthService;
import com.example.albumsnap.util.ErrorMessages;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP minimo para a Spotify Web API (HttpURLConnection, como na T5).
 * Junta o token do SpotifyAuthService a cada pedido. Bloqueante: usar fora da UI thread.
 */
public class SpotifyApiClient {

    public static final String BASE_URL = "https://api.spotify.com/v1";

    /** Erro devolvido pela API, com o codigo HTTP. */
    public static class ApiException extends IOException {
        private final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    private final SpotifyAuthService auth;

    public SpotifyApiClient(SpotifyAuthService auth) {
        this.auth = auth;
    }

    /** GET; "pathAndQuery" comeca por "/" (ex.: "/albums/123"). */
    public JSONObject get(String pathAndQuery) throws IOException {
        String text = request("GET", pathAndQuery, null);
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new IOException(ErrorMessages.UNEXPECTED_RESPONSE, e);
        }
    }

    /** PUT com corpo JSON opcional. */
    public void put(String pathAndQuery, JSONObject body) throws IOException {
        byte[] bytes = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : null;
        request("PUT", pathAndQuery, bytes);
    }

    private String request(String method, String pathAndQuery, byte[] body) throws IOException {
        String token = auth.getAccessToken();

        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + pathAndQuery).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            } else if ("PUT".equals(method)) {
                // PUT sem corpo: o Spotify exige Content-Length: 0
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(0);
                conn.getOutputStream().close();
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readAll(stream);
            if (status >= 200 && status < 300) {
                return text;
            }
            Log.w("SpotifyApiClient", method + " " + pathAndQuery + " -> " + status + " " + text);
            throw new ApiException(status, messageFor(status));
        } finally {
            conn.disconnect();
        }
    }

    /** Codigo da mensagem (traduzida na UI com ErrorMessages.localize); o corpo do erro vai para o Logcat. */
    private static String messageFor(int status) {
        switch (status) {
            case 401:
                return ErrorMessages.SESSION_EXPIRED;
            case 403:
                return ErrorMessages.SPOTIFY_FORBIDDEN;
            case 404:
                return ErrorMessages.SPOTIFY_NOT_FOUND;
            case 429:
                return ErrorMessages.SPOTIFY_RATE_LIMIT;
            default:
                return ErrorMessages.spotifyHttp(status);
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
