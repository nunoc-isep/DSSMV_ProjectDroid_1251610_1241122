package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.services.PreviewService;

import org.junit.Test;

import java.io.IOException;

/** Varias fontes de previews: a primeira que tiver a faixa ganha; as que falham sao saltadas. */
public class CompositePreviewServiceTest {

    private static final PreviewQuery QUERY = new PreviewQuery("", "Song", null, "", 0);

    /** Fonte falsa: devolve um URL, devolve null ou falha; e conta as chamadas. */
    private static final class FakeSource implements PreviewService {
        int calls;
        private final String url;
        private final IOException error;

        FakeSource(String url, IOException error) {
            this.url = url;
            this.error = error;
        }

        static FakeSource hit(String url) {
            return new FakeSource(url, null);
        }

        static FakeSource miss() {
            return new FakeSource(null, null);
        }

        static FakeSource failing(String message) {
            return new FakeSource(null, new IOException(message));
        }

        @Override
        public String findPreviewUrl(PreviewQuery query) throws IOException {
            calls++;
            if (error != null) throw error;
            return url;
        }
    }

    @Test
    public void firstSourceWins_andTheOthersAreNotAsked() throws Exception {
        FakeSource first = FakeSource.hit("https://first");
        FakeSource second = FakeSource.hit("https://second");

        assertEquals("https://first", new CompositePreviewService(first, second).findPreviewUrl(QUERY));
        assertEquals(0, second.calls);
    }

    @Test
    public void whenTheFirstHasNothing_usesTheSecond() throws Exception {
        FakeSource first = FakeSource.miss();
        FakeSource second = FakeSource.hit("https://second");

        assertEquals("https://second", new CompositePreviewService(first, second).findPreviewUrl(QUERY));
        assertEquals(1, first.calls);
    }

    @Test
    public void anEmptyUrlCountsAsNothing() throws Exception {
        assertEquals("https://second", new CompositePreviewService(
                FakeSource.hit(""), FakeSource.hit("https://second")).findPreviewUrl(QUERY));
    }

    @Test
    public void whenTheFirstFails_usesTheSecondInsteadOfFailing() throws Exception {
        assertEquals("https://second", new CompositePreviewService(
                FakeSource.failing("rede"), FakeSource.hit("https://second")).findPreviewUrl(QUERY));
    }

    @Test
    public void ifOneSourceAnsweredWithNothing_aFailureOfTheOtherIsNotAnError() throws Exception {
        assertNull(new CompositePreviewService(
                FakeSource.miss(), FakeSource.failing("limite")).findPreviewUrl(QUERY));
    }

    @Test
    public void whenEverySourceFails_throwsTheLastError() {
        IOException last = new IOException("segunda falhou");
        CompositePreviewService service = new CompositePreviewService(
                FakeSource.failing("primeira falhou"), new FakeSource(null, last));

        IOException e = assertThrows(IOException.class, () -> service.findPreviewUrl(QUERY));

        assertSame(last, e);
    }

    @Test
    public void whenEverySourceAnswersWithNothing_returnsNull() throws Exception {
        assertNull(new CompositePreviewService(FakeSource.miss(), FakeSource.miss()).findPreviewUrl(QUERY));
    }

    @Test
    public void withNoSources_returnsNull() throws Exception {
        assertNull(new CompositePreviewService().findPreviewUrl(QUERY));
    }
}
