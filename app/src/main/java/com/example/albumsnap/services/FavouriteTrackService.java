package com.example.albumsnap.services;

import java.io.IOException;

/** Contrato para marcar uma faixa como favorita (Liked Songs) na biblioteca do Spotify. Bloqueante. */
public interface FavouriteTrackService {

    void addTrack(String spotifyId) throws IOException;
}
