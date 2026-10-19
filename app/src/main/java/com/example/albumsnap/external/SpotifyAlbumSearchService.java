package com.example.albumsnap.external;

import com.example.albumsnap.dto.AlbumDTO;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.RecognitionResult;
import com.example.albumsnap.repository.AlbumRepository;
import com.example.albumsnap.services.AlbumSearchService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** AlbumSearchService com a Spotify Web API: GET /search?type=album (maximo 10 resultados). */
public class SpotifyAlbumSearchService implements AlbumSearchService {

    private static final int MAX_RESULTS = 10;

    private final SpotifyApiClient client;
    private final AlbumRepository repository;

    public SpotifyAlbumSearchService(SpotifyApiClient client, AlbumRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @Override
    public List<Album> searchAlbums(RecognitionResult result) throws IOException {
        if (result == null) return new ArrayList<>();
        return searchAlbums(result.getQuery());
    }

    @Override
    public List<Album> searchAlbums(String query) throws IOException {
        List<Album> albums = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return albums;

        JSONObject json = client.get("/search?type=album&limit=" + MAX_RESULTS
                + "&q=" + URLEncoder.encode(query.trim(), "UTF-8"));
        JSONObject albumsObject = json.optJSONObject("albums");
        JSONArray items = albumsObject != null ? albumsObject.optJSONArray("items") : null;
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) albums.add(AlbumMapper.toDomain(AlbumDTO.fromJson(item)));
            }
        }
        return albums;
    }

    @Override
    public Album getAlbumDetails(String spotifyId) throws IOException {
        return repository.findBySpotifyId(spotifyId);
    }
}
