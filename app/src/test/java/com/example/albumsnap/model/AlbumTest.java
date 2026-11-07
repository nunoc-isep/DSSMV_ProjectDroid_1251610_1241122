package com.example.albumsnap.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlbumTest {

    private static Album albumWithDate(String releaseDate) {
        return new Album("id", "Title", releaseDate, 10, "album", "url", null, null);
    }

    @Test
    public void artistNames_areJoinedWithCommas() {
        Album album = new Album("id", "T", "2020", 1, "album", "",
                Arrays.asList(new Artist("1", "Daft Punk"), new Artist("2", "Pharrell Williams")), null);

        assertEquals("Daft Punk, Pharrell Williams", album.getArtistNames());
    }

    @Test
    public void artistNames_withoutArtists_isEmpty() {
        assertEquals("", albumWithDate("2020").getArtistNames());
    }

    @Test
    public void releaseYear_isTheFirstFourCharacters() {
        assertEquals("2019", albumWithDate("2019-03-04").getReleaseYear());
        assertEquals("2019", albumWithDate("2019").getReleaseYear());
    }

    @Test
    public void releaseYear_isEmptyWhenMissingOrTooShort() {
        assertEquals("", albumWithDate("").getReleaseYear());
        assertEquals("", albumWithDate(null).getReleaseYear());
        assertEquals("", albumWithDate("19").getReleaseYear());
    }

    @Test
    public void nullListsBecomeEmptyLists() {
        Album album = albumWithDate("2020");

        assertTrue(album.getArtists().isEmpty());
        assertTrue(album.getTracks().isEmpty());
    }

    @Test
    public void setTracks_replacesTracksAndAcceptsNull() {
        Album album = albumWithDate("2020");

        album.setTracks(Arrays.asList(new Track("a", "A", 1, 1), new Track("b", "B", 1, 2)));
        assertEquals(2, album.getTracks().size());

        album.setTracks(null);
        assertTrue(album.getTracks().isEmpty());
    }

    @Test
    public void tracksAndArtists_areCopiedAndReadOnly() {
        List<Track> tracks = new ArrayList<>(Arrays.asList(new Track("a", "A", 1, 1)));
        Album album = new Album("id", "T", "2020", 1, "album", "", null, tracks);

        tracks.clear();

        assertEquals(1, album.getTracks().size());
        assertThrows(UnsupportedOperationException.class, () -> album.getTracks().clear());
        assertThrows(UnsupportedOperationException.class, () -> album.getArtists().add(new Artist("x", "X")));
    }
}
