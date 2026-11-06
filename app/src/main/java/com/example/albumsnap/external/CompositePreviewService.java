package com.example.albumsnap.external;

import android.util.Log;

import com.example.albumsnap.model.PreviewQuery;
import com.example.albumsnap.services.PreviewService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tenta varias fontes de previews pela ordem dada e devolve a primeira que tiver a faixa.
 * Se uma fonte falhar (rede, limite de pedidos), passa a seguinte; so da erro se TODAS falharem.
 */
public class CompositePreviewService implements PreviewService {

    private static final String TAG = "CompositePreview";

    private final List<PreviewService> sources;

    public CompositePreviewService(PreviewService... sources) {
        this.sources = new ArrayList<>(Arrays.asList(sources));
    }

    @Override
    public String findPreviewUrl(PreviewQuery query) throws IOException {
        IOException lastError = null;
        boolean anyAnswered = false;

        for (PreviewService source : sources) {
            try {
                String url = source.findPreviewUrl(query);
                anyAnswered = true;
                if (url != null && !url.isEmpty()) return url;
            } catch (IOException e) {
                Log.w(TAG, source.getClass().getSimpleName() + ": " + e.getMessage());
                lastError = e;
            }
        }
        if (!anyAnswered && lastError != null) throw lastError;
        return null;
    }
}
