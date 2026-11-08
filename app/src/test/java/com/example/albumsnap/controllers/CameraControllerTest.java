package com.example.albumsnap.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.R;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.services.AlbumSearchService;
import com.example.albumsnap.services.ImageRecognitionService;
import com.example.albumsnap.testutil.TestAlbums;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Do resultado da Cloud Vision ate aos albuns sugeridos: quantas pesquisas se fazem, quando se abre
 * o album sem perguntar, quando se mostra a lista e que erro se da em cada falha.
 */
public class CameraControllerTest {

    private static final String KEY = "aabbccddeeff001122334455";
    private static final String ID_A = "1111111111111111111111";

    // ------------------------------------------------------------------ fakes

    private static final class FakeVision implements ImageRecognitionService {
        String json;
        Exception error;
        VisionCallback callback;
        int calls;

        @Override
        public void analyzeImage(android.graphics.Bitmap bitmap, VisionCallback callback) {
            calls++;
            this.callback = callback;
            if (json != null) callback.onSuccess(json);
            else if (error != null) callback.onError(error);
            // se nao ha nada, fica a espera: o teste responde mais tarde com "callback"
        }
    }

    private static class FakeSearch implements AlbumSearchService {
        final Map<String, List<Album>> resultsByQuery = new HashMap<>();
        List<Album> defaultResults = new ArrayList<>();
        final Map<String, Album> details = new HashMap<>();
        final List<String> queriesAsked = Collections.synchronizedList(new ArrayList<String>());
        final List<String> idsAsked = Collections.synchronizedList(new ArrayList<String>());
        IOException searchFailure;
        CountDownLatch gate;                                  // se existir, a pesquisa espera por ele
        final CountDownLatch searchStarted = new CountDownLatch(1);

        @Override
        public List<Album> searchAlbums(String query) throws IOException {
            queriesAsked.add(query);
            searchStarted.countDown();
            if (gate != null) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (searchFailure != null) throw searchFailure;
            List<Album> results = resultsByQuery.get(query);
            return results != null ? results : defaultResults;
        }

        @Override
        public Album getAlbumDetails(String spotifyId) throws IOException {
            idsAsked.add(spotifyId);
            Album album = details.get(spotifyId);
            if (album == null) throw new IOException("sem detalhes de " + spotifyId);
            return album;
        }
    }

    /** Guarda tudo o que o controlador comunica; o teste espera pelo ultimo evento (album ou erro). */
    private static final class Recorder implements CameraController.CameraCallback {
        final List<Integer> statuses = Collections.synchronizedList(new ArrayList<Integer>());
        volatile List<Album> albums;
        volatile Integer errorRes;
        volatile String errorDetail;
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onStatusUpdate(int statusRes) {
            statuses.add(statusRes);
        }

        @Override
        public void onAlbumsRecognized(List<Album> albums) {
            this.albums = albums;
            finished.countDown();
        }

        @Override
        public void onError(int messageRes, String detail) {
            this.errorRes = messageRes;
            this.errorDetail = detail;
            finished.countDown();
        }

        Recorder await() throws InterruptedException {
            assertTrue("o controlador nao respondeu em 5 s", finished.await(5, TimeUnit.SECONDS));
            return this;
        }

        List<String> ids() {
            List<String> ids = new ArrayList<>();
            for (Album a : albums) ids.add(a.getSpotifyId());
            return ids;
        }
    }

    private FakeVision vision;
    private FakeSearch search;
    private CameraController controller;
    private Recorder recorder;

    @Before
    public void setUp() {
        vision = new FakeVision();
        search = new FakeSearch();
        controller = new CameraController(search, vision);
        recorder = new Recorder();
    }

    /** Resposta da Vision so com o "melhor palpite" (e opcionalmente uma imagem igual a da foto). */
    private static String guess(String label, String... fullMatchingImageUrls) {
        StringBuilder images = new StringBuilder();
        for (String url : fullMatchingImageUrls) {
            if (images.length() > 0) images.append(',');
            images.append("{\"url\":\"").append(url).append("\"}");
        }
        return "{\"responses\":[{\"webDetection\":{\"bestGuessLabels\":[{\"label\":\"" + label + "\"}]"
                + (images.length() > 0 ? ",\"fullMatchingImages\":[" + images + "]" : "") + "}}]}";
    }

