package com.example.albumsnap.external;

import com.example.albumsnap.services.FavouriteTrackService;

import java.io.IOException;

/**
 * Adiciona uma faixa aos favoritos do Spotify (PUT /me/library com o URI da faixa).
 * Precisa do scope user-library-modify (ja esta em SpotifyAuthManager.SCOPES).
 */
public class SpotifyFavouriteTrackService implements FavouriteTrackService {

    private final SpotifyApiClient client;

    public SpotifyFavouriteTrackService(SpotifyApiClient client) {
        this.client = client;
    }

    @Override
    public void addTrack(String spotifyId) throws IOException {
        client.saveToLibrary("spotify:track:" + spotifyId);
    }
}
