package com.example.albumsnap.services;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.RecognitionResult;

import java.io.IOException;
import java.util.List;

/** Contrato da pesquisa de albuns (class_diagram.puml). Bloqueante: usar fora da UI thread. */
public interface AlbumSearchService {

    /** Albuns candidatos para o texto sugerido pelo reconhecimento da imagem. */
    List<Album> searchAlbums(RecognitionResult result) throws IOException;

    /** Pesquisa por texto livre (maximo 10 resultados). */
    List<Album> searchAlbums(String query) throws IOException;

    /** Album completo, com faixas. */
    Album getAlbumDetails(String spotifyId) throws IOException;
}
