package com.example.albumsnap.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Artist;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Library e scans recentes sobre SQLite real. Usa uma base de dados de teste ("test_albumsnap.db"),
 * por isso NAO mexe na Library verdadeira do telemovel.
 */
@RunWith(AndroidJUnit4.class)
public class SqliteAlbumRepositoryInstrumentedTest {

    private static final String TEST_PREFIX = "test_";

    /** Contexto que redireciona a base de dados da app para um ficheiro so de testes. */
    private static final class TestDbContext extends ContextWrapper {
        TestDbContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;   // o repositorio pede o contexto da aplicacao: tem de continuar a ser este
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(TEST_PREFIX + name);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return super.openOrCreateDatabase(TEST_PREFIX + name, mode, factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                                   DatabaseErrorHandler errorHandler) {
            return super.openOrCreateDatabase(TEST_PREFIX + name, mode, factory, errorHandler);
        }
    }

    private Context realContext;
    private SqliteAlbumRepository repository;

    @Before
    public void setUp() {
        realContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        realContext.deleteDatabase(TEST_PREFIX + "albumsnap.db");
        repository = new SqliteAlbumRepository(new TestDbContext(realContext));
    }

    @After
    public void tearDown() {
        realContext.deleteDatabase(TEST_PREFIX + "albumsnap.db");
    }

    private static Album album(String id) {
        return new Album(id, "Title " + id, "2020-01-01", 10, "album", "https://img.example/" + id,
                Arrays.asList(new Artist("a", "Artist " + id)), null);
    }

    /** As marcas de tempo tem resolucao de milissegundos: espera um pouco para a ordem ser certa. */
    private static void tick() throws InterruptedException {
        Thread.sleep(5);
    }

    private static List<String> ids(List<Album> albums) {
        List<String> ids = new ArrayList<>();
        for (Album a : albums) ids.add(a.getSpotifyId());
        return ids;
    }

    // ---------------------------------------------------------------- Library

    @Test
    public void saveToLibrary_putsTheAlbumInTheLibraryWithItsFields() {
        repository.saveToLibrary(album("a1"));

        assertTrue(repository.isInLibrary("a1"));
        Album stored = repository.getLibrary().get(0);
        assertEquals("a1", stored.getSpotifyId());
        assertEquals("Title a1", stored.getTitle());
        assertEquals("2020", stored.getReleaseYear());
        assertEquals(10, stored.getTotalTracks());
        assertEquals("album", stored.getAlbumType());
        assertEquals("https://img.example/a1", stored.getCoverUrl());
        assertEquals("Artist a1", stored.getArtistNames());
    }

    @Test
    public void isInLibrary_isFalseForAnAlbumThatWasNeverSaved() {
        assertFalse(repository.isInLibrary("nope"));
        assertTrue(repository.getLibrary().isEmpty());
    }

    @Test
    public void severalArtists_areStoredAsOneJoinedName() {
        Album collab = new Album("c1", "Collab", "2020", 1, "album", "",
                Arrays.asList(new Artist("1", "Daft Punk"), new Artist("2", "Pharrell Williams")), null);

        repository.saveToLibrary(collab);

        assertEquals("Daft Punk, Pharrell Williams", repository.getLibrary().get(0).getArtistNames());
    }

    @Test
    public void saveToLibrary_twice_doesNotDuplicateTheAlbum() {
        repository.saveToLibrary(album("a1"));
        repository.saveToLibrary(album("a1"));

        assertEquals(1, repository.getLibrary().size());
    }

    @Test
    public void getLibrary_listsTheMostRecentlySavedFirst() throws Exception {
        repository.saveToLibrary(album("old"));
        tick();
        repository.saveToLibrary(album("middle"));
        tick();
        repository.saveToLibrary(album("new"));

        assertEquals(Arrays.asList("new", "middle", "old"), ids(repository.getLibrary()));
    }

    @Test
    public void removeFromLibrary_removesAnAlbumThatWasNeverScanned() {
        repository.saveToLibrary(album("a1"));

        repository.removeFromLibrary("a1");

        assertFalse(repository.isInLibrary("a1"));
        assertTrue(repository.getLibrary().isEmpty());
    }

    @Test
    public void removeFromLibrary_keepsTheAlbumInTheRecentScans() throws Exception {
        repository.addRecentScan(album("a1"));
        tick();
        repository.saveToLibrary(album("a1"));

        repository.removeFromLibrary("a1");

        assertFalse(repository.isInLibrary("a1"));
        assertEquals(Arrays.asList("a1"), ids(repository.getRecentScans(10)));
    }

    // ---------------------------------------------------------- recent scans

    @Test
    public void addRecentScan_listsTheNewestFirstAndRespectsTheLimit() throws Exception {
        for (int i = 1; i <= 4; i++) {
            repository.addRecentScan(album("s" + i));
            tick();
        }

        assertEquals(Arrays.asList("s4", "s3", "s2", "s1"), ids(repository.getRecentScans(10)));
        assertEquals(Arrays.asList("s4", "s3"), ids(repository.getRecentScans(2)));
    }

    @Test
    public void addRecentScan_keepsOnlyTheTenMostRecent() throws Exception {
        for (int i = 1; i <= 12; i++) {
            repository.addRecentScan(album("s" + i));
            tick();
        }

        List<String> recent = ids(repository.getRecentScans(50));

        assertEquals(10, recent.size());
        assertEquals("s12", recent.get(0));
        assertFalse(recent.contains("s1"));
        assertFalse(recent.contains("s2"));
    }

    @Test
    public void addRecentScan_rescanningMovesTheAlbumToTheTopWithoutDuplicates() throws Exception {
        repository.addRecentScan(album("a"));
        tick();
        repository.addRecentScan(album("b"));
        tick();
        repository.addRecentScan(album("a"));

        assertEquals(Arrays.asList("a", "b"), ids(repository.getRecentScans(10)));
    }

    @Test
    public void addRecentScan_neverEvictsAlbumsSavedInTheLibrary() throws Exception {
        repository.addRecentScan(album("keep"));
        tick();
        repository.saveToLibrary(album("keep"));
        for (int i = 1; i <= 12; i++) {
            tick();
            repository.addRecentScan(album("s" + i));
        }

        assertTrue(repository.isInLibrary("keep"));
        assertEquals(Arrays.asList("keep"), ids(repository.getLibrary()));
    }

    @Test
    public void saveAfterScan_keepsBothTheLibraryAndTheRecentMark() throws Exception {
        repository.addRecentScan(album("a1"));
        tick();
        repository.saveToLibrary(album("a1"));

        assertTrue(repository.isInLibrary("a1"));
        assertEquals(Arrays.asList("a1"), ids(repository.getRecentScans(10)));
    }
}
