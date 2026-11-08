package com.example.albumsnap.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.repository.LocalAlbumRepository;
import com.example.albumsnap.testutil.Await;
import com.example.albumsnap.testutil.TestAlbums;
import com.example.albumsnap.util.Background;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LibraryControllerTest {

    private static final class FakeLocal implements LocalAlbumRepository {
        List<Album> library = new ArrayList<>();
        List<Album> recent = new ArrayList<>();
        volatile int requestedLimit = -1;
        RuntimeException failure;

        @Override
        public void saveToLibrary(Album album) {
        }

        @Override
        public void removeFromLibrary(String spotifyId) {
        }

        @Override
        public boolean isInLibrary(String spotifyId) {
            return false;
        }

        @Override
        public List<Album> getLibrary() {
            if (failure != null) throw failure;
            return library;
        }

        @Override
        public void addRecentScan(Album album) {
        }

        @Override
        public List<Album> getRecentScans(int limit) {
            requestedLimit = limit;
            if (failure != null) throw failure;
            return recent;
        }
    }

    private FakeLocal local;
    private LibraryController controller;

    @Before
    public void setUp() {
        Background.runCallbacksImmediatelyForTests();
        local = new FakeLocal();
        controller = new LibraryController(local);
    }

    @After
    public void tearDown() {
        Background.restoreMainThreadCallbacks();
    }

    @Test
    public void getLibraryAlbums_returnsTheSavedAlbums() {
        local.library = Arrays.asList(TestAlbums.album("a", "A", "X"), TestAlbums.album("b", "B", "Y"));
        Await<List<Album>> callback = new Await<>();

        controller.getLibraryAlbums(callback);

        assertEquals(2, callback.result().size());
        assertEquals("a", callback.result().get(0).getSpotifyId());
    }

    @Test
    public void getLibraryAlbums_emptyLibrary_isAnEmptyList() {
        Await<List<Album>> callback = new Await<>();

        controller.getLibraryAlbums(callback);

        assertTrue(callback.result().isEmpty());
    }

    @Test
    public void getRecentScans_asksForTheTenMostRecent() {
        local.recent = Arrays.asList(TestAlbums.album("r1", "R", "X"));
        Await<List<Album>> callback = new Await<>();

        controller.getRecentScans(callback);

        assertEquals(1, callback.result().size());
        assertEquals(10, local.requestedLimit);
    }

    @Test
    public void databaseFailures_areReportedAsErrors() {
        local.failure = new IllegalStateException("base de dados fechada");
        Await<List<Album>> library = new Await<>();
        Await<List<Album>> recent = new Await<>();

        controller.getLibraryAlbums(library);
        controller.getRecentScans(recent);

        assertEquals("base de dados fechada", library.error());
        assertEquals("base de dados fechada", recent.error());
    }
}
