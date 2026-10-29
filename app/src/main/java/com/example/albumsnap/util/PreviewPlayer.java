package com.example.albumsnap.util;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

/**
 * Reproduz uma preview de cada vez (MediaPlayer em modo assíncrono).
 * Suporta Play, Pause e Resume.
 */
public class PreviewPlayer {

    public interface Listener {
        void onPlaying();
        void onPaused();
        void onFinished();
        void onError();
    }

    private final Listener listener;
    private MediaPlayer player;
    private boolean isPaused = false;

    public PreviewPlayer(Listener listener) {
        this.listener = listener;
    }

    public void play(String url) {
        if (player != null && isPaused) {
            player.start();
            isPaused = false;
            listener.onPlaying();
            return;
        }

        release();

        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        mp.setOnPreparedListener(p -> {
            if (p != player) return;
            p.start();
            isPaused = false;
            listener.onPlaying();
        });

        mp.setOnCompletionListener(p -> {
            if (p != player) return;
            release();
            listener.onFinished();
        });

        mp.setOnErrorListener((p, what, extra) -> {
            if (p == player) {
                release();
                listener.onError();
            }
            return true;
        });

        try {
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (Exception e) {
            release();
            listener.onError();
        }
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
            isPaused = true;
            listener.onPaused();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void stop() {
        release();
    }

    public void release() {
        MediaPlayer mp = player;
        player = null;
        isPaused = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {
            }
        }
    }
}