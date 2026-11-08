package com.example.albumsnap.testutil;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Artist;
import com.example.albumsnap.model.Track;

import java.util.Arrays;
import java.util.List;

/** Albuns e faixas simples para os testes. */
public final class TestAlbums {

    private TestAlbums() {
    }

    public static Album album(String id, String title, String artist) {
        return album(id, title, artist, "https://img.example/" + id);
    }

    public static Album album(String id, String title, String artist, String coverUrl) {
        return new Album(id, title, "2020-01-01", 10, "album", coverUrl,
                Arrays.asList(new Artist("a-" + artist, artist)), null);
    }

    /** Capa do Spotify (i.scdn.co) com a chave de 24 caracteres hexadecimais dada. */
    public static String spotifyCover(String key24, String sizePrefix16) {
        return "https://i.scdn.co/image/" + sizePrefix16 + key24;
    }

    public static Track track(String id, String title, long durationMs, String... artists) {
        Artist[] list = new Artist[artists.length];
        for (int i = 0; i < artists.length; i++) list[i] = new Artist("a" + i, artists[i]);
        List<Artist> artistList = Arrays.asList(list);
        return new Track(id, title, durationMs, 1, 1, artistList);
    }
}
