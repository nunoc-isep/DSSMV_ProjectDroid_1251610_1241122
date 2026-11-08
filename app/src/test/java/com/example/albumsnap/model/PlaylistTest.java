package com.example.albumsnap.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaylistTest {

    @Test
    public void keepsIdAndName() {
        Playlist playlist = new Playlist("p1", "Rock dos 90");

        assertEquals("p1", playlist.getSpotifyId());
        assertEquals("Rock dos 90", playlist.getName());
    }

    @Test
    public void nullName_becomesEmpty() {
        assertEquals("", new Playlist("p1", null).getName());
    }
}
