package com.example.albumsnap.views;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.albumsnap.R;
import com.example.albumsnap.controllers.LibraryController;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.util.Callback;
import com.example.albumsnap.util.EdgeInsets;
import com.example.albumsnap.views.adapters.AlbumAdapter;

import java.util.List;

/** Library: albuns guardados na app. */
public class LibraryActivity extends AppCompatActivity {

    private LibraryController controller;
    private AlbumAdapter adapter;
    private ListView listLibrary;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        EdgeInsets.applyTo(findViewById(R.id.libraryRoot));

        controller = LibraryController.create(this);
        listLibrary = findViewById(R.id.listLibrary);
        txtEmpty = findViewById(R.id.txtEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        adapter = new AlbumAdapter(this, new AlbumAdapter.Listener() {
            @Override
            public void onInfoClicked(Album album) {
                openAlbum(album);
            }
        });
        listLibrary.setAdapter(adapter);
        listLibrary.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openAlbum(adapter.getItem(position));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarrega sempre: o utilizador pode ter guardado/removido um album noutro ecra
        controller.getLibraryAlbums(new Callback<List<Album>>() {
            @Override
            public void onResult(List<Album> albums) {
                if (isFinishing() || isDestroyed()) return;
                adapter.setItems(albums);
                txtEmpty.setVisibility(albums.isEmpty() ? View.VISIBLE : View.GONE);
                listLibrary.setVisibility(albums.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                adapter.setItems(null);
                txtEmpty.setVisibility(View.VISIBLE);
                listLibrary.setVisibility(View.GONE);
            }
        });
    }

    private void openAlbum(Album album) {
        Intent intent = new Intent(this, AlbumActivity.class);
        intent.putExtra(AlbumActivity.EXTRA_SPOTIFY_ID, album.getSpotifyId());
        startActivity(intent);
    }
}
