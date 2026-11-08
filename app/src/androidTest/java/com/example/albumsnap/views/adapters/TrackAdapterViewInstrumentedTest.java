package com.example.albumsnap.views.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.R;
import com.example.albumsnap.model.Track;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** O que cada linha da lista de faixas mostra e o que fazem os seus botoes. */
@RunWith(AndroidJUnit4.class)
public class TrackAdapterViewInstrumentedTest {

    private Context context;
    private FrameLayout parent;
    private TrackAdapter adapter;

    @Before
    public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = new ContextThemeWrapper(target, R.style.Theme_AlbumSnap);   // os layouts usam atributos do tema
        parent = new FrameLayout(context);
        adapter = new TrackAdapter(context);
    }

    private static Track track(String id, String title, long millis, int number, int disc) {
        return new Track(id, title, millis, number, disc, null);
    }

    private View row(int position) {
        return adapter.getView(position, null, parent);
    }

    private static String text(View row, int id) {
        return ((TextView) row.findViewById(id)).getText().toString();
    }

    @Test
    public void aTrackRow_showsNumberTitleAndDuration() {
        adapter.setItems(Arrays.asList(track("t1", "One More Time", 320_000, 7, 1)));

        View row = row(0);

        assertEquals("7", text(row, R.id.txtTrackNumber));
        assertEquals("One More Time", text(row, R.id.txtTrackTitle));
        assertEquals("5:20", text(row, R.id.txtTrackDuration));
    }

    @Test
    public void thePreviewButton_followsThePreviewStateOfThatTrackOnly() {
        adapter.setItems(Arrays.asList(track("t1", "A", 1000, 1, 1), track("t2", "B", 1000, 2, 1)));
        String play = context.getString(R.string.preview_play_cd);
        String pause = context.getString(R.string.preview_pause_cd);

        adapter.setPreviewState("t2", TrackAdapter.PreviewState.PLAYING);

        assertEquals(play, row(0).findViewById(R.id.btnPreview).getContentDescription());
        assertEquals(pause, row(1).findViewById(R.id.btnPreview).getContentDescription());
        assertEquals(View.GONE, row(1).findViewById(R.id.progressPreview).getVisibility());
    }

    @Test
    public void whileLoading_theSpinnerIsShown() {
        adapter.setItems(Arrays.asList(track("t1", "A", 1000, 1, 1)));

        adapter.setPreviewState("t1", TrackAdapter.PreviewState.LOADING);

        ProgressBar spinner = row(0).findViewById(R.id.progressPreview);
        assertEquals(View.VISIBLE, spinner.getVisibility());
    }

    @Test
    public void clickingThePreviewButton_notifiesTheListenerWithThatTrack() {
        Track t = track("t1", "A", 1000, 1, 1);
        adapter.setItems(Arrays.asList(t));
        final List<Track> clicked = new ArrayList<>();
        adapter.setOnPreviewClickListener(clicked::add);

        row(0).findViewById(R.id.btnPreview).performClick();

        assertEquals(1, clicked.size());
        assertSame(t, clicked.get(0));
    }

    @Test
    public void clickingTheThreeDotButton_notifiesTheListenerWithThatTrackAndTheButton() {
        Track t = track("t1", "A", 1000, 1, 1);
        adapter.setItems(Arrays.asList(t));
        final List<Track> clicked = new ArrayList<>();
        final List<View> anchors = new ArrayList<>();
        adapter.setOnTrackMenuClickListener((track, anchor) -> {
            clicked.add(track);
            anchors.add(anchor);
        });
        View row = row(0);
        ImageButton menu = row.findViewById(R.id.btnTrackMenu);

        menu.performClick();

        assertSame(t, clicked.get(0));
        assertSame(menu, anchors.get(0));   // o menu abre junto ao botao
    }

    @Test
    public void clickingWithoutListeners_doesNotCrash() {
        adapter.setItems(Arrays.asList(track("t1", "A", 1000, 1, 1)));
        View row = row(0);

        row.findViewById(R.id.btnPreview).performClick();
        row.findViewById(R.id.btnTrackMenu).performClick();
    }

    @Test
    public void aRecycledRow_isRebuiltForTheNewTrack() {
        adapter.setItems(Arrays.asList(track("t1", "Primeira", 60_000, 1, 1), track("t2", "Segunda", 125_000, 2, 1)));
        View first = adapter.getView(0, null, parent);

        View reused = adapter.getView(1, first, parent);

        assertSame(first, reused);
        assertEquals("Segunda", text(reused, R.id.txtTrackTitle));
        assertEquals("2:05", text(reused, R.id.txtTrackDuration));
    }

    @Test
    public void aDiscHeader_saysWhichDisc() {
        adapter.setItems(Arrays.asList(track("a", "A", 1000, 1, 1), track("b", "B", 1000, 1, 2)));

        View header = row(2);   // 0: cabecalho do disco 1, 1: faixa a, 2: cabecalho do disco 2

        assertEquals(context.getString(R.string.album_disc, 2), ((TextView) header).getText().toString());
        assertTrue(text(header, R.id.txtDiscTitle).contains("2"));
    }

    @Test
    public void aDiscHeaderViewIsNotReusedAsATrackRow() {
        adapter.setItems(Arrays.asList(track("a", "A", 1000, 1, 1), track("b", "B", 1000, 1, 2)));
        View header = row(0);
        View trackRow = row(1);

        assertNotSame(header, trackRow);
        assertEquals(1, adapter.getItemViewType(0));
        assertEquals(0, adapter.getItemViewType(1));
    }
}
