package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Leitura da resposta da Cloud Vision (so o JSON; a chamada HTTP nao e testada aqui). */
public class GoogleCloudVisionServiceParsingTest {

    private static final String ID_A = "1111111111111111111111";
    private static final String ID_B = "2222222222222222222222";
    private static final String ID_C = "3333333333333333333333";

    private static String ocrOnly(String text) {
        return "{\"responses\":[{\"fullTextAnnotation\":{\"text\":\"" + text + "\"}}]}";
    }

    private static String pagesWithUrls(String... urls) {
        StringBuilder pages = new StringBuilder();
        for (String url : urls) {
            if (pages.length() > 0) pages.append(',');
            pages.append("{\"url\":\"").append(url).append("\"}");
        }
        return "{\"responses\":[{\"webDetection\":{\"pagesWithMatchingImages\":[" + pages + "]}}]}";
    }

    /** Resposta completa de uma foto da capa de um album dos Daft Punk. */
    private static final String FULL_RESPONSE = "{\"responses\":[{"
            + "\"fullTextAnnotation\":{\"text\":\"DAFT PUNK\\nRANDOM ACCESS MEMORIES\"},"
            + "\"webDetection\":{"
            + "\"bestGuessLabels\":[{\"label\":\"daft punk homework album cover\"}],"
            + "\"webEntities\":["
            + "{\"description\":\"Daft Punk\",\"score\":0.9},"
            + "{\"description\":\"Poster\",\"score\":0.8},"        // generico
            + "{\"description\":\"Discovery\",\"score\":0.1},"     // pontuacao baixa
            + "{\"description\":\"Alive 2007\",\"score\":0.7},"
            + "{\"description\":\"Third Entity\",\"score\":0.6}],"  // ja passou o limite de 2 entidades
            + "\"pagesWithMatchingImages\":["
            + "{\"url\":\"https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa\","
            + "\"pageTitle\":\"Random Access Memories - Album by Daft Punk | Spotify\"},"
            + "{\"url\":\"https://example.com/blog\",\"pageTitle\":\"My blog about music\"},"   // nao e um site de musica
            + "{\"url\":\"https://www.discogs.com/release/1\",\"pageTitle\":\"Daft Punk - Discovery (2001, CD)\"},"
            + "{\"url\":\"https://music.apple.com/x\",\"pageTitle\":\"Homework by Daft Punk on Apple Music\"}],"
            + "\"fullMatchingImages\":[{\"url\":\"https://i.scdn.co/image/ab67616d0000b273aabbccddeeff001122334455\"}]"
            + "}}]}";

    // ----------------------------------------------------------- parseSearchQueries

    @Test
    public void parseSearchQueries_emptyOrBrokenInput_givesNoQueries() {
        assertTrue(GoogleCloudVisionService.parseSearchQueries(null).isEmpty());
        assertTrue(GoogleCloudVisionService.parseSearchQueries("").isEmpty());
        assertTrue(GoogleCloudVisionService.parseSearchQueries("   ").isEmpty());
        assertTrue(GoogleCloudVisionService.parseSearchQueries("isto nao e json").isEmpty());
        assertTrue(GoogleCloudVisionService.parseSearchQueries("{}").isEmpty());
        assertTrue(GoogleCloudVisionService.parseSearchQueries("{\"responses\":[]}").isEmpty());
    }

    @Test
    public void parseSearchQueries_orderIsMostReliableFirst() {
        List<String> queries = GoogleCloudVisionService.parseSearchQueries(FULL_RESPONSE);

        assertEquals(Arrays.asList(
                "daft punk homework",                // 1. melhor palpite (sem "album cover")
                "DAFT PUNK RANDOM ACCESS MEMORIES",  // 2. texto da capa
                "Random Access Memories Daft Punk",  // 3. titulo da pagina do Spotify
                "Daft Punk Discovery",               //    titulo da pagina do Discogs (maximo 2 paginas)
                "Daft Punk",                         // 4. entidades com boa pontuacao (maximo 2)
                "Alive 2007"), queries);
    }

    @Test
    public void parseSearchQueries_neverReturnsGenericLabelsOrDuplicates() {
        String json = "{\"responses\":[{\"webDetection\":{"
                + "\"bestGuessLabels\":[{\"label\":\"Poster\"}],"
                + "\"webEntities\":[{\"description\":\"Daft Punk\",\"score\":0.9},"
                + "{\"description\":\"DAFT PUNK\",\"score\":0.8},"
                + "{\"description\":\"Font\",\"score\":0.9}]}}]}";

        assertEquals(Collections.singletonList("Daft Punk"), GoogleCloudVisionService.parseSearchQueries(json));
    }

