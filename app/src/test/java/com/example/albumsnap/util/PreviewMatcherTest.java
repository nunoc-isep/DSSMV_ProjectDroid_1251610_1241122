package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.PreviewQuery;

import org.junit.Test;

import java.util.Arrays;

/** A mesma gravacao, e nao outra versao, e que deve dar a preview. */
public class PreviewMatcherTest {

    private static PreviewQuery query(String title, long durationMs, String... artists) {
        return new PreviewQuery("", title, Arrays.asList(artists), "Discovery", durationMs);
    }

    @Test
    public void sameSong_matches() {
        PreviewQuery q = query("One More Time", 320_000, "Daft Punk");
        assertTrue(PreviewMatcher.isMatch(q, "One More Time", "Daft Punk", 320_000));
    }

    @Test
    public void remasterSuffixAccentsAndCase_areIgnored() {
        PreviewQuery q = query("Café Del Mar (Remastered 2011)", 200_000, "Energy 52");
        assertTrue(PreviewMatcher.isMatch(q, "CAFE DEL MAR", "Energy 52", 200_000));
    }

    @Test
    public void differentDuration_isRejected() {
        // versao ao vivo, 8 s mais longa
        PreviewQuery q = query("One More Time", 320_000, "Daft Punk");
        assertFalse(PreviewMatcher.isMatch(q, "One More Time", "Daft Punk", 328_000));
    }

    @Test
    public void smallDurationDifference_isAccepted() {
        PreviewQuery q = query("One More Time", 320_000, "Daft Punk");
        assertTrue(PreviewMatcher.isMatch(q, "One More Time", "Daft Punk", 323_000));
    }

    @Test
    public void unknownDuration_exactTitle_isAccepted() {
        PreviewQuery q = query("One More Time", 0, "Daft Punk");
        assertTrue(PreviewMatcher.isMatch(q, "One More Time", "Daft Punk", 0));
    }

    @Test
    public void differentArtist_isRejected() {
        PreviewQuery q = query("Home", 200_000, "Daft Punk");
        assertFalse(PreviewMatcher.isMatch(q, "Home", "Michael Buble", 200_000));
    }

    @Test
    public void anyCreditedArtist_isAccepted() {
        PreviewQuery q = query("Get Lucky", 369_000, "Daft Punk", "Pharrell Williams");
        assertTrue(PreviewMatcher.isMatch(q, "Get Lucky", "Pharrell Williams", 369_000));
    }

    @Test
    public void featuringInCandidateArtist_isAccepted() {
        PreviewQuery q = query("Get Lucky", 369_000, "Daft Punk");
        assertTrue(PreviewMatcher.isMatch(q, "Get Lucky", "Daft Punk feat. Pharrell Williams", 369_000));
    }

    @Test
    public void partialTitle_needsWholeWordsAndDuration() {
        PreviewQuery q = query("Home", 200_000, "Artist");
        assertFalse(PreviewMatcher.isMatch(q, "Homeless", "Artist", 200_000));           // nao e a palavra inteira
        assertFalse(PreviewMatcher.isMatch(q, "Home Sweet Home", "Artist", 0));          // sem duracao nao chega
        assertTrue(PreviewMatcher.isMatch(q, "Home Sweet Home", "Artist", 201_000));     // duracao confirma
    }

    @Test
    public void unknownWantedArtist_isNotAHurdle() {
        PreviewQuery q = new PreviewQuery("", "Intro", null, "", 60_000);
        assertTrue(PreviewMatcher.isMatch(q, "Intro", "Qualquer Um", 60_000));
    }

    @Test
    public void sameAlbum_winsTheTie() {
        PreviewQuery q = query("One More Time", 320_000, "Daft Punk");
        int sameAlbum = PreviewMatcher.score(q, "One More Time", "Daft Punk", "Discovery", 320_000);
        int otherAlbum = PreviewMatcher.score(q, "One More Time", "Daft Punk", "Alive 2007", 320_000);
        assertTrue(sameAlbum > otherAlbum);
        assertTrue(otherAlbum > 0);
    }

    @Test
    public void cleanTitle_removesVersionNoise() {
        assertEquals("Song", PreviewMatcher.cleanTitle("Song (feat. Someone)"));
        assertEquals("Song", PreviewMatcher.cleanTitle("Song - 2011 Remaster"));
        assertEquals("Song", PreviewMatcher.cleanTitle("Song - Live"));
        assertEquals("(Only Parentheses)", PreviewMatcher.cleanTitle("(Only Parentheses)"));
    }
}
