package com.example.albumsnap.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VisionResponseDTO {

    @SerializedName("responses")
    private List<AnnotateImageResponse> responses;

    public List<AnnotateImageResponse> getResponses() {
        return responses;
    }

    public static class AnnotateImageResponse {
        @SerializedName("fullTextAnnotation")
        private FullTextAnnotation fullTextAnnotation;

        @SerializedName("webDetection")
        private WebDetection webDetection;

        public FullTextAnnotation getFullTextAnnotation() {
            return fullTextAnnotation;
        }

        public WebDetection getWebDetection() {
            return webDetection;
        }
    }

    public static class FullTextAnnotation {
        @SerializedName("text")
        private String text;

        public String getText() {
            return text;
        }
    }

    public static class WebDetection {
        @SerializedName("webEntities")
        private List<WebEntity> webEntities;

        @SerializedName("bestGuessLabels")
        private List<BestGuessLabel> bestGuessLabels;

        public List<WebEntity> getWebEntities() {
            return webEntities;
        }

        public List<BestGuessLabel> getBestGuessLabels() {
            return bestGuessLabels;
        }
    }

    public static class WebEntity {
        @SerializedName("description")
        private String description;

        @SerializedName("score")
        private float score;

        public String getDescription() {
            return description;
        }

        public float getScore() {
            return score;
        }
    }

    public static class BestGuessLabel {
        @SerializedName("label")
        private String label;

        public String getLabel() {
            return label;
        }
    }
}