    @Test
    public void parseSearchQueries_ignoresOcrNoiseFromCoversWithoutText() {
        assertTrue(GoogleCloudVisionService.parseSearchQueries(ocrOnly("I")).isEmpty());
        assertTrue(GoogleCloudVisionService.parseSearchQueries(ocrOnly("l1")).isEmpty());
    }

    @Test
    public void parseSearchQueries_longOcrIsCutAndItsStartIsTriedToo() {
        List<String> queries = GoogleCloudVisionService.parseSearchQueries(
                ocrOnly("a1 b2 c3 d4 e5 f6 g7 h8 i9 j10"));

        assertEquals(Arrays.asList("a1 b2 c3 d4 e5 f6 g7 h8", "a1 b2 c3 d4"), queries);
    }

    @Test
    public void parseSearchQueries_shortOcrIsNotRepeated() {
        assertEquals(Collections.singletonList("Nevermind"),
                GoogleCloudVisionService.parseSearchQueries(ocrOnly("Nevermind")));
    }

    // -------------------------------------------------------- parseSpotifyAlbumIds

    @Test
    public void parseSpotifyAlbumIds_mostRepeatedFirst_maxTwo() {
        String json = pagesWithUrls(
                "https://open.spotify.com/album/" + ID_B,
                "https://open.spotify.com/album/" + ID_A + "?si=1",
                "https://open.spotify.com/album/" + ID_C,
                "https://open.spotify.com/intl-pt/album/" + ID_A,
                "https://open.spotify.com/track/" + ID_C,            // faixa: nao conta
                "https://example.com/album/" + ID_C);                // outro site: nao conta

        assertEquals(Arrays.asList(ID_A, ID_B), GoogleCloudVisionService.parseSpotifyAlbumIds(json));
    }

    @Test
    public void parseSpotifyAlbumIds_noPagesOrBrokenInput_givesNothing() {
        assertTrue(GoogleCloudVisionService.parseSpotifyAlbumIds(null).isEmpty());
        assertTrue(GoogleCloudVisionService.parseSpotifyAlbumIds("nao e json").isEmpty());
        assertTrue(GoogleCloudVisionService.parseSpotifyAlbumIds("{\"responses\":[{}]}").isEmpty());
        assertTrue(GoogleCloudVisionService.parseSpotifyAlbumIds(ocrOnly("Nevermind")).isEmpty());
    }

    // ------------------------------------------------------------- parseCoverKeys

    @Test
    public void parseCoverKeys_collectsKeysFromEveryPlaceTheyAppear() {
        String json = "{\"responses\":[{\"webDetection\":{"
                + "\"fullMatchingImages\":[{\"url\":\"https://i.scdn.co/image/ab67616d0000b273aabbccddeeff001122334455\"}],"
                + "\"partialMatchingImages\":[{\"url\":\"https://i.scdn.co/image/AB67616D00001E02FFEEDDCCBBAA998877665544\"}],"
                + "\"pagesWithMatchingImages\":[{\"url\":\"https://x\","
                + "\"fullMatchingImages\":[{\"url\":\"https://i.scdn.co/image/ab67616d00004851000011112222333344445555\"}],"
                + "\"partialMatchingImages\":[{\"url\":\"https://example.com/not-spotify.jpg\"}]}]"
                + "}}]}";

        Set<String> keys = GoogleCloudVisionService.parseCoverKeys(json);

        assertEquals(new HashSet<>(Arrays.asList(
                "aabbccddeeff001122334455", "ffeeddccbbaa998877665544", "000011112222333344445555")), keys);
    }

    @Test
    public void parseCoverKeys_noImagesOrBrokenInput_givesNothing() {
        assertTrue(GoogleCloudVisionService.parseCoverKeys(null).isEmpty());
        assertTrue(GoogleCloudVisionService.parseCoverKeys("nao e json").isEmpty());
        assertTrue(GoogleCloudVisionService.parseCoverKeys("{\"responses\":[{}]}").isEmpty());
    }

    // ----------------------------------------------------------- describeResponse

    @Test
    public void describeResponse_summarisesTheResponseForTheLogcat() {
        String summary = GoogleCloudVisionService.describeResponse(FULL_RESPONSE);

        assertTrue(summary, summary.contains("bestGuess=daft punk homework album cover"));
        assertTrue(summary, summary.contains("pages=4"));
        assertTrue(summary, summary.contains("fullMatchingImages=1 partial=0"));
    }

    @Test
    public void describeResponse_emptyResponse() {
        assertEquals("(vazia)", GoogleCloudVisionService.describeResponse("{}"));
        assertEquals("(vazia)", GoogleCloudVisionService.describeResponse(null));
    }
}
