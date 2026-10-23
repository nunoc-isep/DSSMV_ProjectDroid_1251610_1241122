package com.example.albumsnap.views;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;
import android.widget.ImageButton;

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

    private CameraController cameraController;
    private Uri photoUri;
    private boolean isCameraOpened = false; // Flag para evitar abertura dupla de câmera

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    try {
                        Bitmap fullBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoUri);
                        if (fullBitmap != null) {
                            processImage(fullBitmap);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error loading captured image.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    // Se o utilizador recusar a permissão
                    Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        // Configura o botão de voltar
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        cameraController = new CameraController(this);
        checkPermissionAndOpenCamera();

        if(savedInstanceState == null && !isCameraOpened){
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
                e.printStackTrace();
                Toast.makeText(this, "Error creating temporary file.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Camera application not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage(Bitmap bitmap) {
        Toast.makeText(this, "Analyzing album cover...", Toast.LENGTH_SHORT).show();

        cameraController.processCapturedImage(bitmap, new CameraController.CameraCallback() {
            @Override
            public void onAlbumsRecognized(List<Album> albums) {
                runOnUiThread(() -> {
                    if (albums.size() == 1) {
                        // 1 Resultado: Abre diretamente
                        openAlbumDetails(albums.get(0));
                    } else {
                        // Múltiplos Resultados: Mostra o Menu de Escolha
                        showAlbumSelectionDialog(albums);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {Toast.makeText(CameraActivity.this, message, Toast.LENGTH_LONG).show();});
            }
        });
    }

    private void showAlbumSelectionDialog(List<Album> albums) {
        String[] titles = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) {
            // Utiliza o getter correto da classe Album (getTitle)
            titles[i] = albums.get(i).getTitle();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose the correct album:");
        builder.setItems(titles, (dialog, which) -> {
            Album selectedAlbum = albums.get(which);
            openAlbumDetails(selectedAlbum);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void openAlbumDetails(Album album) {
        Intent intent = new Intent(CameraActivity.this, AlbumActivity.class);
        // Utiliza o getter correto da classe Album (getSpotifyId)
        intent.putExtra(AlbumActivity.EXTRA_SPOTIFY_ID, album.getSpotifyId());
        intent.putExtra(AlbumActivity.EXTRA_FROM_SCAN, true);
        startActivity(intent);
        finish();
    }
}