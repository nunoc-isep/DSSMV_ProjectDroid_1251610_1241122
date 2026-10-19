package com.example.albumsnap.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Carrega imagens de um URL para um ImageView (thread de fundo + cache em memoria).
 * Sem bibliotecas externas.
 */
public final class ImageLoader {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private ImageLoader() {
    }

    /**
     * @param url            endereco da imagem (pode ser null)
     * @param target         ImageView onde mostrar
     * @param placeholderRes imagem a mostrar enquanto carrega ou se falhar
     * @param reqSizePx      tamanho aproximado (px) em que vai ser mostrada, para poupar memoria
     */
    public static void load(final String url, final ImageView target, int placeholderRes, final int reqSizePx) {
        final String key = url + "@" + reqSizePx;
        target.setTag(key);

        if (url == null || url.isEmpty()) {
            target.setImageResource(placeholderRes);
            return;
        }

        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageResource(placeholderRes);
        Background.run(new Background.Task<Bitmap>() {
            @Override
            public Bitmap call() throws Exception {
                return download(url, reqSizePx);
            }
        }, new Callback<Bitmap>() {
            @Override
            public void onResult(Bitmap bitmap) {
                if (bitmap == null) return;
                CACHE.put(key, bitmap);
                // a view pode ter sido reutilizada para outra linha entretanto
                if (key.equals(target.getTag())) {
                    target.setImageBitmap(bitmap);
                }
            }

            @Override
            public void onError(String message) {
                // mantem o placeholder
            }
        });
    }

    private static Bitmap download(String urlString, int reqSizePx) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() != 200) return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            try (InputStream in = conn.getInputStream()) {
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            byte[] bytes = out.toByteArray();

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

            int sample = 1;
            int smallest = Math.min(bounds.outWidth, bounds.outHeight);
            while (reqSizePx > 0 && smallest / (sample * 2) >= reqSizePx) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } finally {
            conn.disconnect();
        }
    }
}
