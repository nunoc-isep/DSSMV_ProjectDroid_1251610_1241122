package com.example.albumsnap.services;

import java.io.IOException;

/** Contrato para marcar um album como favorito na biblioteca do Spotify. Bloqueante. */
public interface FavouriteAlbumService {

    void addAlbum(String spotifyId) throws IOException;
}
