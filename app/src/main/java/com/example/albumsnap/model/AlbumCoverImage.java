package com.example.albumsnap.model;

/** Fotografia da capa tirada pelo utilizador (domain_model.puml). */
public class AlbumCoverImage {

    private final String id;
    private final String imageUri;
    private final long capturedAt;
    private final int width;
    private final int height;

    public AlbumCoverImage(String id, String imageUri, long capturedAt, int width, int height) {
        this.id = id;
        this.imageUri = imageUri;
        this.capturedAt = capturedAt;
        this.width = width;
        this.height = height;
    }

    public String getId() {
        return id;
    }

    public String getImageUri() {
        return imageUri;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
