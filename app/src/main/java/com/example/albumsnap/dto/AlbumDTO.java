package com.example.albumsnap.dto;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Album tal como vem no JSON do Spotify (pesquisa ou GET /albums/{id}). */
public class AlbumDTO implements Serializable{

    private String id;
    private String name;
    private String releaseDate;
    private int totalTracks;
    private String albumType;
    private String coverUrl;
    private List<ArtistDTO> artists = new ArrayList<>();
    private List<TrackDTO> tracks = new ArrayList<>();

    public AlbumDTO() {
    }

    public static AlbumDTO fromJson(JSONObject o) {
        AlbumDTO dto = new AlbumDTO();
        dto.id = JsonUtil.str(o, "id");
        dto.name = JsonUtil.str(o, "name");
        dto.releaseDate = JsonUtil.str(o, "release_date");
        dto.totalTracks = o.optInt("total_tracks", 0);
        dto.albumType = JsonUtil.str(o, "album_type");

        // O Spotify devolve as imagens da maior para a mais pequena
        JSONArray images = o.optJSONArray("images");
        if (images != null && images.length() > 0) {
            JSONObject first = images.optJSONObject(0);
            dto.coverUrl = JsonUtil.str(first, "url");
        } else {
            dto.coverUrl = "";
        }

        JSONArray artistArray = o.optJSONArray("artists");
        if (artistArray != null) {
            for (int i = 0; i < artistArray.length(); i++) {
                JSONObject artist = artistArray.optJSONObject(i);
                if (artist != null) dto.artists.add(ArtistDTO.fromJson(artist));
            }
        }

        JSONObject tracksObject = o.optJSONObject("tracks");
        if (tracksObject != null) {
            dto.tracks = tracksFromItems(tracksObject.optJSONArray("items"));
        }
        return dto;
    }

    /** Converte um array "items" de faixas. */
    public static List<TrackDTO> tracksFromItems(JSONArray items) {
        List<TrackDTO> list = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) list.add(TrackDTO.fromJson(item));
            }
        }
        return list;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public int getTotalTracks() {
        return totalTracks;
    }

    public void setTotalTracks(int totalTracks) {
        this.totalTracks = totalTracks;
    }

    public String getAlbumType() {
        return albumType;
    }

    public void setAlbumType(String albumType) {
        this.albumType = albumType;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public List<ArtistDTO> getArtists() {
        return artists;
    }

    public void setArtists(List<ArtistDTO> artists) {
        this.artists = artists;
    }

    public List<TrackDTO> getTracks() {
        return tracks;
    }

    public void setTracks(List<TrackDTO> tracks) {
        this.tracks = tracks;
    }
}
