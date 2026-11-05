package com.example.albumsnap.model;

/** Playlist da conta Spotify do utilizador (so o necessario para a escolher numa lista). */
public class Playlist {

    private final String spotifyId;
    private final String name;

    public Playlist(String spotifyId, String name) {
        this.spotifyId = spotifyId;
        this.name = name != null ? name : "";
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public String getName() {
        return name;
    }
}
