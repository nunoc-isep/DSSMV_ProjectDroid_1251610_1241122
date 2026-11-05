package com.example.albumsnap.views;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.albumsnap.R;
import com.example.albumsnap.controllers.AlbumController;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.model.Playlist;
import com.example.albumsnap.model.Track;
import com.example.albumsnap.util.Callback;
import com.example.albumsnap.util.EdgeInsets;
import com.example.albumsnap.util.ErrorMessages;
import com.example.albumsnap.util.ImageLoader;
import com.example.albumsnap.util.PreviewPlayer;
import com.example.albumsnap.views.adapters.TrackAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AlbumView do diagrama: capa, nome, artista, outras informacoes e faixas, com os botoes
 * "Save to Library" (da app) e "Add to Spotify favourites". Cada faixa tem um botao
 * play/pause para ouvir a preview de 30 s e um menu de 3 pontos para a adicionar aos favoritos
 * do Spotify ou a uma playlist da conta.
 *
 * Extras: EXTRA_SPOTIFY_ID (obrigatorio) e EXTRA_FROM_SCAN (true quando vem de uma
 * digitalizacao, para o album aparecer na caixa Recent).
 */
public class AlbumActivity extends AppCompatActivity {

    public static final String EXTRA_SPOTIFY_ID = "spotify_id";
    public static final String EXTRA_FROM_SCAN = "from_scan";

    private static final int COVER_SIZE_PX = 800;

    private AlbumController controller;
    private String spotifyId;
    private Album album;
    private boolean inLibrary;

    private ProgressBar progress;
    private TextView txtError;
    private View header;
    private ImageView imgCover;
    private TextView txtAlbumName;
    private TextView txtArtist;
    private TextView txtInfo;
    private Button btnSaveLibrary;
    private Button btnFavourite;
    private TrackAdapter trackAdapter;

    // Previews: so uma faixa de cada vez; o URL fica em cache enquanto o ecra existe
    private PreviewPlayer previewPlayer;
    private String previewTrackId;                                   // faixa a carregar ou a tocar
    private final Map<String, String> previewUrls = new HashMap<>();  // "" = sem preview

    private boolean loadingPlaylists;   // evita pedir a lista duas vezes se o utilizador tocar de novo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);
        EdgeInsets.applyTo(findViewById(R.id.albumRoot));

        progress = findViewById(R.id.progress);
        txtError = findViewById(R.id.txtError);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        spotifyId = getIntent().getStringExtra(EXTRA_SPOTIFY_ID);
        if (spotifyId == null || spotifyId.isEmpty()) {
            showError(getString(R.string.error_missing_album));
            return;
        }

        // Cabecalho da lista: tudo o que esta acima das faixas
        ListView listTracks = findViewById(R.id.listTracks);
        header = getLayoutInflater().inflate(R.layout.header_album, listTracks, false);
        listTracks.addHeaderView(header, null, false);
        trackAdapter = new TrackAdapter(this);
        listTracks.setAdapter(trackAdapter);
        trackAdapter.setOnPreviewClickListener(this::onPreviewClick);
        trackAdapter.setOnTrackMenuClickListener(this::onTrackMenuClick);

        previewPlayer = new PreviewPlayer(new PreviewPlayer.Listener() {
            @Override
            public void onPlaying() {
                if (previewTrackId != null) {
                    trackAdapter.setPreviewState(previewTrackId, TrackAdapter.PreviewState.PLAYING);
                }
            }

            @Override
            public void onPaused() {
                if (previewTrackId != null) {
                    trackAdapter.setPreviewState(previewTrackId, TrackAdapter.PreviewState.IDLE);
                }
            }

            @Override
            public void onFinished() {
                stopPreview();
            }

            @Override
            public void onError() {
                stopPreview();
                Toast.makeText(AlbumActivity.this, R.string.preview_error, Toast.LENGTH_SHORT).show();
            }
        });
        header.setVisibility(View.INVISIBLE);

        imgCover = header.findViewById(R.id.imgCover);
        txtAlbumName = header.findViewById(R.id.txtAlbumName);
        txtArtist = header.findViewById(R.id.txtArtist);
        txtInfo = header.findViewById(R.id.txtInfo);
        btnSaveLibrary = header.findViewById(R.id.btnSaveLibrary);
        btnFavourite = header.findViewById(R.id.btnFavourite);
        btnSaveLibrary.setEnabled(false);
        btnFavourite.setEnabled(false);
        btnSaveLibrary.setOnClickListener(v -> toggleLibrary());
        btnFavourite.setOnClickListener(v -> addToSpotifyFavourites());

