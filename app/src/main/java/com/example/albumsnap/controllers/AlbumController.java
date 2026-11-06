package com.example.albumsnap.controllers;

import android.content.Context;
import android.util.Log;

import com.example.albumsnap.external.CompositePreviewService;
import com.example.albumsnap.external.DeezerPreviewService;
import com.example.albumsnap.external.ItunesPreviewService;
import com.example.albumsnap.external.SpotifyAlbumRepository;
import com.example.albumsnap.external.SpotifyApiClient;
import com.example.albumsnap.external.SpotifyAuthManager;
import com.example.albumsnap.external.SpotifyFavouriteAlbumService;
import com.example.albumsnap.external.SpotifyFavouriteTrackService;
import com.example.albumsnap.external.SpotifyPlaylistService;
import com.example.albumsnap.external.SpotifyTrackInfoService;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Artist;
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
import com.example.albumsnap.storage.SqliteAlbumRepository;
import com.example.albumsnap.util.Background;
import com.example.albumsnap.util.Callback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Logica do ecra do album: obter o album, guardar na Library da app,
 * adicionar o album ou uma faixa aos favoritos do Spotify, adicionar faixas a playlists
 * e obter previews das faixas.
 * Tudo corre fora da UI thread.
 */
public class AlbumController {

    private static final String TAG = "AlbumController";

    private static final Callback<Boolean> IGNORE = new Callback<Boolean>() {
        @Override
        public void onResult(Boolean result) {
        }

        @Override
        public void onError(String message) {
        }
    };

    private final AlbumRepository remoteRepository;
    private final FavouriteAlbumService favouriteService;
    private final LocalAlbumRepository localRepository;
    private final PreviewService previewService;
    private final TrackInfoService trackInfoService;
    private final FavouriteTrackService favouriteTrackService;
    private final PlaylistService playlistService;

    public AlbumController(AlbumRepository remoteRepository, FavouriteAlbumService favouriteService,
                           LocalAlbumRepository localRepository, PreviewService previewService,
                           TrackInfoService trackInfoService, FavouriteTrackService favouriteTrackService,
                           PlaylistService playlistService) {
        this.remoteRepository = remoteRepository;
        this.favouriteService = favouriteService;
        this.localRepository = localRepository;
        this.previewService = previewService;
        this.trackInfoService = trackInfoService;
        this.favouriteTrackService = favouriteTrackService;
        this.playlistService = playlistService;
    }

    /** Liga as pecas reais (Spotify + Deezer/iTunes + SQLite). */
    public static AlbumController create(Context context) {
        SpotifyAuthManager auth = SpotifyAuthManager.getInstance(context);
        SpotifyApiClient client = new SpotifyApiClient(auth);
        SpotifyFavouriteAlbumService favourite = new SpotifyFavouriteAlbumService(client);
        AlbumRepository repository = new SpotifyAlbumRepository(client);
        // Previews: a Deezer primeiro (pelo ISRC); se nao tiver a faixa, o iTunes
        PreviewService previews = new CompositePreviewService(
                new DeezerPreviewService(), new ItunesPreviewService());
        return new AlbumController(repository, favourite, new SqliteAlbumRepository(context), previews,
                new SpotifyTrackInfoService(client), new SpotifyFavouriteTrackService(client),
                new SpotifyPlaylistService(client));
    }

    public void getAlbum(final String spotifyId, Callback<Album> callback) {
        Background.run(new Background.Task<Album>() {
            @Override
            public Album call() throws Exception {
                return remoteRepository.findBySpotifyId(spotifyId);
            }
        }, callback);
    }

    /** Regista o album como "digitalizado agora" (alimenta a caixa Recent). */
    public void registerScan(final Album album) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() {
                localRepository.addRecentScan(album);
                return Boolean.TRUE;
            }
        }, IGNORE);
    }

    public void isInLibrary(final String spotifyId, Callback<Boolean> callback) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() {
                return localRepository.isInLibrary(spotifyId);
            }
        }, callback);
    }

    public void saveToLibrary(final Album album, Callback<Boolean> callback) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() {
                localRepository.saveToLibrary(album);
                return Boolean.TRUE;
            }
        }, callback);
    }

    public void removeFromLibrary(final String spotifyId, Callback<Boolean> callback) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() {
                localRepository.removeFromLibrary(spotifyId);
                return Boolean.TRUE;
            }
        }, callback);
    }

    /** Adiciona o album aos favoritos da biblioteca do Spotify. */
    public void addToSpotifyFavourites(final String spotifyId, Callback<Boolean> callback) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                favouriteService.addAlbum(spotifyId);
                return Boolean.TRUE;
            }
        }, callback);
    }

    /** Adiciona a faixa aos favoritos (Liked Songs) do Spotify. */
    public void addTrackToSpotifyFavourites(final String trackId, Callback<Boolean> callback) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                favouriteTrackService.addTrack(trackId);
                return Boolean.TRUE;
            }
        }, callback);
    }

    /** Playlists da conta onde se podem adicionar faixas (proprias e colaborativas). */
    public void getEditablePlaylists(Callback<List<Playlist>> callback) {
        Background.run(new Background.Task<List<Playlist>>() {
            @Override
            public List<Playlist> call() throws Exception {
                return playlistService.getEditablePlaylists();
            }
        }, callback);
    }

    public void addTrackToPlaylist(final String playlistId, final String trackId, Callback<Boolean> callback) {
        Background.run(new Background.Task<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                playlistService.addTrack(playlistId, trackId);
                return Boolean.TRUE;
            }
        }, callback);
    }

    /**
     * URL da preview (30 s) da faixa; o resultado é null se nenhuma fonte tiver a mesma gravação.
     * O ISRC (pedido extra ao Spotify) permite achar a gravação exata; sem ele procura-se por texto.
     */
    public void getPreviewUrl(final Album album, final Track track, Callback<String> callback) {
        Background.run(new Background.Task<String>() {
            @Override
            public String call() throws Exception {
                String isrc = "";
                try {
                    isrc = trackInfoService.getIsrc(track.getSpotifyId());
                } catch (IOException e) {
                    Log.w(TAG, "Sem ISRC para " + track.getSpotifyId() + ": " + e.getMessage());
                }
                return previewService.findPreviewUrl(new PreviewQuery(isrc, track.getTitle(),
                        artistNamesOf(album, track), album.getTitle(), track.getDurationMs()));
            }
        }, callback);
    }

    /** Artistas da faixa (numa compilacao diferem dos do album); sem eles, os do album. */
    private static List<String> artistNamesOf(Album album, Track track) {
        List<String> names = new ArrayList<>();
        for (Artist artist : track.getArtists()) names.add(artist.getName());
        if (names.isEmpty()) {
            for (Artist artist : album.getArtists()) names.add(artist.getName());
        }
        return names;
    }
}
