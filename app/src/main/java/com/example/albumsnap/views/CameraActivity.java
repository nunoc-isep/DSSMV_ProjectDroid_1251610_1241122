package com.example.albumsnap.views;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.albumsnap.R;
import com.example.albumsnap.controllers.CameraController;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.util.ErrorMessages;
import com.example.albumsnap.views.adapters.AlbumAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final String KEY_PHOTO_URI = "photo_uri";
    private static final String KEY_PHOTO_PATH = "photo_path";
    private static final String PHOTO_PREFIX = "album_cover_";
    private static final String KEY_CAMERA_OPENED = "is_camera_opened";
    private static final String KEY_PROCESSING = "is_processing";

    private CameraController cameraController;
    private Uri photoUri;
    private File photoFile;   // ficheiro temporario da foto; apagado assim que a imagem e lida para memoria
    private boolean isCameraOpened = false;
    private boolean isProcessing = false;   // foto a ser analisada (nao se retoma se a Activity for recriada)

    private View layoutLoading;
    private TextView txtStatus;
    private BottomSheetDialog albumBottomSheet;

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && photoUri != null) {
                    try {
                        Bitmap fullBitmap = loadBitmapFromUri(photoUri);
                        deleteTempPhoto();   // a imagem ja esta em memoria: o ficheiro nao serve para mais nada
                        if (fullBitmap != null) {
                            processImage(fullBitmap);
                        } else {
                            finish();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading captured image", e);
                        deleteTempPhoto();
                        Toast.makeText(this, R.string.camera_error_load_image, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    deleteTempPhoto();   // foto cancelada: apaga o ficheiro vazio
                    finish();
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        layoutLoading = findViewById(R.id.layoutLoading);
        txtStatus = findViewById(R.id.txtStatus);

        // Unica forma de cancelar: botao "voltar" do Android (cancela a analise e fecha o ecra)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leaveScreen();
            }
        });

        cameraController = new CameraController(this);

        if (savedInstanceState != null) {
            photoUri = savedInstanceState.getParcelable(KEY_PHOTO_URI);
            String path = savedInstanceState.getString(KEY_PHOTO_PATH);
            if (path != null) photoFile = new File(path);
            isCameraOpened = savedInstanceState.getBoolean(KEY_CAMERA_OPENED, false);
            isProcessing = savedInstanceState.getBoolean(KEY_PROCESSING, false);
        }

        // Rede de seguranca: se o Android recriou o ecra (p.ex. a app foi morta em segundo plano)
        // a meio da analise, esta nao pode ser retomada. Sai em vez de ficar num ecra vazio.
        if (isProcessing) {
            finish();
            return;
        }

        if (!isCameraOpened) {
            isCameraOpened = true;
            checkPermissionAndOpenCamera();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_PHOTO_URI, photoUri);
        outState.putString(KEY_PHOTO_PATH, photoFile != null ? photoFile.getAbsolutePath() : null);
        outState.putBoolean(KEY_CAMERA_OPENED, isCameraOpened);
        outState.putBoolean(KEY_PROCESSING, isProcessing);
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            purgeOldPhotos();

            // Pasta de cache da app (privada; o Android tambem a pode limpar sozinho)
            File dir = new File(getCacheDir(), "images");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Cannot create " + dir);
            }
            photoFile = File.createTempFile(PHOTO_PREFIX, ".jpg", dir);

            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    photoFile
            );

            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating temporary file for camera", e);
            Toast.makeText(this, R.string.camera_error_temp_file, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** Apaga o ficheiro temporario da foto atual (se existir). */
    private void deleteTempPhoto() {
        if (photoFile != null) {
            if (photoFile.exists() && !photoFile.delete()) {
                Log.w(TAG, "Could not delete temp photo " + photoFile);
            }
            photoFile = null;
        }
        photoUri = null;
    }

    /** Remove fotos que tenham ficado para tras (crash, versoes anteriores da app). */
    private void purgeOldPhotos() {
        deleteMatching(new File(getCacheDir(), "images"));
        deleteMatching(getExternalFilesDir(Environment.DIRECTORY_PICTURES));   // local usado antes
    }

    private void deleteMatching(File dir) {
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(PHOTO_PREFIX) && !f.delete()) {
                Log.w(TAG, "Could not delete old photo " + f);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Bitmap loadBitmapFromUri(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source);
        } else {
            // Em Android < 9 o Bitmap vem sem a rotacao EXIF aplicada: aplica-se aqui
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            return applyExifRotation(bitmap, uri);
        }
    }

    /** Roda/espelha o bitmap conforme a orientacao EXIF do ficheiro (ImageDecoder ja o faz em Android 9+). */
    private Bitmap applyExifRotation(Bitmap bitmap, Uri uri) {
        if (bitmap == null) return null;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not read EXIF orientation", e);
        }

        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:   m.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:  m.postRotate(90);  m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.postScale(-1, 1); break;
            default: return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    /** Sai do ecra: cancela a analise em curso para que nenhum resultado chegue a uma Activity fechada. */
    private void leaveScreen() {
        cameraController.cancel();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraController != null) cameraController.cancel();
        if (isFinishing()) deleteTempPhoto();   // rede de seguranca (nao apaga numa simples rotacao do ecra)
        if (albumBottomSheet != null && albumBottomSheet.isShowing()) albumBottomSheet.dismiss();
    }

    private void processImage(Bitmap bitmap) {
        isProcessing = true;
        layoutLoading.setVisibility(View.VISIBLE);

        cameraController.processCapturedImage(bitmap, new CameraController.CameraCallback() {
            @Override
            public void onStatusUpdate(int statusRes) {
                runOnUiThread(() -> {
                    if (!isActivityAlive()) return;
                    txtStatus.setText(statusRes);
                });
            }

            @Override
            public void onAlbumsRecognized(List<Album> albums) {
                runOnUiThread(() -> {
                    if (!isActivityAlive()) return;
                    layoutLoading.setVisibility(View.GONE);
                    showAlbumBottomSheet(albums);
                });
            }

            @Override
            public void onError(int messageRes, String detail) {
                runOnUiThread(() -> {
                    if (!isActivityAlive()) return;
                    layoutLoading.setVisibility(View.GONE);
                    String message = getString(messageRes, ErrorMessages.localize(CameraActivity.this, detail));
                    Toast.makeText(CameraActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    /** Exibe um BottomSheet estilizado que permite rolagem interna sem fechar acidentalmente */
    private void showAlbumBottomSheet(List<Album> albums) {
        final BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        albumBottomSheet = bottomSheet;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_album_selection, null);

        ListView listView = dialogView.findViewById(R.id.listAlbums);
        TextView txtTitle = dialogView.findViewById(R.id.txtDialogTitle);
        txtTitle.setText(R.string.camera_choose_album);

        // Ativa a rolagem aninhada programaticamente para garantir compatibilidade
        listView.setNestedScrollingEnabled(true);

        AlbumAdapter albumAdapter = new AlbumAdapter(this, album -> {
            bottomSheet.dismiss();
            openAlbumDetails(album);
        });

        albumAdapter.setItems(albums);
        listView.setAdapter(albumAdapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            bottomSheet.dismiss();
            openAlbumDetails(albums.get(position));
        });

        // Executa o finish() apenas se o utilizador cancelar intencionalmente o diálogo
        bottomSheet.setOnCancelListener(dialog -> finish());

        bottomSheet.setContentView(dialogView);
        bottomSheet.show();
    }

    private void openAlbumDetails(Album album) {
        Intent intent = new Intent(CameraActivity.this, AlbumActivity.class);
        intent.putExtra(AlbumActivity.EXTRA_SPOTIFY_ID, album.getSpotifyId());
        intent.putExtra(AlbumActivity.EXTRA_FROM_SCAN, true);
        startActivity(intent);
        finish();
    }
}