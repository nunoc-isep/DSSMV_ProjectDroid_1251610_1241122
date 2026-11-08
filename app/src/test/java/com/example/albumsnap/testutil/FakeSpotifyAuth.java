package com.example.albumsnap.testutil;

import com.example.albumsnap.services.SpotifyAuthService;

import java.io.IOException;

/** Login falso: devolve sempre o mesmo token, sem rede nem Android. */
public class FakeSpotifyAuth implements SpotifyAuthService {

    public static final String TOKEN = "test-token";
    public static final String REFRESHED_TOKEN = "refreshed-token";

    /** Quantas vezes o cliente pediu uma renovacao forcada do token. */
    public int refreshCalls;

    /** Se definido, a renovacao falha com este erro (ex.: sessao revogada). */
    public IOException refreshFailure;

    @Override
    public void authorize() {
    }

    @Override
    public String getAccessToken() {
        return TOKEN;
    }

    @Override
    public String refreshAccessToken() throws IOException {
        refreshCalls++;
        if (refreshFailure != null) throw refreshFailure;
        return REFRESHED_TOKEN;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void disconnect() {
    }
}
