package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.testutil.FakeHttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

/** Previews do iTunes (segunda fonte): so a mesma gravacao. */
public class ItunesPreviewServiceTest {

    private FakeHttpServer server;
    private ItunesPreviewService service;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
        service = new ItunesPreviewService(server.baseUrl() + "/search");
    }

    @After
    public void tearDown() {
        server.close();
    }

    private static PreviewQuery query() {
        return new PreviewQuery("", "One More Time", Arrays.asList("Daft Punk"), "Discovery", 320_000);
    }

    private static String song(String name, String artist, String album, long millis, String preview) {
        return "{\"kind\":\"song\",\"trackName\":\"" + name + "\",\"artistName\":\"" + artist
                + "\",\"collectionName\":\"" + album + "\",\"trackTimeMillis\":" + millis
                + (preview == null ? "" : ",\"previewUrl\":\"" + preview + "\"") + "}";
    }

    private static String results(String... songs) {
        return "{\"resultCount\":" + songs.length + ",\"results\":[" + String.join(",", songs) + "]}";
    }

    @Test
    public void returnsThePreviewOfTheMatchingSong() throws Exception {
        server.reply("GET", "/search", 200, results(
                song("One More Time", "Daft Punk", "Discovery", 320_000, "https://audio.example/a.m4a")));

        assertEquals("https://audio.example/a.m4a", service.findPreviewUrl(query()));
    }

    @Test
    public void skipsOtherArtistsOtherVersionsAndSongsWithoutPreview() throws Exception {
        server.reply("GET", "/search", 200, results(
                song("One More Time", "Karaoke Stars", "Hits", 320_000, "https://audio.example/karaoke.m4a"),
                song("One More Time", "Daft Punk", "Alive 2007", 351_000, "https://audio.example/live.m4a"),
                song("One More Time", "Daft Punk", "Discovery", 320_000, null),
                song("One More Time", "Daft Punk", "Discovery", 321_500, "https://audio.example/right.m4a")));

        assertEquals("https://audio.example/right.m4a", service.findPreviewUrl(query()));
    }

    @Test
    public void whenTwoRecordingsMatch_prefersTheSameAlbum() throws Exception {
        server.reply("GET", "/search", 200, results(
                song("One More Time", "Daft Punk", "Greatest Hits", 320_000, "https://audio.example/hits.m4a"),
                song("One More Time", "Daft Punk", "Discovery", 320_000, "https://audio.example/discovery.m4a")));

        assertEquals("https://audio.example/discovery.m4a", service.findPreviewUrl(query()));
    }

    @Test
    public void noResults_returnsNull() throws Exception {
        server.reply("GET", "/search", 200, results());
        assertNull(service.findPreviewUrl(query()));
    }

    @Test
    public void responseWithoutResultsField_returnsNull() throws Exception {
        server.reply("GET", "/search", 200, "{\"resultCount\":0}");
        assertNull(service.findPreviewUrl(query()));
    }

    @Test
    public void searchesForTheArtistAndTheCleanedTitle() throws Exception {
        server.reply("GET", "/search", 200, results());
        PreviewQuery remaster = new PreviewQuery("", "One More Time (Remastered)", Arrays.asList("Daft Punk"), "", 0);

        service.findPreviewUrl(remaster);

        FakeHttpServer.RecordedRequest request = server.requests().get(0);
        assertEquals("Daft Punk One More Time", request.queryParam("term"));
        assertEquals("song", request.queryParam("entity"));
        assertEquals("music", request.queryParam("media"));
    }

    @Test
    public void emptyTitle_returnsNullWithoutAnyRequest() throws Exception {
        assertNull(service.findPreviewUrl(new PreviewQuery("", "", Arrays.asList("Daft Punk"), "", 0)));
        assertEquals(0, server.requests().size());
    }

    @Test
    public void previewOverPlainHttp_isUpgradedToHttps() throws Exception {
        server.reply("GET", "/search", 200, results(
                song("One More Time", "Daft Punk", "Discovery", 320_000, "http://audio.example/a.m4a")));

        assertEquals("https://audio.example/a.m4a", service.findPreviewUrl(query()));
    }

    @Test
    public void rateLimitOrServerError_isReportedAsAnIoException() {
        server.reply("GET", "/search", 403, "");

        assertThrows(IOException.class, () -> service.findPreviewUrl(query()));
    }

    @Test
    public void invalidJson_isReportedAsAnIoException() {
        server.reply("GET", "/search", 200, "isto nao e json");

        assertThrows(IOException.class, () -> service.findPreviewUrl(query()));
    }
}
