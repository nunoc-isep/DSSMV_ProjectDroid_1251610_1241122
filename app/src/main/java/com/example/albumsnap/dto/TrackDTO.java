package com.example.albumsnap.dto;

import org.json.JSONObject;

/** Faixa tal como vem no JSON do Spotify. */
public class TrackDTO {

    private String id;
    private String name;
    private long durationMs;
    private int trackNumber;

    public TrackDTO() {
    }

    public static TrackDTO fromJson(JSONObject o) {
        TrackDTO dto = new TrackDTO();
        dto.id = JsonUtil.str(o, "id");
        dto.name = JsonUtil.str(o, "name");
        dto.durationMs = o.optLong("duration_ms", 0);
        dto.trackNumber = o.optInt("track_number", 0);
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
}
