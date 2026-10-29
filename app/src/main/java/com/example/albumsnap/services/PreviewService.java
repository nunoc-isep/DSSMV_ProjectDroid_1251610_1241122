package com.example.albumsnap.services;

import java.io.IOException;
import java.util.Map;

/**
 * Contrato da procura de previews de faixas (30 s). O Spotify ja nao devolve o
 * preview_url a apps novas, por isso o URL vem de outra API (ver DeezerPreviewService).
 * Bloqueante: usar fora da UI thread.
 */
public interface PreviewService {

    /** URL do audio da preview, ou null se nao existir nenhuma para esta faixa. */
    String findPreviewUrl(String artist, String title) throws IOException;
    Map<String, String> fetchAlbumPreviews(String artist, String albumTitle) throws IOException;
}
