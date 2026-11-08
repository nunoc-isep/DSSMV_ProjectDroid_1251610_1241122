package com.example.albumsnap.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Preparacao da foto antes de a enviar para a Cloud Vision: recorta o centro (80 %) e reduz sem
 * deformar nem ampliar. Usa bitmaps reais do Android.
 */
@RunWith(AndroidJUnit4.class)
public class VisionImagePrepInstrumentedTest {

    private final GoogleCloudVisionService service = new GoogleCloudVisionService();

    private static Bitmap blank(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    @Test
    public void aLargeLandscapePhoto_isReducedToAtMost1280PixelsKeepingItsShape() {
        Bitmap photo = blank(3000, 2000);

        Bitmap prepared = service.prepareForUpload(photo);

        assertTrue(Math.max(prepared.getWidth(), prepared.getHeight()) <= 1281);
        assertEquals(1280, prepared.getWidth(), 1);
        assertEquals(1.5, (double) prepared.getWidth() / prepared.getHeight(), 0.01);   // proporcao 3:2 intacta
    }

    @Test
    public void aLargePortraitPhoto_isNotDistorted() {
        Bitmap prepared = service.prepareForUpload(blank(2000, 3000));

        assertEquals(1280, prepared.getHeight(), 1);
        assertEquals(2.0 / 3.0, (double) prepared.getWidth() / prepared.getHeight(), 0.01);
    }

    @Test
    public void aSmallPhoto_isCroppedButNeverEnlarged() {
        Bitmap prepared = service.prepareForUpload(blank(400, 300));

        assertEquals(320, prepared.getWidth());    // 80 % de 400
        assertEquals(240, prepared.getHeight());   // 80 % de 300
    }

    @Test
    public void aSquarePhoto_staysSquare() {
        Bitmap prepared = service.prepareForUpload(blank(1000, 1000));

        assertEquals(800, prepared.getWidth());
        assertEquals(800, prepared.getHeight());
    }

    @Test
    public void onlyTheCentralPartIsKept_theBorderIsCutOff() {
        // Borda de 10 % em azul e centro vermelho: o recorte de 80 % deixa so o vermelho
        Bitmap photo = blank(1000, 1000);
        Canvas canvas = new Canvas(photo);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        canvas.drawRect(0, 0, 1000, 1000, paint);
        paint.setColor(Color.RED);
        canvas.drawRect(100, 100, 900, 900, paint);

        Bitmap prepared = service.prepareForUpload(photo);

        assertEquals(Color.RED, prepared.getPixel(0, 0));
        assertEquals(Color.RED, prepared.getPixel(prepared.getWidth() - 1, prepared.getHeight() - 1));
        assertEquals(Color.RED, prepared.getPixel(prepared.getWidth() / 2, prepared.getHeight() / 2));
    }

    @Test
    public void theOriginalPhotoIsNotModified() {
        Bitmap photo = blank(500, 500);

        Bitmap prepared = service.prepareForUpload(photo);

        assertNotSame(photo, prepared);
        assertEquals(500, photo.getWidth());
        assertEquals(500, photo.getHeight());
    }

    @Test
    public void aTinyPhoto_stillGivesAValidBitmap() {
        Bitmap prepared = service.prepareForUpload(blank(1, 1));

        assertEquals(1, prepared.getWidth());
        assertEquals(1, prepared.getHeight());
    }

    @Test
    public void noPhoto_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.prepareForUpload(null));
    }
}
