package com.example.albumsnap.external;

import com.example.albumsnap.dto.AlbumDTO;
import com.example.albumsnap.dto.ArtistDTO;
import com.example.albumsnap.dto.TrackDTO;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Artist;
import com.example.albumsnap.model.Track;

import java.util.ArrayList;
import java.util.List;

/** Converte DTOs (formato do Spotify) em objetos do modelo de dominio. */
final class AlbumMapper {

    private AlbumMapper() {
    }

    static Album toDomain(AlbumDTO dto) {
        List<Artist> artists = new ArrayList<>();
        for (ArtistDTO a : dto.getArtists()) {
            artists.add(new Artist(a.getId(), a.getName()));
        }
        return new Album(dto.getId(), dto.getName(), dto.getReleaseDate(), dto.getTotalTracks(),
                dto.getAlbumType(), dto.getCoverUrl(), artists, toDomainTracks(dto.getTracks()));
    }

    static List<Track> toDomainTracks(List<TrackDTO> dtos) {
        List<Track> tracks = new ArrayList<>();
        for (TrackDTO t : dtos) {
            tracks.add(new Track(t.getId(), t.getName(), t.getDurationMs(), t.getTrackNumber()));
        }
        return tracks;
    }
}
