package com.example.albumsnap.services;

import com.example.albumsnap.model.Album;

import java.io.IOException;
import java.util.List;

/** Contrato da pesquisa de albuns (class_diagram.puml). Bloqueante: usar fora da UI thread. */
public interface AlbumSearchService {

    /** Pesquisa por texto livre, p.ex. o texto lido na capa pelo reconhecimento da imagem (maximo 10 resultados). */
    List<Album> searchAlbums(String query) throws IOException;

    /** Album completo, com faixas. */
    Album getAlbumDetails(String spotifyId) throws IOException;
}
