package com.example.albumsnap.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrackTest {

    @Test
    public void shortConstructor_defaultsToDiscOneAndNoArtists() {
        Track track = new Track("t1", "Song", 180_000, 3);

        assertEquals("t1", track.getSpotifyId());
        assertEquals("Song", track.getTitle());
        assertEquals(180_000, track.getDurationMs());
        assertEquals(3, track.getTrackNumber());
        assertEquals(1, track.getDiscNumber());
        assertTrue(track.getArtists().isEmpty());
    }

    @Test
    public void discNumberBelowOne_becomesOne() {
        assertEquals(1, new Track("t", "S", 0, 1, 0, null).getDiscNumber());
        assertEquals(1, new Track("t", "S", 0, 1, -4, null).getDiscNumber());
        assertEquals(2, new Track("t", "S", 0, 1, 2, null).getDiscNumber());
    }

    @Test
    public void artists_areCopiedAndReadOnly() {
        List<Artist> source = new ArrayList<>(Arrays.asList(new Artist("a1", "A"), new Artist("a2", "B")));
        Track track = new Track("t", "S", 0, 1, 1, source);

        source.clear();   // mudar a lista original nao afeta a faixa

        assertEquals(2, track.getArtists().size());
        assertThrows(UnsupportedOperationException.class, () -> track.getArtists().add(new Artist("x", "X")));
    }
}
