package com.example.albumsnap.external;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.example.albumsnap.BuildConfig;
import com.example.albumsnap.services.ImageRecognitionService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleCloudVisionService implements ImageRecognitionService {

    private static final String TAG = "GoogleCloudVisionService";
    private static final String API_KEY = BuildConfig.VISION_API_KEY;
    private static final String VISION_URL = "https://vision.googleapis.com/v1/images:annotate?key=" + API_KEY;

    @Override
    public void analyzeImage(Bitmap bitmap, VisionCallback callback) {
        new Thread(() -> {
            try {
                // 1. Recortar a área central (80%) para remover ruído de fundo
                Bitmap croppedBitmap = cropCenter(bitmap);

                // 2. Redimensionar para tamanho ideal para a Cloud Vision API
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, 800, 800, true);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                // 3. Montar o pedido JSON (método extraído)
                JSONObject requestBodyJson = buildRequestBodyJson(base64Image);

                // 4. Executar chamada HTTP POST
                OkHttpClient client = new OkHttpClient();
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(requestBodyJson.toString(), mediaType);

                Request request = new Request.Builder()
                        .url(VISION_URL)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        // response.body() é garantido não ser nulo quando response.isSuccessful() é true
                        String result = response.body().string();
                        callback.onSuccess(result);
                    } else {
                        callback.onError(new Exception("Erro na API: " + response.code()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao processar imagem na Cloud Vision API", e);
                callback.onError(e);
            }
        }).start();
    }

    /**
     * Constrói a estrutura do objeto JSON enviado para a Vision API.
     */
    private JSONObject buildRequestBodyJson(String base64Image) throws JSONException {
        JSONObject requestBodyJson = new JSONObject();
        JSONArray requestsArray = new JSONArray();

        JSONObject requestItem = new JSONObject();
        JSONObject imageObject = new JSONObject();
        imageObject.put("content", base64Image);
        requestItem.put("image", imageObject);

        JSONArray featuresArray = new JSONArray();

        // Deteção de Texto (OCR)
        JSONObject textFeature = new JSONObject();
        textFeature.put("type", "TEXT_DETECTION");
        featuresArray.put(textFeature);

        // Deteção Web (Pesquisa Visual)
        JSONObject webFeature = new JSONObject();
        webFeature.put("type", "WEB_DETECTION");
        webFeature.put("maxResults", 10);
        featuresArray.put(webFeature);

        requestItem.put("features", featuresArray);
        requestsArray.put(requestItem);
        requestBodyJson.put("requests", requestsArray);

        return requestBodyJson;
    }

    private Bitmap cropCenter(Bitmap src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();

        int cropWidth = (int) (width * 0.80);
        int cropHeight = (int) (height * 0.80);
        int startX = (width - cropWidth) / 2;
        int startY = (height - cropHeight) / 2;

        return Bitmap.createBitmap(src, startX, startY, cropWidth, cropHeight);
    }

    public static String parseDetectedText(String jsonResponse) {
        try {
            JSONObject root = new JSONObject(jsonResponse);
            JSONArray responses = root.optJSONArray("responses");
            if (responses == null || responses.length() == 0) return "";

            JSONObject firstResponse = responses.getJSONObject(0);

            // 1. Tentar OCR primeiro (Texto impresso na capa)
            JSONObject fullTextAnnotation = firstResponse.optJSONObject("fullTextAnnotation");
            if (fullTextAnnotation != null) {
                String text = fullTextAnnotation.optString("text", "").replace("\n", " ").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }

            // 2. Se não houver texto, analisar a WEB_DETECTION
            JSONObject webDetection = firstResponse.optJSONObject("webDetection");
            if (webDetection != null) {

                JSONArray webEntities = webDetection.optJSONArray("webEntities");
                if (webEntities != null && webEntities.length() > 0) {
                    for (int i = 0; i < webEntities.length(); i++) {
                        JSONObject entity = webEntities.getJSONObject(i);
                        String description = entity.optString("description", "").trim();
                        float score = (float) entity.optDouble("score", 0.0);

                        // Condição direta sem negação
                        if (!description.isEmpty() && isSpecificLabel(description) && score > 0.3) {
                            return description;
                        }
                    }
                }

                JSONArray bestGuessLabels = webDetection.optJSONArray("bestGuessLabels");
                if (bestGuessLabels != null && bestGuessLabels.length() > 0) {
                    String bestGuess = bestGuessLabels.getJSONObject(0).optString("label", "").trim();
                    // Condição direta sem negação
                    if (!bestGuess.isEmpty() && isSpecificLabel(bestGuess)) {
                        return bestGuess;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao analisar o JSON de resposta da Cloud Vision", e);
        }
        return "";
    }

    /**
     * Verifica se o rótulo é específico e relevante (não genérico).
     */
    private static boolean isSpecificLabel(String label) {
        String lower = label.toLowerCase();
        boolean isGeneric = lower.contains("art") || lower.contains("illustration") ||
                lower.contains("poster") || lower.contains("album cover") ||
                lower.contains("graphic design") || lower.contains("font") ||
                lower.contains("picture") || lower.contains("image") ||
                lower.contains("soundtrack") || lower.contains("compilation");

        return !isGeneric;
    }
}