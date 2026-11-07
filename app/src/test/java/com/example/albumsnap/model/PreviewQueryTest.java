package com.example.albumsnap.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PreviewQueryTest {

    @Test
    public void nullFields_becomeEmptyValues() {
        PreviewQuery query = new PreviewQuery(null, null, null, null, 0);

        assertEquals("", query.getIsrc());
        assertEquals("", query.getTitle());
        assertEquals("", query.getAlbumTitle());
        assertTrue(query.getArtists().isEmpty());
        assertEquals("", query.getPrimaryArtist());
    }

    @Test
    public void isrc_isTrimmed() {
        assertEquals("USUM71703861", new PreviewQuery("  USUM71703861 ", "T", null, "", 0).getIsrc());
    }

    @Test
    public void negativeDuration_becomesZero() {
        assertEquals(0, new PreviewQuery("", "T", null, "", -5).getDurationMs());
        assertEquals(1234, new PreviewQuery("", "T", null, "", 1234).getDurationMs());
    }

    @Test
    public void primaryArtist_isTheFirstOne() {
        PreviewQuery query = new PreviewQuery("", "T", Arrays.asList("Daft Punk", "Pharrell Williams"), "", 0);

        assertEquals("Daft Punk", query.getPrimaryArtist());
    }

    @Test
    public void primaryArtist_withNullFirstEntry_isEmpty() {
        PreviewQuery query = new PreviewQuery("", "T", Arrays.asList((String) null, "Someone"), "", 0);

        assertEquals("", query.getPrimaryArtist());
    }

    @Test
    public void artists_areCopiedAndReadOnly() {
        List<String> source = new ArrayList<>(Arrays.asList("A", "B"));
        PreviewQuery query = new PreviewQuery("", "T", source, "", 0);

        source.clear();

        assertEquals(2, query.getArtists().size());
        assertThrows(UnsupportedOperationException.class, () -> query.getArtists().add("C"));
    }
}
