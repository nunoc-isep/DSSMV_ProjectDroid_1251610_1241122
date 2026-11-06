package com.example.albumsnap.dto;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Faixa tal como vem no JSON do Spotify. */
public class TrackDTO implements Serializable{

    private String id;
    private String name;
    private long durationMs;
    private int trackNumber;
    private int discNumber = 1;
    private List<ArtistDTO> artists = new ArrayList<>();

    public TrackDTO() {
    }

    public static TrackDTO fromJson(JSONObject o) {
        TrackDTO dto = new TrackDTO();
        dto.id = JsonUtil.str(o, "id");
        dto.name = JsonUtil.str(o, "name");
        dto.durationMs = o.optLong("duration_ms", 0);
        dto.trackNumber = o.optInt("track_number", 0);
        dto.discNumber = o.optInt("disc_number", 1);

        JSONArray artistArray = o.optJSONArray("artists");
        if (artistArray != null) {
            for (int i = 0; i < artistArray.length(); i++) {
                JSONObject artist = artistArray.optJSONObject(i);
                if (artist != null) dto.artists.add(ArtistDTO.fromJson(artist));
            }
        }
        return dto;
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
    }

    public int getDiscNumber() {
        return discNumber;
    }

    public void setDiscNumber(int discNumber) {
        this.discNumber = discNumber;
    }

    public List<ArtistDTO> getArtists() {
        return artists;
    }

    public void setArtists(List<ArtistDTO> artists) {
        this.artists = artists;
    }
}
