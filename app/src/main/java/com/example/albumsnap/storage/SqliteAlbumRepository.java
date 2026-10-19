package com.example.albumsnap.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Artist;
import com.example.albumsnap.repository.LocalAlbumRepository;

import java.util.ArrayList;
import java.util.List;

/** LocalAlbumRepository sobre SQLite. Chamar fora da UI thread (ver controllers). */
public class SqliteAlbumRepository implements LocalAlbumRepository {

    private static final int MAX_RECENT = 10;

    private final AlbumDbHelper helper;

    public SqliteAlbumRepository(Context context) {
        helper = new AlbumDbHelper(context.getApplicationContext());
    }

    @Override
    public void saveToLibrary(Album album) {
        ContentValues extra = new ContentValues();
        extra.put(AlbumDbHelper.COL_IN_LIBRARY, 1);
        extra.put(AlbumDbHelper.COL_SAVED_AT, System.currentTimeMillis());
        upsert(album, extra);
    }

    @Override
    public void removeFromLibrary(String spotifyId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(AlbumDbHelper.COL_IN_LIBRARY, 0);
        values.put(AlbumDbHelper.COL_SAVED_AT, 0);
        db.update(AlbumDbHelper.TABLE, values, AlbumDbHelper.COL_ID + "=?", new String[]{spotifyId});
        // Apaga linhas que ja nao servem a Library nem aos scans recentes
        db.delete(AlbumDbHelper.TABLE, AlbumDbHelper.COL_IN_LIBRARY + "=0 AND "
                + AlbumDbHelper.COL_LAST_SCANNED_AT + "=0", null);
    }

    @Override
    public boolean isInLibrary(String spotifyId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(AlbumDbHelper.TABLE, new String[]{AlbumDbHelper.COL_ID},
                AlbumDbHelper.COL_ID + "=? AND " + AlbumDbHelper.COL_IN_LIBRARY + "=1",
                new String[]{spotifyId}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    @Override
    public List<Album> getLibrary() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(AlbumDbHelper.TABLE, null,
                AlbumDbHelper.COL_IN_LIBRARY + "=1", null, null, null,
                AlbumDbHelper.COL_SAVED_AT + " DESC")) {
            return readAlbums(c);
        }
    }

    @Override
    public void addRecentScan(Album album) {
        ContentValues extra = new ContentValues();
        extra.put(AlbumDbHelper.COL_LAST_SCANNED_AT, System.currentTimeMillis());
        upsert(album, extra);

        // Mantem apenas os 10 scans mais recentes (os guardados na Library ficam sempre)
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("DELETE FROM " + AlbumDbHelper.TABLE
                + " WHERE " + AlbumDbHelper.COL_IN_LIBRARY + "=0"
                + " AND " + AlbumDbHelper.COL_LAST_SCANNED_AT + ">0"
                + " AND " + AlbumDbHelper.COL_ID + " NOT IN (SELECT " + AlbumDbHelper.COL_ID
                + " FROM " + AlbumDbHelper.TABLE
                + " WHERE " + AlbumDbHelper.COL_LAST_SCANNED_AT + ">0"
                + " ORDER BY " + AlbumDbHelper.COL_LAST_SCANNED_AT + " DESC LIMIT " + MAX_RECENT + ")");
    }

    @Override
    public List<Album> getRecentScans(int limit) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(AlbumDbHelper.TABLE, null,
                AlbumDbHelper.COL_LAST_SCANNED_AT + ">0", null, null, null,
                AlbumDbHelper.COL_LAST_SCANNED_AT + " DESC", String.valueOf(limit))) {
            return readAlbums(c);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Insere o album ou atualiza os campos descritivos, mantendo as marcas (library, scan). */
    private void upsert(Album album, ContentValues extra) {
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(AlbumDbHelper.COL_TITLE, album.getTitle());
        values.put(AlbumDbHelper.COL_ARTISTS, album.getArtistNames());
        values.put(AlbumDbHelper.COL_COVER_URL, album.getCoverUrl());
        values.put(AlbumDbHelper.COL_RELEASE_DATE, album.getReleaseDate());
        values.put(AlbumDbHelper.COL_TOTAL_TRACKS, album.getTotalTracks());
        values.put(AlbumDbHelper.COL_ALBUM_TYPE, album.getAlbumType());
        values.putAll(extra);

        int updated = db.update(AlbumDbHelper.TABLE, values, AlbumDbHelper.COL_ID + "=?",
                new String[]{album.getSpotifyId()});
        if (updated == 0) {
            values.put(AlbumDbHelper.COL_ID, album.getSpotifyId());
            db.insert(AlbumDbHelper.TABLE, null, values);
        }
    }

    private List<Album> readAlbums(Cursor c) {
        List<Album> albums = new ArrayList<>();
        int idCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_ID);
        int titleCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_TITLE);
        int artistsCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_ARTISTS);
        int coverCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_COVER_URL);
        int dateCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_RELEASE_DATE);
        int tracksCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_TOTAL_TRACKS);
        int typeCol = c.getColumnIndexOrThrow(AlbumDbHelper.COL_ALBUM_TYPE);

        while (c.moveToNext()) {
            // Os nomes ficam guardados juntos; reconstroi-se um unico "artista" com esse texto
            List<Artist> artists = new ArrayList<>();
            String names = c.getString(artistsCol);
            if (names != null && !names.isEmpty()) artists.add(new Artist("", names));

            albums.add(new Album(c.getString(idCol), c.getString(titleCol), c.getString(dateCol),
                    c.getInt(tracksCol), c.getString(typeCol), c.getString(coverCol), artists, null));
        }
        return albums;
    }
}
