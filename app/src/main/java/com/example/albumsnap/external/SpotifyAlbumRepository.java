package com.example.albumsnap.external;

import com.example.albumsnap.dto.AlbumDTO;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Track;
import com.example.albumsnap.repository.AlbumRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** AlbumRepository com a Spotify Web API: GET /albums/{id} (e as paginas de faixas que faltarem). */
public class SpotifyAlbumRepository implements AlbumRepository {

    private static final int TRACKS_PAGE_SIZE = 50;
    private static final int MAX_TRACK_PAGES = 40;   // limite de seguranca: 2000 faixas

    private final SpotifyApiClient client;

    public SpotifyAlbumRepository(SpotifyApiClient client) {
        this.client = client;
    }

    @Override
    public Album findBySpotifyId(String spotifyId) throws IOException {
        String encoded = URLEncoder.encode(spotifyId, "UTF-8");
        JSONObject json = client.get("/albums/" + encoded);
        Album album = AlbumMapper.toDomain(AlbumDTO.fromJson(json));

        // O pedido do album so traz as primeiras faixas: pede as restantes pagina a pagina.
        // "fetched" conta as entradas recebidas (mesmo as nulas) para o offset ficar certo.
        JSONObject embedded = json.optJSONObject("tracks");
        JSONArray embeddedItems = embedded != null ? embedded.optJSONArray("items") : null;
        int fetched = embeddedItems != null ? embeddedItems.length() : 0;
        int total = Math.max(album.getTotalTracks(), embedded != null ? embedded.optInt("total", 0) : 0);

        List<Track> tracks = new ArrayList<>(album.getTracks());
        Set<String> seen = new HashSet<>();
        for (Track t : tracks) seen.add(t.getSpotifyId());

        int pages = 0;
        while (fetched < total && pages++ < MAX_TRACK_PAGES) {
            JSONObject page = client.get("/albums/" + encoded + "/tracks?limit=" + TRACKS_PAGE_SIZE
                    + "&offset=" + fetched);
            JSONArray items = page.optJSONArray("items");
            if (items == null || items.length() == 0) break;

            fetched += items.length();   // a pagina pode vir mais pequena do que o pedido
            for (Track t : AlbumMapper.toDomainTracks(AlbumDTO.tracksFromItems(items))) {
                if (seen.add(t.getSpotifyId())) tracks.add(t);
            }
        }
        album.setTracks(tracks);
        return album;
    }
}
