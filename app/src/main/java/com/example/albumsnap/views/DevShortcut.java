package com.example.albumsnap.views;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.albumsnap.controllers.AlbumController;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.util.Callback;

import java.util.List;

/**
 * TEMPORARIO (so para testar, ate a camara existir).
 *
 * Carregar sem soltar no botao da camara abre uma caixa para pesquisar um album pelo
 * nome. Ao escolher um resultado, o album abre como se tivesse sido digitalizado
 * (aparece na caixa Recent) e ja podes guardar na Library e nos favoritos.
 *
 * Para remover: apagar este ficheiro e a linha "DevShortcut.attach(...)" da MainMenuActivity.
 */
final class DevShortcut {

    private DevShortcut() {
    }

    static void attach(final Activity activity, View trigger) {
        trigger.setOnLongClickListener(v -> {
            askQuery(activity);
            return true;
        });
    }

    private static void askQuery(final Activity activity) {
        final EditText input = new EditText(activity);
        input.setHint("Album name, e.g. Abbey Road");
        input.setSingleLine(true);

        new AlertDialog.Builder(activity)
                .setTitle("TEMP: search an album")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> search(activity, input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void search(final Activity activity, String query) {
        AlbumController.create(activity).searchAlbums(query, new Callback<List<Album>>() {
            @Override
            public void onResult(List<Album> albums) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (albums.isEmpty()) {
                    Toast.makeText(activity, "No results", Toast.LENGTH_SHORT).show();
                } else {
                    showResults(activity, albums);
                }
            }

            @Override
            public void onError(String message) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void showResults(final Activity activity, final List<Album> albums) {
        CharSequence[] labels = new CharSequence[albums.size()];
        for (int i = 0; i < albums.size(); i++) {
            Album album = albums.get(i);
            labels[i] = album.getTitle() + " - " + album.getArtistNames();
        }

        new AlertDialog.Builder(activity)
                .setTitle("Pick an album (counts as a scan)")
                .setItems(labels, (dialog, which) -> {
                    Intent intent = new Intent(activity, AlbumActivity.class);
                    intent.putExtra(AlbumActivity.EXTRA_SPOTIFY_ID, albums.get(which).getSpotifyId());
                    intent.putExtra(AlbumActivity.EXTRA_FROM_SCAN, true);
                    activity.startActivity(intent);
                })
                .show();
    }
}
