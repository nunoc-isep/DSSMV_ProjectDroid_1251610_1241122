package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchQueryTest {

    // ------------------------------------------------------------------ clean

    @Test
    public void clean_removesPrintedNoiseAndLineBreaks() {
        assertEquals("DAFT PUNK Random Access Memories",
                SearchQuery.clean("DAFT PUNK\nRandom Access Memories\nParental Advisory"));
    }

    @Test
    public void clean_removesGoogleLabelSuffixAndPunctuation() {
        assertEquals("Daft Punk Random Access Memories",
                SearchQuery.clean("Daft Punk - Random Access Memories (album cover)"));
        assertEquals("daft punk", SearchQuery.clean("daft punk album cover"));
    }

    @Test
    public void clean_keepsAccentsAndApostrophes() {
        assertEquals("Guns N' Roses", SearchQuery.clean("Guns N' Roses"));
        assertEquals("Beyoncé's Lemonade", SearchQuery.clean("Beyoncé’s Lemonade"));   // apostrofo curvo
    }

    @Test
    public void clean_nullAndNoiseOnly_giveEmpty() {
        assertEquals("", SearchQuery.clean(null));
        assertEquals("", SearchQuery.clean("Explicit Content"));
        assertEquals("", SearchQuery.clean("  \n  "));
    }

    // ---------------------------------------------------------------- generic

    @Test
    public void isGeneric_trueForLabelsThatSayNothingAboutTheAlbum() {
        String[] generic = {"Poster", "Font", "Logo", "Soundtrack", "Compilation", "Music", "CD",
                "Vinyl", "Record", "Album art", "Graphic design", "Illustration", "Text"};
        for (String label : generic) {
            assertTrue(label, SearchQuery.isGeneric(SearchQuery.clean(label)));
        }
    }

    @Test
    public void isGeneric_falseForRealNamesThatContainGenericWords() {
        String[] names = {"Art Blakey", "Earth, Wind & Fire", "Fontaines D.C.", "Heart", "Smart",
                "Music Box", "Martin Garrix"};
        for (String name : names) {
            assertFalse(name, SearchQuery.isGeneric(SearchQuery.clean(name)));
        }
    }

    @Test
    public void isGeneric_nullAndEmptyAreGeneric() {
        assertTrue(SearchQuery.isGeneric(null));
        assertTrue(SearchQuery.isGeneric(""));
    }

    // ----------------------------------------------------------- OCR "noise"

    @Test
    public void isMeaningfulText_rejectsOcrNoise() {
        assertFalse(SearchQuery.isMeaningfulText(null));
        assertFalse(SearchQuery.isMeaningfulText(""));
        assertFalse(SearchQuery.isMeaningfulText("I"));
        assertFalse(SearchQuery.isMeaningfulText("AA"));      // uma so palavra de 2 letras
        assertFalse(SearchQuery.isMeaningfulText("a b"));
    }

    @Test
    public void isMeaningfulText_acceptsRealText() {
        assertTrue(SearchQuery.isMeaningfulText("Nevermind"));
        assertTrue(SearchQuery.isMeaningfulText("Ok Go"));    // duas palavras de 2+ letras
        assertTrue(SearchQuery.isMeaningfulText("U2 XX"));
    }

    // ------------------------------------------------------------ Spotify URLs

    @Test
    public void spotifyAlbumId_findsTheIdInAlbumLinks() {
        String id = "4m2880jivSbbyEGAKfITCa";
        assertEquals(id, SearchQuery.spotifyAlbumId("https://open.spotify.com/album/" + id));
        assertEquals(id, SearchQuery.spotifyAlbumId("https://open.spotify.com/album/" + id + "?si=abc"));
        assertEquals(id, SearchQuery.spotifyAlbumId("https://open.spotify.com/intl-pt/album/" + id));
        assertEquals(id, SearchQuery.spotifyAlbumId("https://open.spotify.com/intl-pt-br/album/" + id));
        assertEquals(id, SearchQuery.spotifyAlbumId("HTTPS://OPEN.SPOTIFY.COM/album/" + id));
    }

    @Test
    public void spotifyAlbumId_ignoresOtherLinks() {
        assertNull(SearchQuery.spotifyAlbumId("https://open.spotify.com/track/4m2880jivSbbyEGAKfITCa"));
        assertNull(SearchQuery.spotifyAlbumId("https://open.spotify.com/album/short"));
        assertNull(SearchQuery.spotifyAlbumId("https://example.com/album/4m2880jivSbbyEGAKfITCa"));
        assertNull(SearchQuery.spotifyAlbumId(null));
    }

    @Test
    public void spotifyCoverKey_isTheSameForEverySizeOfTheSameImage() {
        String key = "aabbccddeeff001122334455";
        String large = "https://i.scdn.co/image/ab67616d0000b273" + key;
        String small = "https://i.scdn.co/image/ab67616d00004851" + key;

        assertEquals(key, SearchQuery.spotifyCoverKey(large));
        assertEquals(SearchQuery.spotifyCoverKey(large), SearchQuery.spotifyCoverKey(small));
    }

    @Test
    public void spotifyCoverKey_isLowerCasedAndIgnoresOtherImages() {
        assertEquals("aabbccddeeff001122334455",
                SearchQuery.spotifyCoverKey("https://i.scdn.co/image/AB67616D0000B273AABBCCDDEEFF001122334455"));
        assertNull(SearchQuery.spotifyCoverKey("https://example.com/cover.jpg"));
        assertNull(SearchQuery.spotifyCoverKey(null));
    }

    // ---------------------------------------------------------------- sites

    @Test
    public void isMusicSite_recognisesKnownSitesIgnoringCase() {
        assertTrue(SearchQuery.isMusicSite("https://open.spotify.com/album/x"));
        assertTrue(SearchQuery.isMusicSite("https://MUSIC.APPLE.COM/pt/album/x"));
        assertTrue(SearchQuery.isMusicSite("https://www.discogs.com/release/1"));
        assertFalse(SearchQuery.isMusicSite("https://example.com/shop"));
        assertFalse(SearchQuery.isMusicSite(null));
    }

    @Test
    public void fromPageTitle_turnsAPageTitleIntoASearch() {
        assertEquals("Random Access Memories Daft Punk",
                SearchQuery.fromPageTitle("Random Access Memories - Album by Daft Punk | Spotify"));
        assertEquals("Abbey Road The Beatles",
                SearchQuery.fromPageTitle("Abbey Road (2019 Mix) – The Beatles - Wikipedia"));
    }

    @Test
    public void fromPageTitle_withNothingUseful_isEmpty() {
        assertEquals("", SearchQuery.fromPageTitle(null));
        assertEquals("", SearchQuery.fromPageTitle("Discogs"));
        assertEquals("", SearchQuery.fromPageTitle("| Spotify"));
    }

    // ----------------------------------------------------------------- words

    @Test
    public void firstWords_keepsTheFirstNWords() {
        assertEquals("a b c", SearchQuery.firstWords("a b c d e", 3));
        assertEquals("a b", SearchQuery.firstWords("  a b  ", 5));
        assertEquals("", SearchQuery.firstWords(null, 3));
    }

    @Test
    public void wordCount_countsWordsSeparatedByAnySpaces() {
        assertEquals(0, SearchQuery.wordCount(null));
        assertEquals(0, SearchQuery.wordCount("   "));
        assertEquals(3, SearchQuery.wordCount(" a  b   c "));
    }
}
