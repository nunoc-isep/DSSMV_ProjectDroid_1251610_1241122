package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Partes do ErrorMessages que nao precisam de Android. A traducao em si (que precisa de um
 * Context real) e testada em ErrorMessagesInstrumentedTest.
 */
public class ErrorMessagesTest {

    @Test
    public void spotifyHttp_putsTheStatusAfterThePrefix() {
        assertEquals("error.spotify_http:500", ErrorMessages.spotifyHttp(500));
        assertEquals(ErrorMessages.SPOTIFY_HTTP_PREFIX + 502, ErrorMessages.spotifyHttp(502));
    }

    @Test
    public void everyCode_isDifferent() throws Exception {
        Set<String> codes = new HashSet<>();
        for (Field f : ErrorMessages.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String code = (String) f.get(null);
                assertFalse("codigo vazio: " + f.getName(), code.isEmpty());
                assertTrue("codigo repetido: " + f.getName(), codes.add(code));
            }
        }
    }

    @Test
    public void localize_nullBecomesEmpty_andUnknownTextPassesThrough() {
        // Nestes casos o Context nao chega a ser usado
        assertEquals("", ErrorMessages.localize(null, null));
        assertEquals("HTTP 500 qualquer coisa", ErrorMessages.localize(null, "HTTP 500 qualquer coisa"));
    }

    @Test
    public void localize_httpPrefixWithAnInvalidStatus_passesThroughUnchanged() {
        String broken = ErrorMessages.SPOTIFY_HTTP_PREFIX + "abc";

        assertEquals(broken, ErrorMessages.localize(null, broken));
    }
}
