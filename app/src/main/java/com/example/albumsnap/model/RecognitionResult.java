package com.example.albumsnap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado do reconhecimento da imagem (domain_model.puml). */
public class RecognitionResult {

    private final double confidence;
    private final long matchedAt;
    private final String query;
    private final List<Album> candidates;

    /**
     * @param query texto que o reconhecimento sugeriu (usado para pesquisar no Spotify)
     */
    public RecognitionResult(double confidence, long matchedAt, String query, List<Album> candidates) {
        this.confidence = confidence;
        this.matchedAt = matchedAt;
        this.query = query;
        this.candidates = candidates != null ? new ArrayList<>(candidates) : new ArrayList<Album>();
    }

    public double getConfidence() {
        return confidence;
    }

    public long getMatchedAt() {
        return matchedAt;
    }

    public String getQuery() {
        return query;
    }

    public List<Album> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }
}
