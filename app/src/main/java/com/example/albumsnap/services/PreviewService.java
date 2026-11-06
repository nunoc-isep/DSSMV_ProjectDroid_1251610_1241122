package com.example.albumsnap.services;

import com.example.albumsnap.model.PreviewQuery;

import java.io.IOException;

/**
 * Contrato da procura de previews de faixas (30 s). O Spotify ja nao devolve o preview_url a
 * apps novas, por isso o audio vem de outras fontes (ver DeezerPreviewService,
 * ItunesPreviewService e CompositePreviewService).
 * Bloqueante: usar fora da UI thread.
 */
public interface PreviewService {

    /**
     * URL do audio da preview, ou null se esta fonte nao tiver a MESMA gravacao da faixa
     * (e preferivel nao ter preview a ter a de outra versao).
     *
     * @throws IOException se a fonte nao respondeu (rede, limite de pedidos...)
     */
    String findPreviewUrl(PreviewQuery query) throws IOException;
}
