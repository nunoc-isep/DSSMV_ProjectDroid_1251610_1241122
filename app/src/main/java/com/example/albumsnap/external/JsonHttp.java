package com.example.albumsnap.external;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** GET simples que devolve JSON, para as APIs publicas sem login (Deezer, iTunes). */
final class JsonHttp {

    private JsonHttp() {
    }

    static JSONObject get(String address) throws IOException {
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
                throw new IOException("HTTP " + status + " em " + conn.getURL().getHost());
            }
            try {
                return new JSONObject(text);
            } catch (JSONException e) {
                throw new IOException("Resposta invalida de " + conn.getURL().getHost(), e);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Texto de um campo, sem o "null" que o org.json devolve para valores JSON null. */
    static String text(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key, "");
    }

    /** As previews vem por vezes em http: o Android bloqueia trafego sem HTTPS. */
    static String secure(String url) {
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
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
