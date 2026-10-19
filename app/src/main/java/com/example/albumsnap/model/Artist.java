package com.example.albumsnap.model;

/** Artista (domain_model.puml). */
public class Artist {

    private final String spotifyId;
    private final String name;

    public Artist(String spotifyId, String name) {
        this.spotifyId = spotifyId;
        this.name = name;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public String getName() {
        return name;
    }
}
