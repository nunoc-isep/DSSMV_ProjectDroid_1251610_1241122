package com.example.albumsnap.views;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.albumsnap.R;
import com.example.albumsnap.controllers.LibraryController;
import com.example.albumsnap.external.SpotifyAuthManager;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.util.Callback;
import com.example.albumsnap.util.EdgeInsets;
import com.example.albumsnap.views.adapters.AlbumAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu principal: Library, Settings, botao da camara e caixa "Recent".
 * A caixa mostra o ultimo album digitalizado; ao tocar expande para os ultimos 10.
 */
public class MainMenuActivity extends AppCompatActivity {

    private static final int EXPANDED_LIST_HEIGHT_DP = 340;

    private LibraryController controller;
    private AlbumAdapter recentAdapter;
    private OnBackPressedCallback collapseOnBack;

    private View recentHeader;
    private ImageView imgExpand;
    private TextView txtRecentEmpty;
    private ListView listRecent;

    private List<Album> recentAlbums = new ArrayList<>();
    private boolean expanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
        EdgeInsets.applyTo(findViewById(R.id.main));

        controller = LibraryController.create(this);

        recentHeader = findViewById(R.id.recentHeader);
        imgExpand = findViewById(R.id.imgExpand);
        txtRecentEmpty = findViewById(R.id.txtRecentEmpty);
        listRecent = findViewById(R.id.listRecent);

        findViewById(R.id.btnLibrary).setOnClickListener(v ->
                startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnCamera).setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class)));

        //DevShortcut.attach(this, findViewById(R.id.btnCamera));   // TEMPORARIO: apagar

        recentAdapter = new AlbumAdapter(this, new AlbumAdapter.Listener() {
            @Override
            public void onInfoClicked(Album album) {
                openAlbum(album);
            }
        });
        listRecent.setAdapter(recentAdapter);
        listRecent.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!expanded) {
                    setExpanded(true);          // tocar na caixa fechada expande-a
                } else {
                    openAlbum(recentAdapter.getItem(position));
                }
            }
        });
        recentHeader.setOnClickListener(v -> {
            if (!recentAlbums.isEmpty()) setExpanded(!expanded);
        });

        // O botao "voltar" fecha primeiro a lista expandida
        collapseOnBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                setExpanded(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, collapseOnBack);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SpotifyAuthManager.getInstance(this).isAuthenticated()) {
            goToLogin();
            return;
        }
        loadRecent();
    }

    private void loadRecent() {
        controller.getRecentScans(new Callback<List<Album>>() {
            @Override
            public void onResult(List<Album> albums) {
                if (isFinishing() || isDestroyed()) return;
                recentAlbums = albums;
                if (recentAlbums.isEmpty()) expanded = false;
                renderRecent();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                recentAlbums = new ArrayList<>();
                expanded = false;
                renderRecent();
            }
        });
    }

    private void setExpanded(boolean value) {
        expanded = value;
        renderRecent();
    }

    private void renderRecent() {
        boolean empty = recentAlbums.isEmpty();
        txtRecentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
        imgExpand.setVisibility(empty ? View.GONE : View.VISIBLE);
        imgExpand.setImageResource(expanded ? R.drawable.ic_expand_more : R.drawable.ic_expand_less);
        collapseOnBack.setEnabled(expanded);

        // Fechada: so o ultimo album. Aberta: ate 10.
        List<Album> shown = empty ? recentAlbums
                : (expanded ? recentAlbums : recentAlbums.subList(0, 1));
        recentAdapter.setItems(shown);

        ViewGroup.LayoutParams params = listRecent.getLayoutParams();
        params.height = expanded
                ? Math.round(EXPANDED_LIST_HEIGHT_DP * getResources().getDisplayMetrics().density)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        listRecent.setLayoutParams(params);
    }

    private void openAlbum(Album album) {
        Intent intent = new Intent(this, AlbumActivity.class);
        intent.putExtra(AlbumActivity.EXTRA_SPOTIFY_ID, album.getSpotifyId());
        startActivity(intent);
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
