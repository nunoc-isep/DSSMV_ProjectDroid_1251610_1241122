package com.example.albumsnap.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Playlist;
import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.model.Track;
import com.example.albumsnap.repository.AlbumRepository;
import com.example.albumsnap.repository.LocalAlbumRepository;
import com.example.albumsnap.services.FavouriteAlbumService;
import com.example.albumsnap.services.FavouriteTrackService;
import com.example.albumsnap.services.PlaylistService;
import com.example.albumsnap.services.PreviewService;
import com.example.albumsnap.services.TrackInfoService;
import com.example.albumsnap.testutil.Await;
import com.example.albumsnap.testutil.TestAlbums;
import com.example.albumsnap.util.Background;
import com.example.albumsnap.util.ErrorMessages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Logica do ecra do album, com servicos falsos (sem rede nem base de dados). */
public class AlbumControllerTest {

    // ------------------------------------------------------------------ fakes

    private static final class FakeRemote implements AlbumRepository {
        final Map<String, Album> albums = new HashMap<>();
        IOException failure;

        @Override
        public Album findBySpotifyId(String spotifyId) throws IOException {
            if (failure != null) throw failure;
            Album album = albums.get(spotifyId);
            if (album == null) throw new IOException("sem album " + spotifyId);
            return album;
        }
    }

    private static final class FakeLocal implements LocalAlbumRepository {
        final List<Album> library = new CopyOnWriteArrayList<>();
        final List<Album> scans = new CopyOnWriteArrayList<>();
        final CountDownLatch scanned = new CountDownLatch(1);
        volatile int requestedRecentLimit = -1;

        @Override
        public void saveToLibrary(Album album) {
            library.add(album);
        }

        @Override
        public void removeFromLibrary(String spotifyId) {
            for (Album a : library) {
                if (a.getSpotifyId().equals(spotifyId)) library.remove(a);
            }
        }

        @Override
        public boolean isInLibrary(String spotifyId) {
            for (Album a : library) {
                if (a.getSpotifyId().equals(spotifyId)) return true;
            }
            return false;
        }

        @Override
        public List<Album> getLibrary() {
            return new ArrayList<>(library);
        }

        @Override
        public void addRecentScan(Album album) {
            scans.add(album);
            scanned.countDown();
        }

        @Override
        public List<Album> getRecentScans(int limit) {
            requestedRecentLimit = limit;
            return new ArrayList<>(scans);
        }
    }

    private static final class FakeFavouriteAlbums implements FavouriteAlbumService {
        final List<String> added = new CopyOnWriteArrayList<>();
        IOException failure;

        @Override
        public void addAlbum(String spotifyId) throws IOException {
            if (failure != null) throw failure;
            added.add(spotifyId);
        }
    }

    private static final class FakeFavouriteTracks implements FavouriteTrackService {
        final List<String> added = new CopyOnWriteArrayList<>();
        IOException failure;

        @Override
        public void addTrack(String spotifyId) throws IOException {
            if (failure != null) throw failure;
            added.add(spotifyId);
        }
    }

    private static final class FakePlaylists implements PlaylistService {
        List<Playlist> playlists = new ArrayList<>();
        final List<String> additions = new CopyOnWriteArrayList<>();   // "playlist|faixa"
        IOException failure;

        @Override
        public List<Playlist> getEditablePlaylists() throws IOException {
            if (failure != null) throw failure;
            return playlists;
        }

        @Override
        public void addTrack(String playlistId, String trackSpotifyId) throws IOException {
            if (failure != null) throw failure;
            additions.add(playlistId + "|" + trackSpotifyId);
        }
    }

    private static final class FakeTrackInfo implements TrackInfoService {
        String isrc = "";
        IOException failure;
        final List<String> asked = new CopyOnWriteArrayList<>();

        @Override
        public String getIsrc(String trackSpotifyId) throws IOException {
            asked.add(trackSpotifyId);
            if (failure != null) throw failure;
            return isrc;
        }
    }

    private static final class FakePreviews implements PreviewService {
        String url = "https://preview.example/a.mp3";
        IOException failure;
        volatile PreviewQuery lastQuery;

        @Override
        public String findPreviewUrl(PreviewQuery query) throws IOException {
            lastQuery = query;
            if (failure != null) throw failure;
            return url;
        }
    }

