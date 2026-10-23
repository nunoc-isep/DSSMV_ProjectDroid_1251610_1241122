package com.example.albumsnap.controllers;

import android.content.Context;
import android.graphics.Bitmap;

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
        void onAlbumsRecognized(List<Album> albums);
        void onError(String message);
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
        imageRecognitionService.analyzeImage(photo, new ImageRecognitionService.VisionCallback() {
            @Override
            public void onSuccess(String jsonResult) {
                String searchQuery = GoogleCloudVisionService.parseDetectedText(jsonResult);

                if (searchQuery != null && !searchQuery.isEmpty()) {
                    new Thread(() -> {
                        try {
                            // Obtém diretamente a lista de modelos de domínio (Album)
                            List<Album> results = albumSearchService.searchAlbums(searchQuery);

                            if (results != null && !results.isEmpty()) {

                                // REGRA DE DECISÃO:
                                // Se o primeiro resultado for quase idêntico à pesquisa, envia apenas esse (Abre Direto).
                                // Se a pesquisa for abrangente, envia a lista completa (Mostra o Menu de Escolha).
                                String topTitle = results.get(0).getTitle().toLowerCase();
                                String queryLower = searchQuery.toLowerCase();

                                if (topTitle.contains(queryLower) || queryLower.contains(topTitle)) {
                                    // Correspondência forte: envia apenas o 1º elemento
                                    List<Album> singleResult = new ArrayList<>();
                                    singleResult.add(results.get(0));
                                    callback.onAlbumsRecognized(singleResult);
                                } else {
                                    // Correspondência incerta: envia todos para o menu de escolha
                                    callback.onAlbumsRecognized(results);
                                }

                            } else {
                                callback.onError("Album not found on Spotify.");
                            }
                        } catch (Exception e) {
                            callback.onError("Spotify search error: " + e.getMessage());
                        }
                    }).start();
                } else {
                    callback.onError("Could not read text or recognize album cover.");
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError("Error analyzing image: " + e.getMessage());
            }
        });
    }
}