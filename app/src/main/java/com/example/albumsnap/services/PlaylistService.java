package com.example.albumsnap.services;

import com.example.albumsnap.model.Playlist;

import java.io.IOException;
import java.util.List;

/** Playlists da conta Spotify do utilizador. Bloqueante: usar fora da UI thread. */
public interface PlaylistService {

    /** Playlists onde o utilizador pode adicionar faixas (as suas e as colaborativas). */
    List<Playlist> getEditablePlaylists() throws IOException;

    void addTrack(String playlistId, String trackSpotifyId) throws IOException;
}
