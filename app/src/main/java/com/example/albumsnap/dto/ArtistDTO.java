package com.example.albumsnap.dto;

import org.json.JSONObject;
import java.io.Serializable;

/** Artista tal como vem no JSON do Spotify. */
public class ArtistDTO implements Serializable{

    private String id;
    private String name;

    public ArtistDTO() {
    }

    public static ArtistDTO fromJson(JSONObject o) {
        ArtistDTO dto = new ArtistDTO();
        dto.id = JsonUtil.str(o, "id");
        dto.name = JsonUtil.str(o, "name");
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
}
