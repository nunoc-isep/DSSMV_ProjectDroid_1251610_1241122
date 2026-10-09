package com.example.albumsnap.external;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.example.albumsnap.services.SpotifyAuthService;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login com o Spotify via Authorization Code + PKCE (sem client secret).
 * Implementa SpotifyAuthService (ver docs/uml/class_diagram.puml).
 *
 * Fluxo:
 *  1. authorize()      -> abre o browser na pagina de login/consentimento do Spotify
 *  2. O Spotify redireciona para albumsnap://callback?code=...&state=...
 *  3. handleRedirect() -> troca o code por access_token + refresh_token (thread de fundo)
 *  4. getAccessToken() -> devolve o token; renova com o refresh_token se expirou
 */
public class SpotifyAuthManager implements SpotifyAuthService {

    public static final String CLIENT_ID = "9521c2b3b6d740fb900a04e9499089a9";

    // Tem de ser IGUAL (maiusculas, barras no fim, etc.) ao Redirect URI registado no Dashboard
    public static final String REDIRECT_URI = "albumsnap://callback";

    private static final String SCOPES =
            "playlist-modify-private playlist-modify-public user-library-modify";

    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";

    private static final String PREFS = "spotify_auth";
    private static final String KEY_VERIFIER = "code_verifier";
    private static final String KEY_STATE = "state";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static final String VERIFIER_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    public interface LoginCallback {
        void onSuccess();
        void onError(String message);
    }

    private static SpotifyAuthManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();

    private SpotifyAuthManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized SpotifyAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new SpotifyAuthManager(context);
        }
        return instance;
    }

    // ---------------------------------------------------------------- passo 1

    @Override
    public void authorize() {
        String verifier = randomString(64);          // 43..128 caracteres
        String challenge = challengeFor(verifier);   // BASE64URL(SHA256(verifier))
        String state = randomString(16);             // protege contra CSRF

        // Guardado em disco porque o Android pode matar a app enquanto o browser esta aberto
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply();

        Uri uri = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build();

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);   // intent implicito (T4)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);         // necessario fora de uma Activity
        appContext.startActivity(intent);
    }

    // ---------------------------------------------------------------- passo 3

    /** Chamar com o Uri que chegou a Activity. Devolve true se era o nosso redirect. */
    public boolean handleRedirect(Uri data, final LoginCallback callback) {
        if (data == null
                || !"albumsnap".equals(data.getScheme())
                || !"callback".equals(data.getHost())) {
            return false;
        }

        String error = data.getQueryParameter("error");
        if (error != null) {
            callback.onError("Login cancelado ou recusado: " + error);
            return true;
        }

        final String code = data.getQueryParameter("code");
        String state = data.getQueryParameter("state");
        final String verifier = prefs.getString(KEY_VERIFIER, null);
        String expectedState = prefs.getString(KEY_STATE, null);

        if (code == null || verifier == null || expectedState == null || !expectedState.equals(state)) {
            callback.onError("Resposta de login invalida. Tenta outra vez.");
            return true;
        }
        prefs.edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply();

        executor.execute(new Runnable() {          // rede nunca na UI thread
            @Override
            public void run() {
                try {
                    Map<String, String> form = new LinkedHashMap<>();
                    form.put("grant_type", "authorization_code");
                    form.put("code", code);
                    form.put("redirect_uri", REDIRECT_URI);
                    form.put("client_id", CLIENT_ID);
                    form.put("code_verifier", verifier);
                    saveTokens(postForm(TOKEN_URL, form));
                    postSuccess(callback);
                } catch (Exception e) {
                    postError(callback, "Falha ao obter o token: " + e.getMessage());
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------- usar o token

    @Override
    public boolean isAuthenticated() {
        return prefs.getString(KEY_REFRESH, null) != null || prefs.getString(KEY_ACCESS, null) != null;
    }

    @Override
    public void disconnect() {
        prefs.edit().clear().apply();
    }

    @Override
    public synchronized String getAccessToken() throws IOException {
        String access = prefs.getString(KEY_ACCESS, null);
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        if (access != null && System.currentTimeMillis() < expiresAt) {
            return access;
        }
        return refreshAccessToken();
    }

    /** BLOQUEANTE (pedido de rede): so numa thread de fundo. */
    public synchronized String refreshAccessToken() throws IOException {
        String refresh = prefs.getString(KEY_REFRESH, null);
        if (refresh == null) {
            throw new IOException("Sem sessao ativa. Faz login outra vez.");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refresh);
        form.put("client_id", CLIENT_ID);
        try {
            saveTokens(postForm(TOKEN_URL, form));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
        return prefs.getString(KEY_ACCESS, null);
    }

    // ------------------------------------------------------------------ helpers

    private void saveTokens(JSONObject json) throws Exception {
        String access = json.getString("access_token");
        long expiresInSec = json.optLong("expires_in", 3600);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACCESS, access)
                .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expiresInSec - 60) * 1000L);
        // Na renovacao o Spotify pode nao devolver refresh_token novo: nesse caso mantem-se o antigo
        String newRefresh = json.optString("refresh_token", "");
        if (!newRefresh.isEmpty()) {
            editor.putString(KEY_REFRESH, newRefresh);
        }
        editor.apply();
    }

    private JSONObject postForm(String urlString, Map<String, String> form) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String response = readAll(stream);
            if (status >= 400) {
                throw new IOException("HTTP " + status + " " + response);
            }
            return new JSONObject(response);
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

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(VERIFIER_CHARS.charAt(random.nextInt(VERIFIER_CHARS.length())));
        }
        return sb.toString();
    }

    static String challengeFor(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void postSuccess(final LoginCallback cb) {
        mainHandler.post(new Runnable() { @Override public void run() { cb.onSuccess(); } });
    }

    private void postError(final LoginCallback cb, final String msg) {
        mainHandler.post(new Runnable() { @Override public void run() { cb.onError(msg); } });
    }
}