package com.example.albumsnap;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.albumsnap.external.SpotifyAuthManager;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        testSpotifyLogin();
    }

    /**
     * TEMPORARIO: confirma que o token funciona pedindo o perfil (GET /v1/me).
     * Apagar quando existir o fluxo real da app.
     */
    private void testSpotifyLogin() {
        final TextView status = findViewById(R.id.textStatus);
        final SpotifyAuthManager auth = SpotifyAuthManager.getInstance(this);

        executor.execute(() -> {                      // rede fora da UI thread
            String message;
            try {
                String token = auth.getAccessToken();

                HttpURLConnection conn =
                        (HttpURLConnection) new URL("https://api.spotify.com/v1/me").openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String body = in == null ? "" : new Scanner(in, "UTF-8").useDelimiter("\\A").next();
                conn.disconnect();

                if (code >= 400) {
                    message = "Erro " + code + ": " + body;
                } else {
                    JSONObject json = new JSONObject(body);
                    message = "Ligado ao Spotify como: "
                            + json.optString("display_name", json.optString("id"));
                }
            } catch (Exception e) {
                message = "Falhou: " + e.getMessage();
            }

            final String result = message;
            runOnUiThread(() -> status.setText(result));   // atualizar a UI na UI thread
        });
    }
}