package com.example.albumsnap.views;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.albumsnap.R;
import com.example.albumsnap.controllers.CameraController;
import com.example.albumsnap.model.Album;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";

    private CameraController cameraController;
    private Uri photoUri;
    private boolean isCameraOpened = false;

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    try {
                        Bitmap fullBitmap = loadBitmapFromUri(photoUri);
                        if (fullBitmap != null) {
                            processImage(fullBitmap);
                        } else {
                            finish();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading captured image", e);
                        Toast.makeText(this, "Error loading captured image.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    finish();
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        cameraController = new CameraController(this);

        if (savedInstanceState == null && !isCameraOpened) {
            isCameraOpened = true;
            checkPermissionAndOpenCamera();
        }
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                File photoFile = File.createTempFile(
                        "album_cover_",
                        ".jpg",
                        getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                );

                photoUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        photoFile
                );

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                takePictureLauncher.launch(takePictureIntent);
            } catch (IOException e) {
                Log.e(TAG, "Error creating temporary file for camera", e);
                Toast.makeText(this, "Error creating temporary file.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Camera application not found.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Carrega o Bitmap de forma compatível com todas as versões do Android,
     * evitando o uso do método descontinuado get() do MediaStore.
     */
    @SuppressWarnings("deprecation")
    private Bitmap loadBitmapFromUri(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source);
        } else {
            return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
    }

    private void processImage(Bitmap bitmap) {
        Toast.makeText(this, "Analyzing album cover...", Toast.LENGTH_SHORT).show();

        cameraController.processCapturedImage(bitmap, new CameraController.CameraCallback() {
            @Override
            public void onAlbumsRecognized(List<Album> albums) {
                runOnUiThread(() -> {
                    if (albums.size() == 1) {
                        openAlbumDetails(albums.get(0));
                    } else {
                        showAlbumSelectionDialog(albums);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void showAlbumSelectionDialog(List<Album> albums) {
        String[] titles = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) {
            titles[i] = albums.get(i).getTitle();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose the correct album:");
        builder.setItems(titles, (dialog, which) -> {
            Album selectedAlbum = albums.get(which);
            openAlbumDetails(selectedAlbum);
        });
        builder.setOnCancelListener(dialog -> finish());
        builder.setNegativeButton("Cancel", (dialog, which) -> finish());
        builder.show();
    }

    private void openAlbumDetails(Album album) {
        Intent intent = new Intent(CameraActivity.this, AlbumActivity.class);
        intent.putExtra(AlbumActivity.EXTRA_SPOTIFY_ID, album.getSpotifyId());
        intent.putExtra(AlbumActivity.EXTRA_FROM_SCAN, true);
        startActivity(intent);
        finish();
    }
}