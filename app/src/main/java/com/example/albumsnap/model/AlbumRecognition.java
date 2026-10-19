package com.example.albumsnap.model;

/** Um pedido de reconhecimento: imagem usada, estado e resultado (domain_model.puml). */
public class AlbumRecognition {

    private final String id;
    private final long recognizedAt;
    private final AlbumCoverImage image;
    private RecognitionStatus status;
    private RecognitionResult result;

    public AlbumRecognition(String id, long recognizedAt, AlbumCoverImage image) {
        this.id = id;
        this.recognizedAt = recognizedAt;
        this.image = image;
        this.status = RecognitionStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public long getRecognizedAt() {
        return recognizedAt;
    }

    public AlbumCoverImage getImage() {
        return image;
    }

    public RecognitionStatus getStatus() {
        return status;
    }

    public void setStatus(RecognitionStatus status) {
        this.status = status;
    }

    public RecognitionResult getResult() {
        return result;
    }

    public void setResult(RecognitionResult result) {
        this.result = result;
    }
}
