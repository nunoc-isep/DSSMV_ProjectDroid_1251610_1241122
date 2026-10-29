package com.example.albumsnap.views.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.albumsnap.R;
import com.example.albumsnap.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Linhas de faixas: numero, titulo, duracao (mm:ss) e botao de preview
 * (play / a carregar / pause).
 */
public class TrackAdapter extends BaseAdapter {

    /** Estado do botao de preview de uma faixa. */
    public enum PreviewState {
        IDLE, LOADING, PLAYING
    }

    /** Chamado quando o utilizador toca no botao de preview de uma faixa. */
    public interface OnPreviewClickListener {
        void onPreviewClick(Track track);
    }

    private final LayoutInflater inflater;
    private List<Track> items = new ArrayList<>();
    private OnPreviewClickListener previewListener;

    // Apenas uma faixa de cada vez tem preview ativa
    private String previewTrackId;
    private PreviewState previewState = PreviewState.IDLE;

    public TrackAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Track> newItems) {
        items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<Track>();
        notifyDataSetChanged();
    }

    public void setOnPreviewClickListener(OnPreviewClickListener listener) {
        this.previewListener = listener;
    }

    /** Atualiza o botao da faixa indicada; as restantes voltam a "play". */
    public void setPreviewState(String trackId, PreviewState state) {
        this.previewTrackId = trackId;
        this.previewState = state != null ? state : PreviewState.IDLE;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Track getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean isEnabled(int position) {
        return false;   // a linha nao e clicavel; so o botao de preview
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_track, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Track track = getItem(position);
        holder.number.setText(String.valueOf(track.getTrackNumber()));
        holder.title.setText(track.getTitle());
        holder.duration.setText(formatDuration(track.getDurationMs()));

        PreviewState state = Objects.equals(track.getSpotifyId(), previewTrackId)
                ? previewState : PreviewState.IDLE;
        bindPreviewButton(holder, state);
        holder.preview.setOnClickListener(v -> {
            if (previewListener != null) previewListener.onPreviewClick(track);
        });
        return convertView;
    }

    private void bindPreviewButton(ViewHolder holder, PreviewState state) {
        switch (state) {
            case LOADING:
                // O spinner fica por cima; o botao continua clicavel para cancelar
                holder.preview.setImageDrawable(null);
                holder.preview.setContentDescription(
                        holder.preview.getContext().getString(R.string.preview_pause_cd));
                holder.previewLoading.setVisibility(View.VISIBLE);
                break;
            case PLAYING:
                holder.preview.setImageResource(R.drawable.ic_pause);
                holder.preview.setContentDescription(
                        holder.preview.getContext().getString(R.string.preview_pause_cd));
                holder.previewLoading.setVisibility(View.GONE);
                break;
            default:
                holder.preview.setImageResource(R.drawable.ic_play);
                holder.preview.setContentDescription(
                        holder.preview.getContext().getString(R.string.preview_play_cd));
                holder.previewLoading.setVisibility(View.GONE);
                break;
        }
    }

    static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static class ViewHolder {
        final TextView number;
        final TextView title;
        final TextView duration;
        final ImageButton preview;
        final ProgressBar previewLoading;

        ViewHolder(View row) {
            number = row.findViewById(R.id.txtTrackNumber);
            title = row.findViewById(R.id.txtTrackTitle);
            duration = row.findViewById(R.id.txtTrackDuration);
            preview = row.findViewById(R.id.btnPreview);
            previewLoading = row.findViewById(R.id.progressPreview);
        }
    }
}
