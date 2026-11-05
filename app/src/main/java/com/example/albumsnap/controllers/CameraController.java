package com.example.albumsnap.controllers;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

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

import com.example.albumsnap.util.AlbumMatcher;
import com.example.albumsnap.util.SearchQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CameraController {

    private final AlbumSearchService albumSearchService;
    private final ImageRecognitionService imageRecognitionService;

    private static final int MIN_SEARCHES = 3;   // pesquisas feitas antes de parar, se ja ha resultados
    private static final int MAX_SEARCHES = 5;
    private static final int MAX_CANDIDATES = 10;
    private static final int PER_QUERY_QUOTA = 2;   // cada pesquisa garante estes lugares no menu
    private static final String TAG = "AlbumSnapVision";

    // Passa a true quando o utilizador sai do ecra (voltar): os resultados em curso sao ignorados
    private volatile boolean cancelled = false;

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

    /** Cancela o processamento em curso: nenhum callback voltara a ser chamado. */
    public void cancel() {
        cancelled = true;
    }

    public void processCapturedImage(Bitmap photo, CameraCallback callback) {
        cancelled = false;

        // Notifica etapa 1: Análise visual (Google Vision)
        callback.onStatusUpdate(R.string.status_analyzing_vision);

        imageRecognitionService.analyzeImage(photo, new ImageRecognitionService.VisionCallback() {
            @Override
            public void onSuccess(String jsonResult) {
                if (cancelled) return;
                final List<String> queries = GoogleCloudVisionService.parseSearchQueries(jsonResult);
                final List<String> spotifyIds = GoogleCloudVisionService.parseSpotifyAlbumIds(jsonResult);
                final Set<String> coverKeys = GoogleCloudVisionService.parseCoverKeys(jsonResult);
                Log.d(TAG, "vision: " + GoogleCloudVisionService.describeResponse(jsonResult));
                Log.d(TAG, "queries=" + queries + " spotifyIds=" + spotifyIds + " coverKeys=" + coverKeys);

                if (!queries.isEmpty() || !spotifyIds.isEmpty()) {
                    // Notifica etapa 2: Pesquisa no Spotify
                    callback.onStatusUpdate(R.string.status_searching_spotify);

                    new Thread(() -> {
                        try {
                            Outcome outcome = searchAndRank(queries, spotifyIds, coverKeys);
                            List<Album> ranked = outcome.albums;
                            if (cancelled) return;

                            if (ranked.isEmpty()) {
                                callback.onError(R.string.error_album_not_found, null);
                            } else if (outcome.exactCoverMatch || AlbumMatcher.isConfident(ranked, queries)) {
                                List<Album> singleResult = new ArrayList<>();
                                singleResult.add(ranked.get(0));
                                callback.onAlbumsRecognized(singleResult);
                            } else {
                                callback.onAlbumsRecognized(ranked);
                            }
                        } catch (Exception e) {
                            if (cancelled) return;
                            callback.onError(R.string.error_spotify_search, e.getMessage());
                        }
                    }).start();
                } else {
                    callback.onError(R.string.error_no_text, null);
                }
            }

            @Override
            public void onError(Exception e) {
                if (cancelled) return;
                callback.onError(R.string.error_analyze_image, e.getMessage());
            }
        });
    }

    /** Resultado da pesquisa: albuns por ordem e se um deles tem exatamente a mesma capa da foto. */
    private static final class Outcome {
        final List<Album> albums;
        final boolean exactCoverMatch;

        Outcome(List<Album> albums, boolean exactCoverMatch) {
            this.albums = albums;
            this.exactCoverMatch = exactCoverMatch;
        }
    }

    /**
     * Junta candidatos de varias fontes e ordena-os:
     * 1. albuns cujo link do Spotify apareceu junto da capa;
     * 2. albuns cuja capa e exatamente a imagem da foto (mesmo identificador i.scdn.co);
     * 3. resultados das pesquisas por texto, por semelhanca com o que foi lido. Cada pesquisa
     *    garante os seus primeiros resultados no menu, para uma pesquisa fraca (p.ex. um
     *    objeto que o Google viu na imagem) nao ocupar todos os lugares.
     * Faz sempre pelo menos MIN_SEARCHES pesquisas (se houver tantas).
     */
    private Outcome searchAndRank(List<String> queries, List<String> spotifyIds, Set<String> coverKeys)
            throws IOException {
        Map<String, List<Album>> perQuery = new LinkedHashMap<>();
        List<Album> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        IOException firstError = null;
        int searches = 0;

        // Albuns cujo link do Spotify apareceu junto da capa
        List<Album> fromLinks = new ArrayList<>();
        for (String id : spotifyIds) {
            if (cancelled) return new Outcome(merged, false);
            try {
                Album album = albumSearchService.getAlbumDetails(id);
                if (album != null && seen.add(album.getSpotifyId())) fromLinks.add(album);
            } catch (IOException e) {
                if (firstError == null) firstError = e;   // ignora: ainda ha a pesquisa por texto
            }
        }

        for (String query : queries) {
            if (cancelled) return new Outcome(merged, false);
            if (searches >= MAX_SEARCHES || (searches >= MIN_SEARCHES && !merged.isEmpty())) break;
            searches++;

            try {
                List<Album> results = albumSearchService.searchAlbums(query);
                if (results == null) continue;
                perQuery.put(query, results);
                for (Album album : results) {
                    if (seen.add(album.getSpotifyId())) merged.add(album);
                }
            } catch (IOException e) {
                if (firstError == null) firstError = e;   // tenta as restantes pesquisas
            }
        }

        if (merged.isEmpty() && fromLinks.isEmpty() && firstError != null) throw firstError;

        // Albuns com exatamente a mesma capa da foto (inclui os dos links)
        List<Album> sameCover = new ArrayList<>();
        List<Album> rest = new ArrayList<>();
        for (Album album : merged) {
            String key = SearchQuery.spotifyCoverKey(album.getCoverUrl());
            if (key != null && coverKeys.contains(key)) sameCover.add(album); else rest.add(album);
        }
        boolean exact = sameCover.size() == 1;
        Log.d(TAG, "candidatos=" + merged.size() + " links=" + fromLinks.size()
                + " mesmaCapa=" + sameCover.size());

        // Lugares do menu que sobram para as pesquisas por texto
        int slots = Math.max(MAX_CANDIDATES - fromLinks.size() - sameCover.size(), 1);

        List<Album> byScore = AlbumMatcher.rank(rest, queries);
        Set<String> guaranteed = new HashSet<>();
        for (List<Album> results : perQuery.values()) {
            for (int i = 0; i < PER_QUERY_QUOTA && i < results.size(); i++) {
                guaranteed.add(results.get(i).getSpotifyId());
            }
        }
        List<Album> chosen = new ArrayList<>();
        for (Album a : byScore) {
            if (chosen.size() < slots && guaranteed.contains(a.getSpotifyId())) chosen.add(a);
        }
        for (Album a : byScore) {
            if (chosen.size() < slots && !chosen.contains(a)) chosen.add(a);
        }

        List<Album> ranked = new ArrayList<>(fromLinks);
        for (Album a : sameCover) if (!ranked.contains(a)) ranked.add(a);
        ranked.addAll(AlbumMatcher.rank(chosen, queries));
        if (ranked.size() > MAX_CANDIDATES) ranked = new ArrayList<>(ranked.subList(0, MAX_CANDIDATES));

        // Um unico album com exatamente a mesma capa: abre-o sem perguntar
        if (exact) {
            List<Album> only = new ArrayList<>();
            only.add(sameCover.get(0));
            return new Outcome(only, true);
        }
        return new Outcome(ranked, false);
    }
}