    private FakeRemote remote;
    private FakeLocal local;
    private FakeFavouriteAlbums favouriteAlbums;
    private FakeFavouriteTracks favouriteTracks;
    private FakePlaylists playlists;
    private FakeTrackInfo trackInfo;
    private FakePreviews previews;
    private AlbumController controller;

    @Before
    public void setUp() {
        Background.runCallbacksImmediatelyForTests();
        remote = new FakeRemote();
        local = new FakeLocal();
        favouriteAlbums = new FakeFavouriteAlbums();
        favouriteTracks = new FakeFavouriteTracks();
        playlists = new FakePlaylists();
        trackInfo = new FakeTrackInfo();
        previews = new FakePreviews();
        controller = new AlbumController(remote, favouriteAlbums, local, previews, trackInfo,
                favouriteTracks, playlists);
    }

    @After
    public void tearDown() {
        Background.restoreMainThreadCallbacks();
    }

    private static final Album ALBUM = TestAlbums.album("alb1", "Discovery", "Daft Punk");

    // ----------------------------------------------------------------- album

    @Test
    public void getAlbum_returnsTheAlbumFromTheRemoteRepository() {
        remote.albums.put("alb1", ALBUM);
        Await<Album> callback = new Await<>();

        controller.getAlbum("alb1", callback);

        assertEquals("alb1", callback.result().getSpotifyId());
    }

    @Test
    public void getAlbum_failures_arePassedAsMessages() {
        remote.failure = new IOException("album apagado");
        Await<Album> failing = new Await<>();
        controller.getAlbum("alb1", failing);
        assertEquals("album apagado", failing.error());

        remote.failure = new UnknownHostException("api.spotify.com");
        Await<Album> offline = new Await<>();
        controller.getAlbum("alb1", offline);
        assertEquals(ErrorMessages.NETWORK, offline.error());
    }

    // --------------------------------------------------------------- library

    @Test
    public void registerScan_storesTheAlbumInTheRecentScans() throws Exception {
        controller.registerScan(ALBUM);

        assertTrue("o scan nao foi registado", local.scanned.await(5, TimeUnit.SECONDS));
        assertEquals("alb1", local.scans.get(0).getSpotifyId());
    }

    @Test
    public void saveIsInLibraryAndRemove_followTheLocalRepository() {
        Await<Boolean> before = new Await<>();
        controller.isInLibrary("alb1", before);
        assertFalse(before.result());

        Await<Boolean> saved = new Await<>();
        controller.saveToLibrary(ALBUM, saved);
        assertTrue(saved.result());

        Await<Boolean> after = new Await<>();
        controller.isInLibrary("alb1", after);
        assertTrue(after.result());

        Await<Boolean> removed = new Await<>();
        controller.removeFromLibrary("alb1", removed);
        assertTrue(removed.result());

        Await<Boolean> gone = new Await<>();
        controller.isInLibrary("alb1", gone);
        assertFalse(gone.result());
    }

    // ------------------------------------------------------------ favourites

    @Test
    public void addToSpotifyFavourites_sendsTheAlbumId() {
        Await<Boolean> callback = new Await<>();

        controller.addToSpotifyFavourites("alb1", callback);

        assertTrue(callback.result());
        assertEquals(Arrays.asList("alb1"), favouriteAlbums.added);
    }

    @Test
    public void addToSpotifyFavourites_failure_isAnError() {
        favouriteAlbums.failure = new IOException(ErrorMessages.SESSION_EXPIRED);
        Await<Boolean> callback = new Await<>();

        controller.addToSpotifyFavourites("alb1", callback);

        assertEquals(ErrorMessages.SESSION_EXPIRED, callback.error());
        assertTrue(favouriteAlbums.added.isEmpty());
    }

    @Test
    public void addTrackToSpotifyFavourites_sendsTheTrackId() {
        Await<Boolean> callback = new Await<>();

        controller.addTrackToSpotifyFavourites("t1", callback);

        assertTrue(callback.result());
        assertEquals(Arrays.asList("t1"), favouriteTracks.added);
    }

    @Test
    public void addTrackToSpotifyFavourites_failure_isAnError() {
        favouriteTracks.failure = new IOException(ErrorMessages.SPOTIFY_FORBIDDEN);
        Await<Boolean> callback = new Await<>();

        controller.addTrackToSpotifyFavourites("t1", callback);

        assertEquals(ErrorMessages.SPOTIFY_FORBIDDEN, callback.error());
    }

    // ------------------------------------------------------------- playlists