        controller = AlbumController.create(this);
        loadAlbum();
    }

    private void loadAlbum() {
        progress.setVisibility(View.VISIBLE);
        txtError.setVisibility(View.GONE);

        controller.getAlbum(spotifyId, new Callback<Album>() {
            @Override
            public void onResult(Album result) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                album = result;
                displayAlbum(result);

                if (getIntent().getBooleanExtra(EXTRA_FROM_SCAN, false)) {
                    controller.registerScan(result);
                }
                controller.isInLibrary(spotifyId, new Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean saved) {
                        if (isFinishing() || isDestroyed()) return;
                        inLibrary = saved;
                        updateLibraryButton();
                    }

                    @Override
                    public void onError(String message) {
                        updateLibraryButton();
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                showError(message);
            }
        });
    }

    /** displayAlbum do diagrama de classes. */
    private void displayAlbum(Album a) {
        header.setVisibility(View.VISIBLE);
        txtAlbumName.setText(a.getTitle());
        txtArtist.setText(a.getArtistNames());
        txtInfo.setText(buildInfoLine(a));
        ImageLoader.load(a.getCoverUrl(), imgCover, R.drawable.ic_album_placeholder, COVER_SIZE_PX);
        trackAdapter.setItems(a.getTracks());
        btnFavourite.setEnabled(true);
    }

    private String buildInfoLine(Album a) {
        StringBuilder sb = new StringBuilder();
        if (!a.getReleaseYear().isEmpty()) sb.append(a.getReleaseYear());

        int total = a.getTotalTracks() > 0 ? a.getTotalTracks() : a.getTracks().size();
        if (total > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(getResources().getQuantityString(R.plurals.track_count, total, total));
        }

        String type = typeLabel(a.getAlbumType());
        if (!type.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(type);
        }
        return sb.toString();
    }

    private String typeLabel(String albumType) {
        if (albumType == null) return "";
        switch (albumType) {
            case "album":
                return getString(R.string.album_type_album);
            case "single":
                return getString(R.string.album_type_single);
            case "compilation":
                return getString(R.string.album_type_compilation);
            default:
                return albumType;
        }
    }

    private void updateLibraryButton() {
        btnSaveLibrary.setText(inLibrary ? R.string.album_remove_library : R.string.album_save_library);
        btnSaveLibrary.setEnabled(album != null);
    }

    private void toggleLibrary() {
        if (album == null) return;
        btnSaveLibrary.setEnabled(false);

        if (inLibrary) {
            controller.removeFromLibrary(spotifyId, new Callback<Boolean>() {
                @Override
                public void onResult(Boolean ok) {
                    if (isFinishing() || isDestroyed()) return;
                    inLibrary = false;
                    updateLibraryButton();
                    Toast.makeText(AlbumActivity.this, R.string.toast_removed, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    updateLibraryButton();
                    Toast.makeText(AlbumActivity.this, ErrorMessages.localize(AlbumActivity.this, message),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            controller.saveToLibrary(album, new Callback<Boolean>() {
                @Override
                public void onResult(Boolean ok) {
                    if (isFinishing() || isDestroyed()) return;
                    inLibrary = true;
                    updateLibraryButton();
                    Toast.makeText(AlbumActivity.this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    updateLibraryButton();
                    Toast.makeText(AlbumActivity.this, ErrorMessages.localize(AlbumActivity.this, message),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void addToSpotifyFavourites() {
        btnFavourite.setEnabled(false);
        controller.addToSpotifyFavourites(spotifyId, new Callback<Boolean>() {
            @Override
            public void onResult(Boolean ok) {
                if (isFinishing() || isDestroyed()) return;
                btnFavourite.setText(R.string.album_favourite_added);   // fica desativado
                Toast.makeText(AlbumActivity.this, R.string.toast_favourite_ok, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                btnFavourite.setEnabled(true);
                Toast.makeText(AlbumActivity.this, ErrorMessages.localize(AlbumActivity.this, message),
                            Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------ menu da faixa (3 pontos)

    private void onTrackMenuClick(final Track track, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.menu_track);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_track_favourite) {
                addTrackToFavourites(track);
                return true;
            }
            if (id == R.id.action_track_playlist) {
                chooseAndAddToPlaylist(track);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void addTrackToFavourites(final Track track) {
        if (track.getSpotifyId() == null || track.getSpotifyId().isEmpty()) {
            Toast.makeText(this, R.string.error_spotify_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        controller.addTrackToSpotifyFavourites(track.getSpotifyId(), new Callback<Boolean>() {
            @Override
            public void onResult(Boolean ok) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(AlbumActivity.this,
                        getString(R.string.toast_track_favourite_ok, track.getTitle()),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                showToastError(message);
            }
        });
    }

    /** Pede as playlists da conta, mostra uma lista para escolher e adiciona a faixa a escolhida. */
    private void chooseAndAddToPlaylist(final Track track) {
        if (loadingPlaylists) return;
        if (track.getSpotifyId() == null || track.getSpotifyId().isEmpty()) {
            Toast.makeText(this, R.string.error_spotify_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        loadingPlaylists = true;
        Toast.makeText(this, R.string.playlist_loading, Toast.LENGTH_SHORT).show();

        controller.getEditablePlaylists(new Callback<List<Playlist>>() {
            @Override
            public void onResult(List<Playlist> playlists) {
                loadingPlaylists = false;
                if (isFinishing() || isDestroyed()) return;
                if (playlists.isEmpty()) {
                    Toast.makeText(AlbumActivity.this, R.string.playlist_none, Toast.LENGTH_LONG).show();
                    return;
                }
                showPlaylistPicker(track, playlists);
            }

            @Override
            public void onError(String message) {
                loadingPlaylists = false;
                if (isFinishing() || isDestroyed()) return;
                showToastError(message);
            }
        });
    }

    private void showPlaylistPicker(final Track track, final List<Playlist> playlists) {
        String[] names = new String[playlists.size()];
        for (int i = 0; i < names.length; i++) {
            String name = playlists.get(i).getName();
            names[i] = name.isEmpty() ? getString(R.string.playlist_unnamed) : name;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playlist_choose)
                .setItems(names, (dialog, which) ->
                        addTrackToPlaylist(track, playlists.get(which), names[which]))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void addTrackToPlaylist(final Track track, Playlist playlist, final String playlistName) {
        controller.addTrackToPlaylist(playlist.getSpotifyId(), track.getSpotifyId(), new Callback<Boolean>() {
            @Override
            public void onResult(Boolean ok) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(AlbumActivity.this,
                        getString(R.string.toast_track_playlist_ok, track.getTitle(), playlistName),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                showToastError(message);
            }
        });
    }

    /** Erro numa chamada as playlists/favoritos. 403 aqui costuma ser falta de permissao (scope). */
    private void showToastError(String message) {
        String text = ErrorMessages.SPOTIFY_FORBIDDEN.equals(message)
                ? getString(R.string.error_playlist_permission)
                : ErrorMessages.localize(this, message);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    /** Toque no botao de uma faixa: alterna entre Play, Pause e troca de faixa. */
    private void onPreviewClick(final Track track) {
        final String id = track.getSpotifyId();

        // Clicou na mesma faixa que ja esta ativa
        if (Objects.equals(id, previewTrackId)) {
            if (previewPlayer.isPlaying()) {
                previewPlayer.pause();
            } else if (previewPlayer.isPaused()) {
                previewPlayer.play(previewUrls.get(id));
            }
            return;
        }

        // Clicou numa faixa diferente
        stopPreview();
        previewTrackId = id;
        trackAdapter.setPreviewState(id, TrackAdapter.PreviewState.LOADING);

        String cached = previewUrls.get(id);
        if (cached != null) {
            startPreview(cached);
            return;
        }

        controller.getPreviewUrl(album, track, new Callback<String>() {
            @Override
            public void onResult(String url) {
                if (isFinishing() || isDestroyed()) return;

                String finalUrl = (url != null) ? url : "";
                previewUrls.put(id, finalUrl);

                if (!Objects.equals(id, previewTrackId)) return;
                startPreview(finalUrl);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                if (!Objects.equals(id, previewTrackId)) return;
                stopPreview();
                Toast.makeText(AlbumActivity.this, R.string.preview_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startPreview(String url) {
        if (url == null || url.isEmpty()) {
            stopPreview();
            Toast.makeText(this, R.string.preview_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        previewPlayer.play(url);   // o botao passa a pause quando o audio comecar (onPlaying)
    }

    /** Para o audio (se houver) e poe todos os botoes outra vez em "play". */
    private void stopPreview() {
        if (previewPlayer != null) previewPlayer.stop();
        previewTrackId = null;
        if (trackAdapter != null) {
            trackAdapter.setPreviewState(null, TrackAdapter.PreviewState.IDLE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPreview();   // a preview nao continua com a app em segundo plano
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewPlayer != null) previewPlayer.release();
    }

    private void showError(String message) {
        txtError.setText(ErrorMessages.localize(this, message));
        txtError.setVisibility(View.VISIBLE);
    }
}