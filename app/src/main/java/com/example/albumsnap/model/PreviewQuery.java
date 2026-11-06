package com.example.albumsnap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** O que se sabe sobre uma faixa do Spotify para procurar a sua preview noutras fontes. */
public class PreviewQuery {

    private final String isrc;
    private final String title;
    private final List<String> artists;
    private final String albumTitle;
    private final long durationMs;

    /**
     * @param isrc       codigo unico da gravacao (pode ser vazio se o Spotify nao o der)
     * @param artists    artistas da faixa, o principal primeiro
     * @param durationMs duracao da faixa no Spotify (0 se desconhecida)
     */
    public PreviewQuery(String isrc, String title, List<String> artists, String albumTitle, long durationMs) {
        this.isrc = isrc != null ? isrc.trim() : "";
        this.title = title != null ? title : "";
        this.artists = artists != null
                ? Collections.unmodifiableList(new ArrayList<>(artists))
                : Collections.<String>emptyList();
        this.albumTitle = albumTitle != null ? albumTitle : "";
        this.durationMs = Math.max(durationMs, 0);
    }

    public String getIsrc() {
        return isrc;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getArtists() {
        return artists;
    }

    /** O primeiro artista, ou "" se nao houver nenhum. */
    public String getPrimaryArtist() {
        return artists.isEmpty() || artists.get(0) == null ? "" : artists.get(0);
    }

    public String getAlbumTitle() {
        return albumTitle;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
