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

        @SerializedName("pagesWithMatchingImages")
        private List<WebPage> pagesWithMatchingImages;

        @SerializedName("fullMatchingImages")
        private List<WebImage> fullMatchingImages;

        @SerializedName("partialMatchingImages")
        private List<WebImage> partialMatchingImages;

        public List<WebEntity> getWebEntities() {
            return webEntities;
        }

        public List<BestGuessLabel> getBestGuessLabels() {
            return bestGuessLabels;
        }

        public List<WebPage> getPagesWithMatchingImages() {
            return pagesWithMatchingImages;
        }

        public List<WebImage> getFullMatchingImages() {
            return fullMatchingImages;
        }

        public List<WebImage> getPartialMatchingImages() {
            return partialMatchingImages;
        }
    }

    /** Imagem encontrada na web igual (ou parecida) a enviada. */
    public static class WebImage {
        @SerializedName("url")
        private String url;

        public String getUrl() {
            return url;
        }
    }

    /** Pagina web onde a imagem (ou parte dela) aparece. */
    public static class WebPage {
        @SerializedName("url")
        private String url;

        @SerializedName("pageTitle")
        private String pageTitle;

        @SerializedName("fullMatchingImages")
        private List<WebImage> fullMatchingImages;

        @SerializedName("partialMatchingImages")
        private List<WebImage> partialMatchingImages;

        public List<WebImage> getFullMatchingImages() {
            return fullMatchingImages;
        }

        public List<WebImage> getPartialMatchingImages() {
            return partialMatchingImages;
        }

        public String getUrl() {
            return url;
        }

        public String getPageTitle() {
            return pageTitle;
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