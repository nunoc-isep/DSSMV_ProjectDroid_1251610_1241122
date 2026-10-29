package com.example.albumsnap.util;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

/**
 * Reproduz uma preview de cada vez (MediaPlayer em modo assincrono).
 * Os callbacks correm na UI thread. stop() e release() nao chamam o Listener.
 */
public class PreviewPlayer {

    public interface Listener {
        /** O audio comecou a tocar. */
        void onPlaying();

        /** A preview chegou ao fim. */
        void onFinished();

        /** Falhou a preparar ou a reproduzir. */
        void onError();
    }

    private final Listener listener;
    private MediaPlayer player;

    public PreviewPlayer(Listener listener) {
        this.listener = listener;
    }

    /** Para o que estiver a tocar e comeca a preparar o novo URL. */
    public void play(String url) {
        release();

        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        // So reage a callbacks do player atual (p == player); os de players ja libertados sao ignorados
        mp.setOnPreparedListener(p -> {
            if (p != player) return;
            p.start();
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

    /** Para e liberta o player, sem avisar o Listener. */
    public void stop() {
        release();
    }

    public void release() {
        MediaPlayer mp = player;
        player = null;     // antes do release, para ignorar callbacks que ainda cheguem
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {
                // ja estava libertado
            }
        }
    }
}
