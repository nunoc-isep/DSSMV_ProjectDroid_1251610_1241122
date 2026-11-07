package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.testutil.FakeHttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

/** Previews da Deezer: primeiro pelo ISRC, depois por pesquisa so com a mesma gravacao. */
public class DeezerPreviewServiceTest {

    private static final String ISRC = "USUM71703861";
    private static final String NO_DATA = "{\"error\":{\"type\":\"DataException\",\"message\":\"no data\",\"code\":800}}";

    private FakeHttpServer server;
    private DeezerPreviewService service;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        service = new DeezerPreviewService(server.baseUrl());
    }

    @After
    public void tearDown() {
        server.close();
    }

    private static PreviewQuery query(String isrc) {
        return new PreviewQuery(isrc, "One More Time", Arrays.asList("Daft Punk"), "Discovery", 320_000);
    }

    /** Resultado de pesquisa da Deezer (a duracao e em segundos). */
    private static String result(String title, String artist, int durationSeconds, String preview) {
        return "{\"title\":\"" + title + "\",\"duration\":" + durationSeconds + ",\"preview\":\"" + preview
                + "\",\"artist\":{\"name\":\"" + artist + "\"},\"album\":{\"title\":\"Discovery\"}}";
    }

    private static String data(String... results) {
        return "{\"data\":[" + String.join(",", results) + "]}";
    }

    // ------------------------------------------------------------------ ISRC

    @Test
    public void withAnIsrc_usesThePreviewOfThatExactRecording() throws Exception {
        server.reply("GET", "/track/isrc:" + ISRC, 200, "{\"title\":\"x\",\"preview\":\"https://cdn.example/exact.mp3\"}");

        String url = service.findPreviewUrl(query(ISRC));

        assertEquals("https://cdn.example/exact.mp3", url);
        assertEquals(1, server.requests().size());   // nem chega a pesquisar
    }

    @Test
    public void withoutAnIsrc_goesStraightToTheSearch() throws Exception {
        server.reply("GET", "/search", 200, data(result("One More Time", "Daft Punk", 320, "https://cdn.example/a.mp3")));

        service.findPreviewUrl(query(""));

        assertEquals(0, server.requests("GET", "/track/isrc:").size());
        assertTrue(server.requests().get(0).path.equals("/search"));
    }

    @Test
    public void unknownIsrc_fallsBackToTheSearch() throws Exception {
        server.reply("GET", "/track/isrc:" + ISRC, 200, NO_DATA);   // a Deezer responde 200 com "error"
        server.reply("GET", "/search", 200, data(result("One More Time", "Daft Punk", 320, "https://cdn.example/s.mp3")));

        assertEquals("https://cdn.example/s.mp3", service.findPreviewUrl(query(ISRC)));
        assertEquals("/track/isrc:" + ISRC, server.requests().get(0).path);
        assertEquals("/search", server.requests().get(1).path);
    }

    @Test
    public void isrcTrackWithoutPreview_fallsBackToTheSearch() throws Exception {
        server.reply("GET", "/track/isrc:" + ISRC, 200, "{\"title\":\"x\",\"preview\":\"\"}");
        server.reply("GET", "/search", 200, data(result("One More Time", "Daft Punk", 320, "https://cdn.example/s.mp3")));

        assertEquals("https://cdn.example/s.mp3", service.findPreviewUrl(query(ISRC)));
    }

    // ---------------------------------------------------------------- search

    @Test
    public void search_rejectsOtherVersionsAndOtherArtists() throws Exception {
        server.reply("GET", "/search", 200, data(
                result("One More Time", "Some Cover Band", 320, "https://cdn.example/cover.mp3"),        // outro artista
                result("One More Time - Live", "Daft Punk", 345, "https://cdn.example/live.mp3"),        // 25 s mais longa
                result("Another Song", "Daft Punk", 320, "https://cdn.example/other.mp3"),               // outro titulo
                result("One More Time", "Daft Punk", 321, "https://cdn.example/right.mp3")));            // a certa

        assertEquals("https://cdn.example/right.mp3", service.findPreviewUrl(query("")));
    }

    @Test
    public void search_withNoCorrectRecording_returnsNullAfterBothSearches() throws Exception {
        server.reply("GET", "/search", 200, data(result("One More Time", "Some Cover Band", 320, "https://cdn.example/x.mp3")));

        assertNull(service.findPreviewUrl(query("")));
        assertEquals(2, server.requests("GET", "/search").size());   // com campos e livre
    }

    @Test
    public void search_ignoresResultsWithoutPreview() throws Exception {
        server.reply("GET", "/search", 200, data(result("One More Time", "Daft Punk", 320, "")));

        assertNull(service.findPreviewUrl(query("")));
    }

    @Test
    public void search_sendsTheArtistAndTheCleanedTitle() throws Exception {
        server.reply("GET", "/search", 200, data());
        PreviewQuery remaster = new PreviewQuery("", "One More Time (Remastered 2011)",
                Arrays.asList("Daft Punk"), "Discovery", 320_000);

        service.findPreviewUrl(remaster);

        FakeHttpServer.RecordedRequest first = server.requests("GET", "/search").get(0);
        assertEquals("artist:\"Daft Punk\" track:\"One More Time\"", first.queryParam("q"));
        FakeHttpServer.RecordedRequest second = server.requests("GET", "/search").get(1);
        assertEquals("Daft Punk One More Time", second.queryParam("q"));
    }

    @Test
    public void emptyTitle_returnsNullWithoutAnyRequest() throws Exception {
        PreviewQuery noTitle = new PreviewQuery("", "", Arrays.asList("Daft Punk"), "", 0);

        assertNull(service.findPreviewUrl(noTitle));
        assertEquals(0, server.requests().size());
    }

    @Test
    public void previewOverPlainHttp_isUpgradedToHttps() throws Exception {
        server.reply("GET", "/search", 200, data(result("One More Time", "Daft Punk", 320, "http://cdn.example/a.mp3")));

        assertEquals("https://cdn.example/a.mp3", service.findPreviewUrl(query("")));
    }

    // ---------------------------------------------------------------- errors

    @Test
    public void quotaError_isReportedAsAnIoException() {
        server.reply("GET", "/search", 200, "{\"error\":{\"type\":\"Exception\",\"message\":\"Quota limit exceeded\",\"code\":4}}");

        assertThrows(IOException.class, () -> service.findPreviewUrl(query("")));
    }

    @Test
    public void serverError_isReportedAsAnIoException() {
        server.reply("GET", "/search", 500, "{}");

        assertThrows(IOException.class, () -> service.findPreviewUrl(query("")));
    }

    @Test
    public void invalidJson_isReportedAsAnIoException() {
        server.reply("GET", "/search", 200, "<html>nao e json</html>");

        assertThrows(IOException.class, () -> service.findPreviewUrl(query("")));
    }
}
