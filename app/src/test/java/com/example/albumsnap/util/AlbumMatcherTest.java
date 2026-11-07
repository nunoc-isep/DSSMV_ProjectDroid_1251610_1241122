package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Artist;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AlbumMatcherTest {

    private static final double DELTA = 1e-9;
    private static final List<String> RAM_QUERY = Arrays.asList("Random Access Memories Daft Punk");

    private static Album album(String id, String title, String artist) {
        return new Album(id, title, "2013", 10, "album", "",
                Arrays.asList(new Artist("a", artist)), null);
    }

    // ----------------------------------------------------------------- tokens

    @Test
    public void tokens_dropAccentsPunctuationAndEditionWords() {
        Set<String> tokens = AlbumMatcher.tokens("The Dark Side of the Moon (Remastered)");

        assertEquals(new java.util.HashSet<>(Arrays.asList("dark", "side", "moon")), tokens);
        assertTrue(AlbumMatcher.tokens("Beyoncé").contains("beyonce"));
        assertTrue(AlbumMatcher.tokens(null).isEmpty());
    }

    // ------------------------------------------------------------------ score

    @Test
    public void score_isOneForAnExactTitleAndArtistMatch() {
        assertEquals(1.0, AlbumMatcher.score(album("1", "Random Access Memories", "Daft Punk"), RAM_QUERY), DELTA);
    }

    @Test
    public void score_isZeroWithoutUsableTitleOrQueries() {
        assertEquals(0.0, AlbumMatcher.score(album("1", null, "Daft Punk"), RAM_QUERY), DELTA);
        assertEquals(0.0, AlbumMatcher.score(album("1", "The", "Daft Punk"), RAM_QUERY), DELTA);   // so palavras ignoradas
        assertEquals(0.0, AlbumMatcher.score(album("1", "Homework", "Daft Punk"), new ArrayList<String>()), DELTA);
        assertEquals(0.0, AlbumMatcher.score(album("1", "Homework", "Daft Punk"), Arrays.asList("", "  ")), DELTA);
    }

    @Test
    public void score_laterQueriesAreWorthLess() {
        Album album = album("1", "Random Access Memories", "Daft Punk");

        double first = AlbumMatcher.score(album, Arrays.asList("Random Access Memories Daft Punk"));
        double second = AlbumMatcher.score(album, Arrays.asList("nada a ver", "Random Access Memories Daft Punk"));
        double tenth = AlbumMatcher.score(album, queriesWithMatchAtIndex(10));

        assertEquals(1.0, first, DELTA);
        assertEquals(0.92, second, DELTA);   // 1 - 0.08
        assertEquals(0.6, tenth, DELTA);     // nunca desce de 0.6
    }

    private static List<String> queriesWithMatchAtIndex(int index) {
        List<String> queries = new ArrayList<>(Collections.nCopies(index, "irrelevante"));
        queries.add("Random Access Memories Daft Punk");
        return queries;
    }

    @Test
    public void score_usesTheBestQuery() {
        Album album = album("1", "Random Access Memories", "Daft Punk");

        double score = AlbumMatcher.score(album, Arrays.asList("Random Access Memories Daft Punk", "outra coisa"));

        assertEquals(1.0, score, DELTA);
    }

    // ------------------------------------------------------------------- rank

    @Test
    public void rank_putsTheBestMatchFirstWithoutChangingTheInputList() {
        List<Album> input = new ArrayList<>(Arrays.asList(
                album("1", "Homework", "Daft Punk"),
                album("2", "Random Access Memories", "Daft Punk"),
                album("3", "Discovery", "Daft Punk")));

        List<Album> ranked = AlbumMatcher.rank(input, RAM_QUERY);

        assertEquals("2", ranked.get(0).getSpotifyId());
        assertEquals("1", input.get(0).getSpotifyId());   // original intacta
    }

    @Test
    public void rank_keepsSpotifyOrderOnTies() {
        List<Album> input = Arrays.asList(
                album("1", "Alpha", "X"), album("2", "Beta", "X"), album("3", "Gamma", "X"));

        List<Album> ranked = AlbumMatcher.rank(input, Arrays.asList("Nada"));

        assertSame(input.get(0), ranked.get(0));
        assertSame(input.get(1), ranked.get(1));
        assertSame(input.get(2), ranked.get(2));
    }

    // ------------------------------------------------------------- confidence

    @Test
    public void isConfident_falseForNoCandidates() {
        assertFalse(AlbumMatcher.isConfident(new ArrayList<Album>(), RAM_QUERY));
    }

    @Test
    public void isConfident_singleStrongCandidateIsEnough() {
        assertTrue(AlbumMatcher.isConfident(
                Arrays.asList(album("1", "Random Access Memories", "Daft Punk")), RAM_QUERY));
    }

    @Test
    public void isConfident_singleWeakCandidateIsNot() {
        assertFalse(AlbumMatcher.isConfident(
                Arrays.asList(album("1", "Homework", "Daft Punk")), RAM_QUERY));
    }

    @Test
    public void isConfident_needsAClearGapToTheSecondCandidate() {
        List<Album> clearWinner = Arrays.asList(
                album("1", "Random Access Memories", "Daft Punk"),
                album("2", "Homework", "Daft Punk"));
        List<Album> twoEditions = AlbumMatcher.rank(Arrays.asList(
                album("1", "Random Access Memories", "Daft Punk"),
                album("2", "Random Access Memories (Drumless Edition)", "Daft Punk")), RAM_QUERY);

        assertTrue(AlbumMatcher.isConfident(clearWinner, RAM_QUERY));
        assertFalse(AlbumMatcher.isConfident(twoEditions, RAM_QUERY));   // o utilizador escolhe
    }
}
