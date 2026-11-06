package com.example.albumsnap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Faixa de um album (domain_model.puml). */
public class Track {

    private final String spotifyId;
    private final String title;
    private final long durationMs;
    private final int trackNumber;
    private final int discNumber;
    private final List<Artist> artists;

    public Track(String spotifyId, String title, long durationMs, int trackNumber) {
        this(spotifyId, title, durationMs, trackNumber, 1, null);
    }

    /**
     * @param discNumber disco do album a que a faixa pertence (comeca em 1)
     * @param artists    artistas da faixa (podem ser diferentes dos do album, p.ex. compilacoes)
     */
    public Track(String spotifyId, String title, long durationMs, int trackNumber,
                 int discNumber, List<Artist> artists) {
        this.spotifyId = spotifyId;
        this.title = title;
        this.durationMs = durationMs;
        this.trackNumber = trackNumber;
        this.discNumber = Math.max(discNumber, 1);
        this.artists = artists != null ? new ArrayList<>(artists) : new ArrayList<Artist>();
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

    public int getDiscNumber() {
        return discNumber;
    }

    public List<Artist> getArtists() {
        return Collections.unmodifiableList(artists);
    }
}
