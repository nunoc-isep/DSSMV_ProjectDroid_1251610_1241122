package com.example.albumsnap.external;

import com.example.albumsnap.services.FavouriteAlbumService;

import java.io.IOException;

/**
 * Adiciona um album a biblioteca do Spotify (PUT /me/library com o URI do album).
 * Precisa do scope user-library-modify (ja esta em SpotifyAuthManager.SCOPES).
 */
public class SpotifyFavouriteAlbumService implements FavouriteAlbumService {

    private final SpotifyApiClient client;

    public SpotifyFavouriteAlbumService(SpotifyApiClient client) {
        this.client = client;
    }

    @Override
    public void addAlbum(String spotifyId) throws IOException {
        client.saveToLibrary("spotify:album:" + spotifyId);
    }
}
