package com.example.albumsnap.external;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Base64;
import android.util.Log;

import com.example.albumsnap.BuildConfig;
import com.example.albumsnap.services.ImageRecognitionService;
import com.example.albumsnap.dto.VisionResponseDTO;
import com.example.albumsnap.util.SearchQuery;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleCloudVisionService implements ImageRecognitionService {

    private static final String TAG = "GoogleCloudVisionService";
    private static final String API_KEY = BuildConfig.VISION_API_KEY;

    /**
     * Um so cliente para a app toda (cada OkHttpClient tem o seu proprio pool de ligacoes e threads),
     * criado so na primeira chamada de rede: as funcoes que apenas leem o JSON nao o chegam a criar.
     */
    private static final class Http {
        static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    private static final String VISION_URL = "https://vision.googleapis.com/v1/images:annotate?key=" + API_KEY;

    // Lado maior da imagem enviada (so se reduz, nunca se amplia) e parte central usada
    private static final int MAX_SIDE_PX = 1280;
    private static final float CROP_RATIO = 0.80f;

    // Limites das pesquisas geradas a partir da resposta do Vision
    private static final int OCR_MAX_WORDS = 8;
    private static final int OCR_HEAD_WORDS = 4;
    private static final int MAX_ENTITIES = 2;
    private static final int MAX_PAGE_TITLES = 2;
    private static final int MAX_SPOTIFY_IDS = 2;
    private static final float MIN_ENTITY_SCORE = 0.3f;

    @Override
    public void analyzeImage(Bitmap bitmap, VisionCallback callback) {
        new Thread(() -> {
            final String result;
            try {
                // 1+2. Recortar a área central (80%) e reduzir mantendo a proporção
                Bitmap resizedBitmap = prepareForUpload(bitmap);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
                resizedBitmap.recycle();   // e uma copia nossa; o bitmap original pertence a quem o enviou
                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                // 3. Montar o pedido JSON (método extraído)
                JSONObject requestBodyJson = buildRequestBodyJson(base64Image);

                // 4. Executar chamada HTTP POST
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(requestBodyJson.toString(), mediaType);

                Request request = new Request.Builder()
                        .url(VISION_URL)
                        .post(body)
                        .build();

                try (Response response = Http.CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Vision API HTTP " + response.code());
                    }
                    // response.body() é garantido não ser nulo quando response.isSuccessful() é true
                    result = response.body().string();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao processar imagem na Cloud Vision API", e);
                callback.onError(e);
                return;
            }
            // Fora do try: um erro dentro do onSuccess nao pode voltar a chegar ao onError
            callback.onSuccess(result);
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
        webFeature.put("maxResults", 20);   // mais paginas = mais hipoteses de achar o link do Spotify
        featuresArray.put(webFeature);

        requestItem.put("features", featuresArray);
        requestsArray.put(requestItem);
        requestBodyJson.put("requests", requestsArray);

        return requestBodyJson;
    }

    /**
     * Recorta a parte central da imagem e reduz-a (sem a deformar nem ampliar) para o lado
     * maior ter no maximo MAX_SIDE_PX. A reducao faz-se ja ao copiar a regiao recortada,
     * por isso nunca existe uma copia intermedia do tamanho original.
     */
    private Bitmap prepareForUpload(Bitmap src) {
        if (src == null) throw new IllegalArgumentException("bitmap is null");
        int width = src.getWidth();
        int height = src.getHeight();

        int cropWidth = Math.max(1, (int) (width * CROP_RATIO));
        int cropHeight = Math.max(1, (int) (height * CROP_RATIO));
        int startX = (width - cropWidth) / 2;
        int startY = (height - cropHeight) / 2;

        float scale = Math.min(1f, (float) MAX_SIDE_PX / Math.max(cropWidth, cropHeight));
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        return Bitmap.createBitmap(src, startX, startY, cropWidth, cropHeight, matrix, true);
    }

    /**
     * Le o JSON da resposta da Cloud Vision (uma so vez) e devolve a primeira resposta, ou null
     * se o JSON vier vazio ou invalido. As funcoes parse... e describeResponse recebem este objeto.
     */
    public static VisionResponseDTO.AnnotateImageResponse parseResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return null;
        try {
            VisionResponseDTO dto = new Gson().fromJson(jsonResponse, VisionResponseDTO.class);
            if (dto == null || dto.getResponses() == null || dto.getResponses().isEmpty()) return null;
            return dto.getResponses().get(0);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao ler a resposta da Cloud Vision", e);
            return null;
        }
    }

    /**
     * Pesquisas candidatas para o Spotify, da mais para a menos fiavel:
     * 1. melhor palpite web do Google ("artista album ... album cover" sem o sufixo);
     * 2. texto OCR da capa, limpo e limitado a poucas palavras;
     * 3. titulos de paginas de sites de musica onde a capa aparece ("Album - Artista | Spotify");
     * 4. entidades web com boa pontuacao (normalmente artista e/ou album);
     * 5. so o inicio do texto OCR, quando este e longo (a capa costuma ter artista e titulo primeiro).
     * Nunca devolve rotulos genericos ("Poster", "Font", ...), mas nomes como "Art Blakey" ou
     * "Earth, Wind & Fire" passam.
     */
    public static List<String> parseSearchQueries(VisionResponseDTO.AnnotateImageResponse response) {
        Set<String> queries = new LinkedHashSet<>();
        if (response == null) {
            return new ArrayList<>(queries);
        }

        try {
            VisionResponseDTO.WebDetection web = response.getWebDetection();

            // 1. Melhor palpite do Google
            if (web != null && web.getBestGuessLabels() != null && !web.getBestGuessLabels().isEmpty()) {
                addIfUseful(queries, web.getBestGuessLabels().get(0).getLabel());
            }

            // 2. OCR (ignora o ruido que o OCR le em capas sem texto)
            String ocr = "";
            if (response.getFullTextAnnotation() != null) {
                ocr = SearchQuery.firstWords(
                        SearchQuery.clean(response.getFullTextAnnotation().getText()), OCR_MAX_WORDS);
                if (SearchQuery.isMeaningfulText(ocr)) {
                    addIfUseful(queries, ocr);
                } else {
                    ocr = "";
                }
            }

            // 3. Titulos das paginas (sites de musica) onde a capa aparece
            if (web != null && web.getPagesWithMatchingImages() != null) {
                int added = 0;
                for (VisionResponseDTO.WebPage page : web.getPagesWithMatchingImages()) {
                    if (added >= MAX_PAGE_TITLES) break;
                    if (SearchQuery.isMusicSite(page.getUrl())
                            && addIfUseful(queries, SearchQuery.fromPageTitle(page.getPageTitle()))) {
                        added++;
                    }
                }
            }

            // 4. Entidades web
            if (web != null && web.getWebEntities() != null) {
                int added = 0;
                for (VisionResponseDTO.WebEntity entity : web.getWebEntities()) {
                    if (added >= MAX_ENTITIES) break;
                    if (entity.getScore() > MIN_ENTITY_SCORE && addIfUseful(queries, entity.getDescription())) {
                        added++;
                    }
                }
            }

            // 5. Inicio do OCR
            if (SearchQuery.wordCount(ocr) > OCR_HEAD_WORDS + 1) {
                addIfUseful(queries, SearchQuery.firstWords(ocr, OCR_HEAD_WORDS));
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao analisar o DTO da resposta da Cloud Vision", e);
        }

        return new ArrayList<>(queries);
    }

    /**
     * Ids de albuns do Spotify encontrados nos URLs das paginas onde a capa aparece
     * (open.spotify.com/album/...). Os mais repetidos primeiro. Uma pagina de album do Spotify
     * com esta imagem e quase de certeza o album certo, sem precisar de pesquisar por texto.
     */
    public static List<String> parseSpotifyAlbumIds(VisionResponseDTO.AnnotateImageResponse response) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (response == null) return new ArrayList<>();

        try {
            VisionResponseDTO.WebDetection web = response.getWebDetection();
            if (web == null || web.getPagesWithMatchingImages() == null) return new ArrayList<>();

            for (VisionResponseDTO.WebPage page : web.getPagesWithMatchingImages()) {
                String id = SearchQuery.spotifyAlbumId(page.getUrl());
                if (id != null) {
                    Integer n = counts.get(id);
                    counts.put(id, n == null ? 1 : n + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao procurar links do Spotify na resposta da Cloud Vision", e);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        java.util.Collections.sort(entries, (a, b) -> b.getValue() - a.getValue());   // sort estavel
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries) {
            if (ids.size() >= MAX_SPOTIFY_IDS) break;
            ids.add(e.getKey());
        }
        return ids;
    }

    /**
     * Identificadores das imagens de capa do Spotify (i.scdn.co) que o Google encontrou iguais
     * a foto. Se uma capa candidata tiver o mesmo identificador, e exatamente a mesma imagem.
     * Funciona mesmo em capas sem texto.
     */
    public static Set<String> parseCoverKeys(VisionResponseDTO.AnnotateImageResponse response) {
        Set<String> keys = new LinkedHashSet<>();
        if (response == null) return keys;

        try {
            VisionResponseDTO.WebDetection web = response.getWebDetection();
            if (web == null) return keys;

            addCoverKeys(keys, web.getFullMatchingImages());
            addCoverKeys(keys, web.getPartialMatchingImages());
            if (web.getPagesWithMatchingImages() != null) {
                for (VisionResponseDTO.WebPage page : web.getPagesWithMatchingImages()) {
                    addCoverKeys(keys, page.getFullMatchingImages());
                    addCoverKeys(keys, page.getPartialMatchingImages());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao procurar capas do Spotify na resposta da Cloud Vision", e);
        }
        return keys;
    }

    private static void addCoverKeys(Set<String> keys, List<VisionResponseDTO.WebImage> images) {
        if (images == null) return;
        for (VisionResponseDTO.WebImage image : images) {
            String key = SearchQuery.spotifyCoverKey(image.getUrl());
            if (key != null) keys.add(key);
        }
    }

    /** Resumo curto da resposta (para o logcat): palpite, entidades, paginas e imagens iguais. */
    public static String describeResponse(VisionResponseDTO.AnnotateImageResponse r) {
        if (r == null) return "(vazia)";
        StringBuilder sb = new StringBuilder();
        try {

            String ocr = r.getFullTextAnnotation() != null ? r.getFullTextAnnotation().getText() : null;
            sb.append("ocr=").append(ocr == null ? "" : ocr.replace('\n', ' ').trim());

            VisionResponseDTO.WebDetection web = r.getWebDetection();
            if (web == null) return sb.append(" | sem webDetection").toString();

            if (web.getBestGuessLabels() != null && !web.getBestGuessLabels().isEmpty()) {
                sb.append(" | bestGuess=").append(web.getBestGuessLabels().get(0).getLabel());
            }
            if (web.getWebEntities() != null) {
                sb.append(" | entities=");
                int n = 0;
                for (VisionResponseDTO.WebEntity e : web.getWebEntities()) {
                    if (n++ >= 8) break;
                    sb.append(e.getDescription()).append('(').append(e.getScore()).append(") ");
                }
            }
            if (web.getPagesWithMatchingImages() != null) {
                sb.append(" | pages=").append(web.getPagesWithMatchingImages().size()).append(": ");
                int n = 0;
                for (VisionResponseDTO.WebPage p : web.getPagesWithMatchingImages()) {
                    if (n++ >= 5) break;
                    sb.append(p.getPageTitle()).append(" <").append(p.getUrl()).append("> ");
                }
            }
            sb.append(" | fullMatchingImages=")
                    .append(web.getFullMatchingImages() == null ? 0 : web.getFullMatchingImages().size())
                    .append(" partial=")
                    .append(web.getPartialMatchingImages() == null ? 0 : web.getPartialMatchingImages().size());
        } catch (Exception e) {
            sb.append(" | erro a descrever: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private static boolean addIfUseful(Set<String> queries, String raw) {
        String cleaned = SearchQuery.clean(raw);
        if (SearchQuery.isGeneric(cleaned)) return false;
        for (String existing : queries) {
            if (existing.equalsIgnoreCase(cleaned)) return false;
        }
        return queries.add(cleaned);
    }
}
