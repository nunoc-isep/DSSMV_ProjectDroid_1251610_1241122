package com.example.albumsnap.views.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.model.Track;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Linhas da lista de faixas: cabecalhos "Disco N" so quando ha mais de um disco. */
@RunWith(AndroidJUnit4.class)
public class TrackAdapterInstrumentedTest {

    private static final int TYPE_TRACK = 0;
    private static final int TYPE_DISC = 1;

    private TrackAdapter adapter;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        adapter = new TrackAdapter(context);
    }

    private static Track track(String id, int number, int disc) {
        return new Track(id, "Song " + id, 180_000, number, disc, null);
    }

    @Test
    public void singleDiscAlbum_hasNoHeaders() {
        adapter.setItems(Arrays.asList(track("a", 1, 1), track("b", 2, 1), track("c", 3, 1)));

        assertEquals(3, adapter.getCount());
        for (int i = 0; i < 3; i++) assertEquals(TYPE_TRACK, adapter.getItemViewType(i));
        assertEquals(2, adapter.getViewTypeCount());
    }

    @Test
    public void multiDiscAlbum_getsAHeaderBeforeEachDisc() {
        Track a = track("a", 1, 1), b = track("b", 2, 1), c = track("c", 1, 2), d = track("d", 2, 2);
        adapter.setItems(Arrays.asList(a, b, c, d));

        assertEquals(6, adapter.getCount());
        assertEquals(TYPE_DISC, adapter.getItemViewType(0));
        assertEquals(Integer.valueOf(1), adapter.getItem(0));
        assertSame(a, adapter.getItem(1));
        assertSame(b, adapter.getItem(2));
        assertEquals(TYPE_DISC, adapter.getItemViewType(3));
        assertEquals(Integer.valueOf(2), adapter.getItem(3));
        assertSame(c, adapter.getItem(4));
        assertSame(d, adapter.getItem(5));
    }

    @Test
    public void tracksOutOfOrder_areGroupedByDiscKeepingTheirOrderInsideEachDisc() {
        Track d2a = track("d2a", 1, 2), d1a = track("d1a", 1, 1), d2b = track("d2b", 2, 2), d1b = track("d1b", 2, 1);
        adapter.setItems(Arrays.asList(d2a, d1a, d2b, d1b));

        assertEquals(6, adapter.getCount());   // so dois cabecalhos
        assertEquals(Integer.valueOf(1), adapter.getItem(0));
        assertSame(d1a, adapter.getItem(1));
        assertSame(d1b, adapter.getItem(2));
        assertEquals(Integer.valueOf(2), adapter.getItem(3));
        assertSame(d2a, adapter.getItem(4));
        assertSame(d2b, adapter.getItem(5));
    }

    @Test
    public void nullOrEmptyList_givesAnEmptyAdapter() {
        adapter.setItems(null);
        assertEquals(0, adapter.getCount());

        adapter.setItems(Arrays.<Track>asList());
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void rowsAreNotSelectable_onlyTheirButtonsAre() {
        adapter.setItems(Arrays.asList(track("a", 1, 1), track("b", 1, 2)));

        for (int i = 0; i < adapter.getCount(); i++) assertFalse(adapter.isEnabled(i));
        assertTrue(adapter.getCount() > 0);
    }
}
