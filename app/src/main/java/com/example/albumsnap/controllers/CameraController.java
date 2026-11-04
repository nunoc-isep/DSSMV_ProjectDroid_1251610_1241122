package com.example.albumsnap.controllers;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.StringRes;

import com.example.albumsnap.R;
import com.example.albumsnap.external.GoogleCloudVisionService;
import com.example.albumsnap.external.SpotifyApiClient;
import com.example.albumsnap.external.SpotifyAlbumRepository;
import com.example.albumsnap.external.SpotifyAlbumSearchService;
import com.example.albumsnap.external.SpotifyAuthManager;
import com.example.albumsnap.external.SpotifyFavouriteAlbumService;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.repository.AlbumRepository;
import com.example.albumsnap.services.AlbumSearchService;
import com.example.albumsnap.services.FavouriteAlbumService;
import com.example.albumsnap.services.ImageRecognitionService;
import com.example.albumsnap.services.SpotifyAuthService;

import java.util.ArrayList;
import java.util.List;

public class CameraController {

    private final AlbumSearchService albumSearchService;
    private final ImageRecognitionService imageRecognitionService;

    public interface CameraCallback {
        void onStatusUpdate(@StringRes int statusRes);
        void onAlbumsRecognized(List<Album> albums);
        void onError(@StringRes int messageRes, String detail);
    }

    public CameraController(Context context) {
        SpotifyAuthService authService = SpotifyAuthManager.getInstance(context);
        SpotifyApiClient spotifyApiClient = new SpotifyApiClient(authService);
        FavouriteAlbumService favouriteService = new SpotifyFavouriteAlbumService(spotifyApiClient);
        AlbumRepository repository = new SpotifyAlbumRepository(spotifyApiClient, favouriteService);

        this.albumSearchService = new SpotifyAlbumSearchService(spotifyApiClient, repository);
        this.imageRecognitionService = new GoogleCloudVisionService();
    }

    public void processCapturedImage(Bitmap photo, CameraCallback callback) {
        // Notifica etapa 1: Análise visual (Google Vision)
        callback.onStatusUpdate(R.string.status_analyzing_vision);

        imageRecognitionService.analyzeImage(photo, new ImageRecognitionService.VisionCallback() {
            @Override
            public void onSuccess(String jsonResult) {
                String searchQuery = GoogleCloudVisionService.parseDetectedText(jsonResult);

                if (searchQuery != null && !searchQuery.isEmpty()) {
                    // Notifica etapa 2: Pesquisa no Spotify
                    callback.onStatusUpdate(R.string.status_searching_spotify);

                    new Thread(() -> {
                        try {
                            List<Album> results = albumSearchService.searchAlbums(searchQuery);

                            if (results != null && !results.isEmpty()) {
                                String topTitle = results.get(0).getTitle().toLowerCase();
                                String queryLower = searchQuery.toLowerCase();

                                if (topTitle.contains(queryLower) || queryLower.contains(topTitle)) {
                                    List<Album> singleResult = new ArrayList<>();
                                    singleResult.add(results.get(0));
                                    callback.onAlbumsRecognized(singleResult);
                                } else {
                                    callback.onAlbumsRecognized(results);
                                }
                            } else {
                                callback.onError(R.string.error_album_not_found, null);
                            }
                        } catch (Exception e) {
                            callback.onError(R.string.error_spotify_search, e.getMessage());
                        }
                    }).start();
                } else {
                    callback.onError(R.string.error_no_text, null);
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(R.string.error_analyze_image, e.getMessage());
            }
        });
    }
}