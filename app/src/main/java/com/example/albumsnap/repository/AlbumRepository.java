package com.example.albumsnap.repository;

import com.example.albumsnap.model.Album;

import java.io.IOException;

/** Contrato do UML: acesso a albuns (aqui, a fonte e o Spotify). Bloqueante. */
public interface AlbumRepository {

    Album findBySpotifyId(String spotifyId) throws IOException;

    /** Guarda o album na biblioteca do Spotify do utilizador. */
    void save(Album album) throws IOException;
}
