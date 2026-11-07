package com.example.albumsnap.testutil;

import com.example.albumsnap.services.SpotifyAuthService;

/** Login falso: devolve sempre o mesmo token, sem rede nem Android. */
public class FakeSpotifyAuth implements SpotifyAuthService {

    public static final String TOKEN = "test-token";

    @Override
    public void authorize() {
    }

    @Override
    public String getAccessToken() {
        return TOKEN;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void disconnect() {
    }
}
