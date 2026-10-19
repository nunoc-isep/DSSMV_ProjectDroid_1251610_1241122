package com.example.albumsnap.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Base de dados local (SQLite): uma tabela "albums" para a Library e os ultimos scans. */
public class AlbumDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "albumsnap.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "albums";
    public static final String COL_ID = "spotify_id";
    public static final String COL_TITLE = "title";
    public static final String COL_ARTISTS = "artists";
    public static final String COL_COVER_URL = "cover_url";
    public static final String COL_RELEASE_DATE = "release_date";
    public static final String COL_TOTAL_TRACKS = "total_tracks";
    public static final String COL_ALBUM_TYPE = "album_type";
    public static final String COL_IN_LIBRARY = "in_library";
    public static final String COL_SAVED_AT = "saved_at";
    public static final String COL_LAST_SCANNED_AT = "last_scanned_at";

    public AlbumDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_ARTISTS + " TEXT, "
                + COL_COVER_URL + " TEXT, "
                + COL_RELEASE_DATE + " TEXT, "
                + COL_TOTAL_TRACKS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_ALBUM_TYPE + " TEXT, "
                + COL_IN_LIBRARY + " INTEGER NOT NULL DEFAULT 0, "
                + COL_SAVED_AT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_LAST_SCANNED_AT + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Versao 1: ainda nao ha migracoes; recria a tabela
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }
}
