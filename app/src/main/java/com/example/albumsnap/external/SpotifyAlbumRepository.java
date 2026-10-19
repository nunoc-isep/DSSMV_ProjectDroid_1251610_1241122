package com.example.albumsnap.external;

import com.example.albumsnap.dto.AlbumDTO;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.repository.AlbumRepository;
import com.example.albumsnap.services.FavouriteAlbumService;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

/** AlbumRepository com a Spotify Web API: GET /albums/{id}. */
public class SpotifyAlbumRepository implements AlbumRepository {

    private final SpotifyApiClient client;
    private final FavouriteAlbumService favouriteService;

    public SpotifyAlbumRepository(SpotifyApiClient client, FavouriteAlbumService favouriteService) {
        this.client = client;
        this.favouriteService = favouriteService;
    }

    @Override
    public Album findBySpotifyId(String spotifyId) throws IOException {
        String encoded = URLEncoder.encode(spotifyId, "UTF-8");
        JSONObject json = client.get("/albums/" + encoded);
        Album album = AlbumMapper.toDomain(AlbumDTO.fromJson(json));

        // Se as faixas nao vieram dentro do album, pede-as a parte (ate 50)
        if (album.getTracks().isEmpty() && album.getTotalTracks() > 0) {
            JSONObject page = client.get("/albums/" + encoded + "/tracks?limit=50");
            album.setTracks(AlbumMapper.toDomainTracks(
                    AlbumDTO.tracksFromItems(page.optJSONArray("items"))));
        }
        return album;
    }

    @Override
    public void save(Album album) throws IOException {
        favouriteService.addAlbum(album.getSpotifyId());
    }
}
