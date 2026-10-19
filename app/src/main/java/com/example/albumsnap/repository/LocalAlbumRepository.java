package com.example.albumsnap.repository;

import com.example.albumsnap.model.Album;

import java.util.List;

/** Armazenamento local: Library da app e ultimos albuns digitalizados. */
public interface LocalAlbumRepository{

    void saveToLibrary(Album album);

    void removeFromLibrary(String spotifyId);

    boolean isInLibrary(String spotifyId);

    /** Albuns guardados, do mais recente para o mais antigo. */
    List<Album> getLibrary();

    /** Regista um album como "digitalizado agora" (mantem apenas os 10 mais recentes). */
    void addRecentScan(Album album);

    List<Album> getRecentScans(int limit);
}