    @Test
    public void getEditablePlaylists_returnsWhatTheServiceReturns() {
        playlists.playlists = Arrays.asList(new Playlist("p1", "Rock"), new Playlist("p2", "Estudo"));
        Await<List<Playlist>> callback = new Await<>();

        controller.getEditablePlaylists(callback);

        assertEquals(2, callback.result().size());
        assertEquals("Estudo", callback.result().get(1).getName());
    }

    @Test
    public void getEditablePlaylists_failure_isAnError() {
        playlists.failure = new IOException(ErrorMessages.SPOTIFY_FORBIDDEN);
        Await<List<Playlist>> callback = new Await<>();

        controller.getEditablePlaylists(callback);

        assertEquals(ErrorMessages.SPOTIFY_FORBIDDEN, callback.error());
    }

    @Test
    public void addTrackToPlaylist_passesThePlaylistFirstAndTheTrackSecond() {
        Await<Boolean> callback = new Await<>();

        controller.addTrackToPlaylist("PLAYLIST", "TRACK", callback);

        assertTrue(callback.result());
        assertEquals(Arrays.asList("PLAYLIST|TRACK"), playlists.additions);
    }

    @Test
    public void addTrackToPlaylist_failure_isAnError() {
        playlists.failure = new IOException("sem permissao");
        Await<Boolean> callback = new Await<>();

        controller.addTrackToPlaylist("PLAYLIST", "TRACK", callback);

        assertEquals("sem permissao", callback.error());
    }

    // -------------------------------------------------------------- previews

    @Test
    public void getPreviewUrl_looksUpTheIsrcAndBuildsTheQueryFromTheTrack() {
        trackInfo.isrc = "USUM71703861";
        Track track = TestAlbums.track("t1", "One More Time", 320_000, "Daft Punk");
        Await<String> callback = new Await<>();

        controller.getPreviewUrl(ALBUM, track, callback);

        assertEquals("https://preview.example/a.mp3", callback.result());
        assertEquals(Arrays.asList("t1"), trackInfo.asked);
        PreviewQuery query = previews.lastQuery;
        assertEquals("USUM71703861", query.getIsrc());
        assertEquals("One More Time", query.getTitle());
        assertEquals(Arrays.asList("Daft Punk"), query.getArtists());
        assertEquals("Discovery", query.getAlbumTitle());
        assertEquals(320_000, query.getDurationMs());
    }

    @Test
    public void getPreviewUrl_withoutTheIsrc_stillSearchesByText() {
        trackInfo.failure = new IOException("429");
        Track track = TestAlbums.track("t1", "One More Time", 320_000, "Daft Punk");
        Await<String> callback = new Await<>();

        controller.getPreviewUrl(ALBUM, track, callback);

        assertEquals("https://preview.example/a.mp3", callback.result());
        assertEquals("", previews.lastQuery.getIsrc());
    }

    @Test
    public void getPreviewUrl_onACompilation_usesTheArtistsOfTheTrack() {
        Album compilation = TestAlbums.album("c1", "Now That's Music", "Various Artists");
        Track track = TestAlbums.track("t9", "Hit Song", 200_000, "Real Artist", "Guest");
        Await<String> callback = new Await<>();

        controller.getPreviewUrl(compilation, track, callback);

        callback.result();
        assertEquals(Arrays.asList("Real Artist", "Guest"), previews.lastQuery.getArtists());
    }

    @Test
    public void getPreviewUrl_trackWithoutArtists_fallsBackToTheAlbumArtists() {
        Track track = TestAlbums.track("t1", "Song", 100_000);   // sem artistas
        Await<String> callback = new Await<>();

        controller.getPreviewUrl(ALBUM, track, callback);

        callback.result();
        assertEquals(Arrays.asList("Daft Punk"), previews.lastQuery.getArtists());
    }

    @Test
    public void getPreviewUrl_whenNoSourceHasIt_theResultIsNull() {
        previews.url = null;
        Await<String> callback = new Await<>();

        controller.getPreviewUrl(ALBUM, TestAlbums.track("t1", "Song", 1, "Daft Punk"), callback);

        assertNull(callback.result());
    }

    @Test
    public void getPreviewUrl_whenEverySourceFails_isAnError() {
        previews.failure = new IOException("sem rede");
        Await<String> callback = new Await<>();

        controller.getPreviewUrl(ALBUM, TestAlbums.track("t1", "Song", 1, "Daft Punk"), callback);

        assertEquals("sem rede", callback.error());
    }
}
