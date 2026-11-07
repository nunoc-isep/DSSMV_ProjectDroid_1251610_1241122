package com.example.albumsnap.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.albumsnap.testutil.SpotifyJson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** Leitura dos JSON do Spotify para DTOs. */
public class DtoParsingTest {

    @Test
    public void albumDto_readsAllFields() throws Exception {
        JSONObject json = SpotifyJson.album("ALB", "Big Album", 3, SpotifyJson.tracks(1, 3));

        AlbumDTO dto = AlbumDTO.fromJson(json);

        assertEquals("ALB", dto.getId());
        assertEquals("Big Album", dto.getName());
        assertEquals("2020-05-01", dto.getReleaseDate());
        assertEquals(3, dto.getTotalTracks());
        assertEquals("album", dto.getAlbumType());
        assertEquals("https://img.example/big.jpg", dto.getCoverUrl());   // a primeira (maior) imagem
        assertEquals(1, dto.getArtists().size());
        assertEquals("The Artist", dto.getArtists().get(0).getName());
        assertEquals(3, dto.getTracks().size());
    }

    @Test
    public void albumDto_withoutImagesOrTracks_usesEmptyValues() {
        AlbumDTO dto = AlbumDTO.fromJson(new JSONObject());

        assertEquals("", dto.getId());
        assertEquals("", dto.getName());
        assertEquals("", dto.getCoverUrl());
        assertEquals(0, dto.getTotalTracks());
        assertTrue(dto.getArtists().isEmpty());
        assertTrue(dto.getTracks().isEmpty());
    }

    @Test
    public void albumDto_jsonNullIsNotTheText_null() throws Exception {
        JSONObject json = new JSONObject().put("id", "A1").put("name", JSONObject.NULL);

        assertEquals("", AlbumDTO.fromJson(json).getName());
    }

    @Test
    public void trackDto_readsDiscNumberAndArtists() throws Exception {
        JSONObject json = SpotifyJson.track("t9", "Song", 5, 2,
                SpotifyJson.artist("a1", "First"), SpotifyJson.artist("a2", "Second"));

        TrackDTO dto = TrackDTO.fromJson(json);

        assertEquals("t9", dto.getId());
        assertEquals("Song", dto.getName());
        assertEquals(200_000, dto.getDurationMs());
        assertEquals(5, dto.getTrackNumber());
        assertEquals(2, dto.getDiscNumber());
        assertEquals(2, dto.getArtists().size());
        assertEquals("Second", dto.getArtists().get(1).getName());
    }

    @Test
    public void trackDto_missingDiscNumber_defaultsToOne() throws Exception {
        JSONObject json = new JSONObject().put("id", "t").put("name", "S");

        TrackDTO dto = TrackDTO.fromJson(json);

        assertEquals(1, dto.getDiscNumber());
        assertEquals(0, dto.getTrackNumber());
        assertEquals(0, dto.getDurationMs());
        assertTrue(dto.getArtists().isEmpty());
    }

    @Test
    public void tracksFromItems_skipsNullEntriesAndAcceptsNull() throws Exception {
        JSONArray items = new JSONArray();
        items.put(SpotifyJson.track("t1", "One", 1, 1));
        items.put(JSONObject.NULL);   // o Spotify pode devolver entradas null
        items.put(SpotifyJson.track("t2", "Two", 2, 1));

        List<TrackDTO> tracks = AlbumDTO.tracksFromItems(items);

        assertEquals(2, tracks.size());
        assertEquals("t2", tracks.get(1).getId());
        assertTrue(AlbumDTO.tracksFromItems(null).isEmpty());
    }
}
