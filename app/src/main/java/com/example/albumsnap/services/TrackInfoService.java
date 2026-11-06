package com.example.albumsnap.services;

import java.io.IOException;

/** Informacao extra de uma faixa que o Spotify so da no pedido da faixa. Bloqueante. */
public interface TrackInfoService {

    /** ISRC (codigo unico da gravacao) da faixa, ou "" se o Spotify nao o der. */
    String getIsrc(String trackSpotifyId) throws IOException;
}
