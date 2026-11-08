package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.testutil.FakeHttpServer;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/** GET simples de JSON usado pela Deezer e pelo iTunes. */
public class JsonHttpTest {

    private FakeHttpServer server;

    @Before
    public void setUp() throws IOException {
        server = new FakeHttpServer();
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void get_returnsTheParsedJson() throws Exception {
        server.reply("GET", "/x", 200, "{\"a\":1,\"b\":\"dois\"}");

        JSONObject json = JsonHttp.get(server.baseUrl() + "/x");

        assertEquals(1, json.getInt("a"));
        assertEquals("dois", json.getString("b"));
        assertEquals("application/json", server.requests().get(0).header("Accept"));
    }

    @Test
    public void get_keepsTheQueryString() throws Exception {
        server.reply("GET", "/search", 200, "{}");

        JsonHttp.get(server.baseUrl() + "/search?q=daft%20punk&limit=5");

        assertEquals("daft punk", server.requests().get(0).queryParam("q"));
        assertEquals("5", server.requests().get(0).queryParam("limit"));
    }

    @Test
    public void get_httpErrors_becomeIoExceptionsThatNameTheHostButNotTheUrl() {
        server.reply("GET", "/secret", 500, "{}");

        IOException e = assertThrows(IOException.class,
                () -> JsonHttp.get(server.baseUrl() + "/secret?key=SEGREDO"));

        assertTrue(e.getMessage(), e.getMessage().contains("HTTP 500"));
        assertTrue(e.getMessage(), e.getMessage().contains("127.0.0.1"));
        assertFalse("a mensagem nao deve revelar a chave do URL", e.getMessage().contains("SEGREDO"));
    }

    @Test
    public void get_notFoundAndForbidden_areErrorsToo() {
        server.reply("GET", "/a", 404, "{}");
        server.reply("GET", "/b", 403, "");

        assertThrows(IOException.class, () -> JsonHttp.get(server.baseUrl() + "/a"));
        assertThrows(IOException.class, () -> JsonHttp.get(server.baseUrl() + "/b"));
    }

    @Test
    public void get_aBodyThatIsNotJson_isAnIoException() {
        server.reply("GET", "/html", 200, "<html>nao e json</html>");
        server.reply("GET", "/empty", 200, "");

        assertThrows(IOException.class, () -> JsonHttp.get(server.baseUrl() + "/html"));
        assertThrows(IOException.class, () -> JsonHttp.get(server.baseUrl() + "/empty"));
    }

    @Test
    public void get_serverNotReachable_isAnIoException() throws Exception {
        String url = server.baseUrl() + "/x";
        server.close();   // ja nao ha ninguem a escutar

        assertThrows(IOException.class, () -> JsonHttp.get(url));
    }

    @Test
    public void text_isEmptyForMissingNullOrNullObject() throws Exception {
        JSONObject json = new JSONObject("{\"ok\":\"valor\",\"nulo\":null}");

        assertEquals("valor", JsonHttp.text(json, "ok"));
        assertEquals("", JsonHttp.text(json, "nulo"));       // e nao o texto "null"
        assertEquals("", JsonHttp.text(json, "falta"));
        assertEquals("", JsonHttp.text(null, "ok"));
    }

    @Test
    public void secure_upgradesOnlyPlainHttp() {
        assertEquals("https://cdn.example/a.mp3", JsonHttp.secure("http://cdn.example/a.mp3"));
        assertEquals("https://cdn.example/a.mp3", JsonHttp.secure("https://cdn.example/a.mp3"));
        assertEquals("ftp://x", JsonHttp.secure("ftp://x"));
    }
}
