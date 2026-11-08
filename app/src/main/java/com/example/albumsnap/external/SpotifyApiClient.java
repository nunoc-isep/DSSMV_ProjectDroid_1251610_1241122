package com.example.albumsnap.external;

import android.util.Log;

import com.example.albumsnap.services.SpotifyAuthService;
import com.example.albumsnap.util.ErrorMessages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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
    private final String baseUrl;

    public SpotifyApiClient(SpotifyAuthService auth) {
        this(auth, BASE_URL);
    }

    /** So para testes: aponta o cliente para um servidor local. */
    SpotifyApiClient(SpotifyAuthService auth, String baseUrl) {
        this.auth = auth;
        this.baseUrl = baseUrl;
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

    /** POST com corpo JSON (a resposta nao e usada). */
    public void post(String pathAndQuery, JSONObject body) throws IOException {
        byte[] bytes = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];
        request("POST", pathAndQuery, bytes);
    }

    /**
     * Guarda um item na biblioteca do utilizador (PUT /me/library) a partir do seu URI do Spotify,
     * p.ex. "spotify:album:..." ou "spotify:track:...". Forma documentada: URI no parametro "uris";
     * se der 400, tenta com os URIs num corpo JSON {"uris": [...]}.
     */
    public void saveToLibrary(String uri) throws IOException {
        try {
            put("/me/library?uris=" + URLEncoder.encode(uri, "UTF-8"), null);
        } catch (ApiException e) {
            if (e.getStatus() != 400) throw e;
            try {
                put("/me/library", new JSONObject().put("uris", new JSONArray().put(uri)));
            } catch (JSONException je) {
                throw new IOException(je.getMessage(), je);
            }
        }
    }

    /** PUT com corpo JSON opcional. */
    public void put(String pathAndQuery, JSONObject body) throws IOException {
        byte[] bytes = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : null;
        request("PUT", pathAndQuery, bytes);
    }

    private String request(String method, String pathAndQuery, byte[] body) throws IOException {
        try {
            return execute(method, pathAndQuery, body, auth.getAccessToken());
        } catch (ApiException e) {
            if (e.getStatus() != 401) throw e;
            // O Spotify recusou o token (por exemplo, o utilizador revogou o acesso). Tenta renovar
            // uma vez e repetir o pedido; se o refresh token tambem estiver morto, o AuthService
            // apaga a sessao e lanca SESSION_EXPIRED.
            Log.w("SpotifyApiClient", method + " " + pathAndQuery + " -> 401: a renovar o token");
            return execute(method, pathAndQuery, body, auth.refreshAccessToken());
        }
    }

    private String execute(String method, String pathAndQuery, byte[] body, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + pathAndQuery).openConnection();
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
