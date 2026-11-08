package com.example.albumsnap.views.adapters;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Duracao das faixas na lista (minutos:segundos). */
public class TrackAdapterFormatTest {

    @Test
    public void formatDuration_showsMinutesAndTwoDigitSeconds() {
        assertEquals("0:00", TrackAdapter.formatDuration(0));
        assertEquals("0:05", TrackAdapter.formatDuration(5_000));
        assertEquals("1:00", TrackAdapter.formatDuration(60_000));
        assertEquals("3:07", TrackAdapter.formatDuration(187_000));
        assertEquals("5:20", TrackAdapter.formatDuration(320_000));
    }

    @Test
    public void formatDuration_dropsTheFractionOfASecond() {
        assertEquals("0:59", TrackAdapter.formatDuration(59_999));
        assertEquals("1:00", TrackAdapter.formatDuration(60_001));
    }

    @Test
    public void formatDuration_longTracksKeepCountingMinutes() {
        assertEquals("60:05", TrackAdapter.formatDuration(3_605_000));
        assertEquals("125:00", TrackAdapter.formatDuration(7_500_000));
    }
}
