package com.example.albumsnap.controllers;

import android.content.Context;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.repository.LocalAlbumRepository;
import com.example.albumsnap.storage.SqliteAlbumRepository;
import com.example.albumsnap.util.Background;
import com.example.albumsnap.util.Callback;

import java.util.List;

/** Logica da Library e da caixa Recent do menu principal. */
public class LibraryController {

    private static final int RECENT_LIMIT = 10;

    private final LocalAlbumRepository localRepository;

    public LibraryController(LocalAlbumRepository localRepository) {
        this.localRepository = localRepository;
    }

    public static LibraryController create(Context context) {
        return new LibraryController(new SqliteAlbumRepository(context));
    }

    public void getLibraryAlbums(Callback<List<Album>> callback) {
        Background.run(new Background.Task<List<Album>>() {
            @Override
            public List<Album> call() {
                return localRepository.getLibrary();
            }
        }, callback);
    }

    /** Ultimos 10 albuns digitalizados, o mais recente primeiro. */
    public void getRecentScans(Callback<List<Album>> callback) {
        Background.run(new Background.Task<List<Album>>() {
            @Override
            public List<Album> call() {
                return localRepository.getRecentScans(RECENT_LIMIT);
            }
        }, callback);
    }
}