    private Recorder scan(String visionJson) throws InterruptedException {
        vision.json = visionJson;
        controller.processCapturedImage(null, recorder);
        return recorder.await();
    }

    private static final Album RAM = TestAlbums.album("ram", "Random Access Memories", "Daft Punk");
    private static final Album HOMEWORK = TestAlbums.album("hw", "Homework", "Daft Punk");
    private static final Album DRUMLESS = TestAlbums.album("dl", "Random Access Memories (Drumless Edition)", "Daft Punk");
    private static final String RAM_GUESS = guess("random access memories daft punk album cover");

    // --------------------------------------------------------------- results

    @Test
    public void aClearWinner_isOpenedWithoutAsking() throws Exception {
        search.defaultResults = Arrays.asList(HOMEWORK, RAM);   // o certo nem vem em primeiro

        scan(RAM_GUESS);

        assertEquals(Arrays.asList("ram"), recorder.ids());
    }

    @Test
    public void twoSimilarEditions_areShownAsAListToChooseFrom() throws Exception {
        search.defaultResults = Arrays.asList(DRUMLESS, RAM);

        scan(RAM_GUESS);

        assertEquals(Arrays.asList("ram", "dl"), recorder.ids());   // melhor primeiro
    }

    @Test
    public void exactCoverImage_isOpenedWithoutAskingEvenWhenTheTextAloneWouldNotBeEnough() throws Exception {
        // O texto lido so diz "daft punk": nao chega para ter certeza. Mas a capa e exatamente a da foto.
        Album sameCover = TestAlbums.album("hw", "Homework", "Daft Punk",
                TestAlbums.spotifyCover(KEY, "ab67616d00001e02"));        // tamanho pequeno da capa
        search.defaultResults = Arrays.asList(DRUMLESS, sameCover);
        String largeCover = TestAlbums.spotifyCover(KEY, "ab67616d0000b273");   // tamanho grande na foto

        scan(guess("daft punk album cover", largeCover));

        assertEquals(Arrays.asList("hw"), recorder.ids());
    }

    @Test
    public void withoutAnExactCover_aWeakTextMatchIsShownAsAList() throws Exception {
        search.defaultResults = Arrays.asList(DRUMLESS, HOMEWORK);

        scan(guess("daft punk album cover"));

        assertEquals(2, recorder.albums.size());
    }

    @Test
    public void twoAlbumsWithTheSameCover_arePresentedAsAList() throws Exception {
        Album a = TestAlbums.album("a", "Random Access Memories", "Daft Punk", TestAlbums.spotifyCover(KEY, "ab67616d00001e02"));
        Album b = TestAlbums.album("b", "Random Access Memories (Deluxe)", "Daft Punk", TestAlbums.spotifyCover(KEY, "ab67616d00001e02"));
        search.defaultResults = Arrays.asList(a, b);

        scan(guess("random access memories daft punk album cover", TestAlbums.spotifyCover(KEY, "ab67616d0000b273")));

        assertEquals(2, recorder.albums.size());
    }

    @Test
    public void theSpotifyLinkFoundNextToTheCover_isTheFirstSuggestion() throws Exception {
        search.details.put(ID_A, RAM);
        search.defaultResults = Arrays.asList(HOMEWORK);
        String json = "{\"responses\":[{\"webDetection\":{\"bestGuessLabels\":[{\"label\":\"daft punk\"}],"
                + "\"pagesWithMatchingImages\":[{\"url\":\"https://open.spotify.com/album/" + ID_A + "\"}]}}]}";

        scan(json);

        assertEquals("ram", recorder.albums.get(0).getSpotifyId());
        assertEquals(Arrays.asList(ID_A), search.idsAsked);
    }

    @Test
    public void aLinkAlone_isEnoughToGiveTheAlbum() throws Exception {
        search.details.put(ID_A, RAM);
        String json = "{\"responses\":[{\"webDetection\":{\"pagesWithMatchingImages\":["
                + "{\"url\":\"https://open.spotify.com/album/" + ID_A + "\"}]}}]}";

        scan(json);

        assertEquals(Arrays.asList("ram"), recorder.ids());
        assertTrue(search.queriesAsked.isEmpty());   // sem texto nao ha pesquisa
    }

