package com.example.albumsnap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Album (domain_model.puml) + capa, artistas e faixas. */
public class Album {

    private final String spotifyId;
    private final String title;
    private final String releaseDate;
    private final int totalTracks;
    private final String albumType;
    private final String coverUrl;
    private final List<Artist> artists;
    private List<Track> tracks;

    public Album(String spotifyId, String title, String releaseDate, int totalTracks,
                 String albumType, String coverUrl, List<Artist> artists, List<Track> tracks) {
        this.spotifyId = spotifyId;
        this.title = title;
        this.releaseDate = releaseDate;
        this.totalTracks = totalTracks;
        this.albumType = albumType;
        this.coverUrl = coverUrl;
        this.artists = artists != null ? new ArrayList<>(artists) : new ArrayList<Artist>();
        this.tracks = tracks != null ? new ArrayList<>(tracks) : new ArrayList<Track>();
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public String getTitle() {
        return title;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public int getTotalTracks() {
        return totalTracks;
    }

    public String getAlbumType() {
        return albumType;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public List<Artist> getArtists() {
        return Collections.unmodifiableList(artists);
    }

    public List<Track> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks != null ? new ArrayList<>(tracks) : new ArrayList<Track>();
    }

    /** "Artista 1, Artista 2". */
    public String getArtistNames() {
        StringBuilder sb = new StringBuilder();
        for (Artist artist : artists) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(artist.getName());
        }
        return sb.toString();
    }

    /** Ano de lancamento ("2019"), ou "" se nao existir. */
    public String getReleaseYear() {
        return releaseDate != null && releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
    }
}
