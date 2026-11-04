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
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final String KEY_PHOTO_URI = "photo_uri";
    private static final String KEY_CAMERA_OPENED = "is_camera_opened";

    private CameraController cameraController;
    private Uri photoUri;
    private boolean isCameraOpened = false;

    private View layoutLoading;
    private TextView txtStatus;

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && photoUri != null) {
                    try {
                        Bitmap fullBitmap = loadBitmapFromUri(photoUri);
                        if (fullBitmap != null) {
                            processImage(fullBitmap);
                        } else {
                            finish();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading captured image", e);
                        Toast.makeText(this, R.string.camera_error_load_image, Toast.LENGTH_SHORT).show();
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

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        cameraController = new CameraController(this);

        if (savedInstanceState != null) {
            photoUri = savedInstanceState.getParcelable(KEY_PHOTO_URI);
            isCameraOpened = savedInstanceState.getBoolean(KEY_CAMERA_OPENED, false);
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
        outState.putBoolean(KEY_CAMERA_OPENED, isCameraOpened);
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

            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating temporary file for camera", e);
            Toast.makeText(this, R.string.camera_error_temp_file, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

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
        layoutLoading.setVisibility(View.VISIBLE);

        cameraController.processCapturedImage(bitmap, new CameraController.CameraCallback() {
            @Override
            public void onStatusUpdate(int statusRes) {
                runOnUiThread(() -> txtStatus.setText(statusRes));
            }

            @Override
            public void onAlbumsRecognized(List<Album> albums) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    if (albums.size() == 1) {
                        openAlbumDetails(albums.get(0));
                    } else {
                        showAlbumBottomSheet(albums);
                    }
                });
            }

            @Override
            public void onError(int messageRes, String detail) {
                runOnUiThread(() -> {
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
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
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