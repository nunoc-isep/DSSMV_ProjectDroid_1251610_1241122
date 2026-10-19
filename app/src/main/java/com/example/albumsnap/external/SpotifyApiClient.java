package com.example.albumsnap.external;

import com.example.albumsnap.services.SpotifyAuthService;

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
            throw new IOException("Unexpected response from Spotify.", e);
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
            throw new ApiException(status, messageFor(status, text));
        } finally {
            conn.disconnect();
        }
    }

    private static String messageFor(int status, String body) {
        switch (status) {
            case 401:
                return "Your Spotify session expired. Log out in Settings and log in again.";
            case 403:
                return "Spotify refused the request. Check that this account was added to the app "
                        + "in the Spotify Dashboard and that the permissions were granted.";
            case 404:
                return "Not found on Spotify.";
            case 429:
                return "Too many requests to Spotify. Try again in a moment.";
            default:
                String detail = "";
                try {
                    JSONObject error = new JSONObject(body).optJSONObject("error");
                    if (error != null) detail = ": " + error.optString("message", "");
                } catch (JSONException ignored) {
                    // corpo sem JSON
                }
                return "Spotify error " + status + detail;
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
