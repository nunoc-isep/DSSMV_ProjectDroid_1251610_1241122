package com.example.albumsnap.controllers;

import android.content.Context;

import com.example.albumsnap.external.DeezerPreviewService;
import com.example.albumsnap.external.SpotifyAlbumRepository;
import com.example.albumsnap.external.SpotifyAlbumSearchService;
import com.example.albumsnap.external.SpotifyApiClient;
import com.example.albumsnap.external.SpotifyAuthManager;
import com.example.albumsnap.external.SpotifyFavouriteAlbumService;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Track;
import com.example.albumsnap.repository.AlbumRepository;
import com.example.albumsnap.repository.LocalAlbumRepository;
import com.example.albumsnap.services.AlbumSearchService;
import com.example.albumsnap.services.FavouriteAlbumService;
import com.example.albumsnap.services.PreviewService;
import com.example.albumsnap.storage.SqliteAlbumRepository;
import com.example.albumsnap.util.Background;
import com.example.albumsnap.util.Callback;

import java.util.List;
import java.util.Map;

/**
 * Logica do ecra do album: obter o album, guardar na Library da app,
 * adicionar aos favoritos do Spotify e obter previews das faixas.
 * Tudo corre fora da UI thread.
 */
public class AlbumController {

    private static final Callback<Boolean> IGNORE = new Callback<Boolean>() {
        @Override
        public void onResult(Boolean result) {
        }

        @Override
        public void onError(String message) {
        }
    };

    private final AlbumRepository remoteRepository;
    private final AlbumSearchService searchService;
    private final FavouriteAlbumService favouriteService;
    private final LocalAlbumRepository localRepository;
    private final PreviewService previewService;

    public AlbumController(AlbumRepository remoteRepository, AlbumSearchService searchService,
                           FavouriteAlbumService favouriteService, LocalAlbumRepository localRepository,
                           PreviewService previewService) {
        this.remoteRepository = remoteRepository;
        this.searchService = searchService;
        this.favouriteService = favouriteService;
        this.localRepository = localRepository;
        this.previewService = previewService;
    }

    /** Liga as pecas reais (Spotify + Deezer + SQLite). */
    public static AlbumController create(Context context) {
        SpotifyAuthManager auth = SpotifyAuthManager.getInstance(context);
        SpotifyApiClient client = new SpotifyApiClient(auth);
        SpotifyFavouriteAlbumService favourite = new SpotifyFavouriteAlbumService(client);
        AlbumRepository repository = new SpotifyAlbumRepository(client, favourite);
        AlbumSearchService search = new SpotifyAlbumSearchService(client, repository);
        return new AlbumController(repository, search, favourite, new SqliteAlbumRepository(context),
                new DeezerPreviewService());
    }

    public void getAlbum(final String spotifyId, Callback<Album> callback) {
        Background.run(new Background.Task<Album>() {
            @Override
            public Album call() throws Exception {
                return remoteRepository.findBySpotifyId(spotifyId);
            }
        }, callback);
    }

    /** Pesquisa por texto (ainda nao usada pelos ecras; fica pronta para o fluxo da camara). */
    public void searchAlbums(final String query, Callback<List<Album>> callback) {
        Background.run(new Background.Task<List<Album>>() {
            @Override
            public List<Album> call() throws Exception {
                return searchService.searchAlbums(query);
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

    /** URL da preview (30 s) da faixa; o resultado é null se não houver preview. */
    public void getPreviewUrl(final Album album, final Track track, Callback<String> callback) {
        final String artist = album.getArtists().isEmpty() ? "" : album.getArtists().get(0).getName();
        final String albumTitle = album.getTitle();

        Background.run(new Background.Task<String>() {
            @Override
            public String call() throws Exception {
                // Tenta procurar a faixa no contexto do artista e album no Deezer
                String url = previewService.findPreviewUrl(artist, track.getTitle());
                if (url == null && albumTitle != null && !albumTitle.isEmpty()) {
                    Map<String, String> albumPreviews = previewService.fetchAlbumPreviews(artist, albumTitle);
                    String normalizedTrack = DeezerPreviewService.normalize(DeezerPreviewService.cleanTitle(track.getTitle()));
                    url = albumPreviews.get(normalizedTrack);
                }
                return url;
            }
        }, callback);
    }
}
