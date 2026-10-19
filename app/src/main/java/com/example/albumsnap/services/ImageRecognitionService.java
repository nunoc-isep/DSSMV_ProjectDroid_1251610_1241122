package com.example.albumsnap.services;

import android.graphics.Bitmap;

public interface ImageRecognitionService {

    interface VisionCallback {
        void onSuccess(String jsonResult);
        void onError(Exception e);
    }

    void analyzeImage(Bitmap bitmap, VisionCallback callback);
}