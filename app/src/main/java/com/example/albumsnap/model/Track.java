package com.example.albumsnap.model;

/** Faixa de um album (domain_model.puml). */
public class Track {

    private final String spotifyId;
    private final String title;
    private final long durationMs;
    private final int trackNumber;

    public Track(String spotifyId, String title, long durationMs, int trackNumber) {
        this.spotifyId = spotifyId;
        this.title = title;
        this.durationMs = durationMs;
        this.trackNumber = trackNumber;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public String getTitle() {
        return title;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getTrackNumber() {
        return trackNumber;
    }
}
