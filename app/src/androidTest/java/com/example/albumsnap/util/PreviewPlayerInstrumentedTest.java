package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Play, pausa, retoma, fim e erro do leitor de previews, com ficheiros de audio gerados pelo
 * proprio teste (um tom WAV): nao precisa de rede nem de colunas.
 */
@RunWith(AndroidJUnit4.class)
public class PreviewPlayerInstrumentedTest {

    private static final long WAIT_SECONDS = 10;

    private File longTone;    // 5 s: da tempo de pausar e retomar
    private File shortTone;   // 0.4 s: acaba sozinho
    private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
    private PreviewPlayer player;

    @Before
    public void setUp() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        longTone = writeWav(new File(context.getCacheDir(), "test_tone_long.wav"), 5000);
        shortTone = writeWav(new File(context.getCacheDir(), "test_tone_short.wav"), 400);
        player = new PreviewPlayer(new PreviewPlayer.Listener() {
            @Override
            public void onPlaying() {
                events.add("playing");
            }

            @Override
            public void onPaused() {
                events.add("paused");
            }

            @Override
            public void onFinished() {
                events.add("finished");
            }

            @Override
            public void onError() {
                events.add("error");
            }
        });
    }

    @After
    public void tearDown() {
        player.release();
        longTone.delete();
        shortTone.delete();
    }

    /** Ficheiro WAV (PCM 16 bits, mono, 8 kHz) com um tom de 440 Hz de volume baixo. */
    private static File writeWav(File file, int millis) throws IOException {
        int sampleRate = 8000;
        int samples = sampleRate * millis / 1000;
        int dataSize = samples * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1)           // PCM, mono
                .putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        for (int i = 0; i < samples; i++) {
            buffer.putShort((short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 2000));
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(buffer.array());
        }
        return file;
    }

    private String next() throws InterruptedException {
        String event = events.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        return event != null ? event : "(nada em " + WAIT_SECONDS + " s)";
    }

    @Test
    public void play_aValidFile_startsPlaying() throws Exception {
        player.play(longTone.getAbsolutePath());

        assertEquals("playing", next());
        assertTrue(player.isPlaying());
        assertFalse(player.isPaused());
    }

    @Test
    public void pause_whilePlaying_pauses() throws Exception {
        player.play(longTone.getAbsolutePath());
        assertEquals("playing", next());

        player.pause();

        assertEquals("paused", next());
        assertFalse(player.isPlaying());
        assertTrue(player.isPaused());
    }

    @Test
    public void play_afterAPause_resumesTheSameTrack() throws Exception {
        player.play(longTone.getAbsolutePath());
        assertEquals("playing", next());
        player.pause();
        assertEquals("paused", next());

        player.play(longTone.getAbsolutePath());   // retoma, sem recarregar

        assertEquals("playing", next());
        assertTrue(player.isPlaying());
        assertFalse(player.isPaused());
    }

    @Test
    public void pause_whenNothingIsPlaying_doesNothing() throws Exception {
        player.pause();

        assertNull(events.poll(300, TimeUnit.MILLISECONDS));
        assertFalse(player.isPaused());
    }

    @Test
    public void stop_releasesThePlayer() throws Exception {
        player.play(longTone.getAbsolutePath());
        assertEquals("playing", next());

        player.stop();

        assertFalse(player.isPlaying());
        assertFalse(player.isPaused());
    }

    @Test
    public void aShortTrack_reportsFinishedWhenItEnds() throws Exception {
        player.play(shortTone.getAbsolutePath());
        assertEquals("playing", next());

        assertEquals("finished", next());
        assertFalse(player.isPlaying());
    }

    @Test
    public void playingAnotherTrack_replacesTheFirstOne() throws Exception {
        player.play(longTone.getAbsolutePath());
        assertEquals("playing", next());

        player.play(shortTone.getAbsolutePath());

        assertEquals("playing", next());
        assertEquals("finished", next());   // so a segunda faixa chega ao fim
    }

    @Test
    public void aMissingFile_isReportedAsAnError() throws Exception {
        player.play("/nao/existe/preview.mp3");

        assertEquals("error", next());
        assertFalse(player.isPlaying());
    }

    @Test
    public void aFileThatIsNotAudio_isReportedAsAnError() throws Exception {
        File junk = new File(longTone.getParentFile(), "test_not_audio.wav");
        try (FileOutputStream out = new FileOutputStream(junk)) {
            out.write("isto nao e audio".getBytes(StandardCharsets.UTF_8));
        }

        try {
            player.play(junk.getAbsolutePath());
            assertEquals("error", next());
        } finally {
            junk.delete();
        }
    }
}