    @Test
    public void aBrokenLink_fallsBackToTheTextSearch() throws Exception {
        // getAlbumDetails falha para este id
        search.defaultResults = Arrays.asList(RAM);
        String json = "{\"responses\":[{\"webDetection\":{\"bestGuessLabels\":[{\"label\":\"random access memories daft punk\"}],"
                + "\"pagesWithMatchingImages\":[{\"url\":\"https://open.spotify.com/album/" + ID_A + "\"}]}}]}";

        scan(json);

        assertEquals(Arrays.asList("ram"), recorder.ids());
    }

    @Test
    public void anAlbumFoundByLinkAndBySearch_appearsOnlyOnce() throws Exception {
        search.details.put(ID_A, RAM);
        search.defaultResults = Arrays.asList(RAM, DRUMLESS);
        String json = "{\"responses\":[{\"webDetection\":{\"bestGuessLabels\":[{\"label\":\"random access memories daft punk\"}],"
                + "\"pagesWithMatchingImages\":[{\"url\":\"https://open.spotify.com/album/" + ID_A + "\"}]}}]}";

        scan(json);

        List<String> ids = recorder.ids();
        assertEquals(1, java.util.Collections.frequency(ids, "ram"));
    }

    // ------------------------------------------------------- amount of searches

    private static final String SIX_QUERIES = "{\"responses\":[{"
            + "\"fullTextAnnotation\":{\"text\":\"beta two\"},"
            + "\"webDetection\":{\"bestGuessLabels\":[{\"label\":\"alpha one\"}],"
            + "\"webEntities\":[{\"description\":\"epsilon five\",\"score\":0.9},{\"description\":\"zeta six\",\"score\":0.8}],"
            + "\"pagesWithMatchingImages\":["
            + "{\"url\":\"https://www.discogs.com/release/1\",\"pageTitle\":\"Gamma Three - Discogs\"},"
            + "{\"url\":\"https://www.discogs.com/release/2\",\"pageTitle\":\"Delta Four - Discogs\"}]}}]}";

    @Test
    public void theQueriesAreTriedFromTheMostReliableToTheLeast() throws Exception {
        scan(SIX_QUERIES);   // nenhum resultado: tenta ate ao maximo

        assertEquals(Arrays.asList("alpha one", "beta two", "Gamma Three", "Delta Four", "epsilon five"),
                search.queriesAsked);
    }

    @Test
    public void withoutAnyResult_itStopsAtFiveSearchesAndSaysNotFound() throws Exception {
        scan(SIX_QUERIES);

        assertEquals(5, search.queriesAsked.size());   // havia 6 pesquisas possiveis
        assertEquals(Integer.valueOf(R.string.error_album_not_found), recorder.errorRes);
        assertNull(recorder.errorDetail);
    }

    @Test
    public void onceThereAreResults_itStopsAfterThreeSearches() throws Exception {
        search.resultsByQuery.put("alpha one", Arrays.asList(RAM));

        scan(SIX_QUERIES);

        assertEquals(3, search.queriesAsked.size());
        assertNotNull(recorder.albums);
    }

    @Test
    public void ifTheFirstSearchesFindNothing_itKeepsLookingUntilTheyDo() throws Exception {
        search.resultsByQuery.put("Delta Four", Arrays.asList(RAM));   // so a 4.a pesquisa acerta

        scan(SIX_QUERIES);

        assertEquals(4, search.queriesAsked.size());
        assertNotNull(recorder.albums);
    }

    // --------------------------------------------------------- the menu is limited

    @Test
    public void theListNeverHasMoreThanTenAlbums_andEveryQueryKeepsItsBestResults() throws Exception {
        List<Album> strong = new ArrayList<>();
        for (int i = 1; i <= 10; i++) strong.add(TestAlbums.album("l" + i, "Daft Punk Live " + i, "Daft Punk"));
        List<Album> weak = Arrays.asList(
                TestAlbums.album("u1", "Unrelated One", "Nobody"), TestAlbums.album("u2", "Unrelated Two", "Nobody"));
        search.resultsByQuery.put("daft punk", strong);
        search.resultsByQuery.put("ZZZ YYY", weak);
        String json = "{\"responses\":[{\"fullTextAnnotation\":{\"text\":\"ZZZ YYY\"},"
                + "\"webDetection\":{\"bestGuessLabels\":[{\"label\":\"daft punk\"}]}}]}";

        scan(json);

        List<String> ids = recorder.ids();
        assertEquals(10, ids.size());
        assertTrue("a pesquisa fraca ficou sem lugares", ids.contains("u1") && ids.contains("u2"));
    }

