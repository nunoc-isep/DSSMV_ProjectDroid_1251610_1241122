package com.example.albumsnap.views;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.albumsnap.MainActivity;
import com.example.albumsnap.R;
import com.example.albumsnap.external.SpotifyAuthManager;

/** LoginView do diagrama de classes. */
public class LoginActivity extends AppCompatActivity {

    private SpotifyAuthManager auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = SpotifyAuthManager.getInstance(this);

        if (auth.isAuthenticated()) {
            showConnectionStatus(true);
            return;
        }

        displayLogin();
        handleIntent(getIntent());
    }

    // launchMode="singleTask": o redirect do Spotify chega aqui se a Activity ja existir
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void displayLogin() {
        setContentView(R.layout.activity_login);
        findViewById(R.id.btnLogin).setOnClickListener(v -> connectSpotify());
    }

    private void connectSpotify() {
        auth.authorize();
    }

    private void showConnectionStatus(boolean connected) {
        if (connected) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Nao ligado ao Spotify", Toast.LENGTH_LONG).show();
        }
    }

    private void handleIntent(Intent intent) {
        Uri data = intent.getData();
        if (data == null) return;

        boolean handled = auth.handleRedirect(data, new SpotifyAuthManager.LoginCallback() {
            @Override
            public void onSuccess() {
                showConnectionStatus(true);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        if (handled) {
            intent.setData(null);   // evita processar o mesmo redirect numa rotacao do ecra
        }
    }
}