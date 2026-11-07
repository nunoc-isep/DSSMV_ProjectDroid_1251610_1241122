package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.dto.AlbumDTO;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Track;
import com.example.albumsnap.testutil.SpotifyJson;

import org.json.JSONArray;
import org.junit.Test;

public class AlbumMapperTest {

    @Test
    public void toDomain_copiesAlbumFieldsArtistsAndTracks() throws Exception {
        JSONArray tracks = new JSONArray()
                .put(SpotifyJson.track("t1", "One", 1, 1, SpotifyJson.artist("a1", "Guest")))
                .put(SpotifyJson.track("t2", "Two", 1, 2));

        Album album = AlbumMapper.toDomain(AlbumDTO.fromJson(SpotifyJson.album("ALB", "Double", 2, tracks)));

        assertEquals("ALB", album.getSpotifyId());
        assertEquals("Double", album.getTitle());
        assertEquals("2020", album.getReleaseYear());
        assertEquals(2, album.getTotalTracks());
        assertEquals("album", album.getAlbumType());
        assertEquals("https://img.example/big.jpg", album.getCoverUrl());
        assertEquals("The Artist", album.getArtistNames());
        assertEquals(2, album.getTracks().size());
    }

    @Test
    public void toDomainTracks_keepsDiscNumberAndTrackArtists() throws Exception {
        JSONArray items = new JSONArray()
                .put(SpotifyJson.track("t1", "One", 1, 1, SpotifyJson.artist("a1", "Guest")))
                .put(SpotifyJson.track("t2", "Two", 1, 2));

        java.util.List<Track> tracks = AlbumMapper.toDomainTracks(AlbumDTO.tracksFromItems(items));

        assertEquals(1, tracks.get(0).getDiscNumber());
        assertEquals("Guest", tracks.get(0).getArtists().get(0).getName());
        assertEquals(2, tracks.get(1).getDiscNumber());
        assertEquals(1, tracks.get(1).getTrackNumber());   // a numeracao reinicia em cada disco
        assertTrue(tracks.get(1).getArtists().isEmpty());
    }
}