    // ----------------------------------------------------------------- errors

    @Test
    public void whenVisionFails_theErrorCarriesTheReason() throws Exception {
        vision.error = new IOException("sem rede");

        scan(null);

        assertEquals(Integer.valueOf(R.string.error_analyze_image), recorder.errorRes);
        assertEquals("sem rede", recorder.errorDetail);
        assertTrue(search.queriesAsked.isEmpty());
    }

    @Test
    public void noTextAndNoLinks_isNotSearched() throws Exception {
        scan("{}");

        assertEquals(Integer.valueOf(R.string.error_no_text), recorder.errorRes);
        assertTrue(search.queriesAsked.isEmpty());
        assertEquals(Arrays.asList(R.string.status_analyzing_vision), recorder.statuses);
    }

    @Test
    public void aResponseThatIsNotJson_countsAsNoText() throws Exception {
        scan("isto nao e json");

        assertEquals(Integer.valueOf(R.string.error_no_text), recorder.errorRes);
    }

    @Test
    public void ifEverySearchFails_theErrorIsTheSpotifyOne() throws Exception {
        search.searchFailure = new IOException("rede caiu");

        scan(RAM_GUESS);

        assertEquals(Integer.valueOf(R.string.error_spotify_search), recorder.errorRes);
        assertEquals("rede caiu", recorder.errorDetail);
    }

    @Test
    public void ifOnlySomeSearchesFail_theOthersStillGiveResults() throws Exception {
        // a 1.a pesquisa falha, a 2.a funciona
        final List<Album> results = Arrays.asList(RAM);
        search = new FakeSearch() {
            @Override
            public List<Album> searchAlbums(String query) throws IOException {
                if (query.equals("alpha one")) throw new IOException("falhou so esta");
                return results;
            }
        };
        controller = new CameraController(search, vision);

        scan(SIX_QUERIES);

        assertNull(recorder.errorRes);
        assertEquals(Arrays.asList("ram"), recorder.ids());
    }

    // --------------------------------------------------------------- progress

    @Test
    public void statusUpdates_followTheSteps() throws Exception {
        search.defaultResults = Arrays.asList(RAM);

        scan(RAM_GUESS);

        assertEquals(Arrays.asList(R.string.status_analyzing_vision, R.string.status_searching_spotify),
                recorder.statuses);
    }

    // ----------------------------------------------------------------- cancel

    @Test
    public void cancelBeforeVisionAnswers_theAnswerIsIgnored() throws Exception {
        controller.processCapturedImage(null, recorder);   // a Vision fica a espera
        controller.cancel();

        vision.callback.onSuccess(RAM_GUESS);
        Thread.sleep(300);

        assertNull(recorder.albums);
        assertNull(recorder.errorRes);
        assertTrue(search.queriesAsked.isEmpty());
    }

    @Test
    public void cancelBeforeVisionFails_theErrorIsIgnoredToo() throws Exception {
        controller.processCapturedImage(null, recorder);
        controller.cancel();

        vision.callback.onError(new IOException("tarde demais"));

        assertNull(recorder.errorRes);
    }

    @Test
    public void cancelWhileSearching_nothingIsDelivered() throws Exception {
        search.defaultResults = Arrays.asList(RAM);
        search.gate = new CountDownLatch(1);
        vision.json = RAM_GUESS;

        controller.processCapturedImage(null, recorder);
        assertTrue(search.searchStarted.await(5, TimeUnit.SECONDS));
        controller.cancel();
        search.gate.countDown();
        Thread.sleep(300);

        assertNull(recorder.albums);
        assertNull(recorder.errorRes);
    }

    @Test
    public void processingAgainResetsTheCancelFlag() throws Exception {
        controller.cancel();
        search.defaultResults = Arrays.asList(RAM);

        scan(RAM_GUESS);   // um novo scan no mesmo controlador

        assertEquals(Arrays.asList("ram"), recorder.ids());
    }
}
