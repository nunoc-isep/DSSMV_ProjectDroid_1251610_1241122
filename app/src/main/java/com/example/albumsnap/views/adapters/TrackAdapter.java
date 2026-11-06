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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Linhas de faixas: numero, titulo, duracao (mm:ss) e botao de preview
 * (play / a carregar / pause) e menu de 3 pontos. Nos albuns com varios discos, cada disco
 * comeca com um cabecalho "Disco N".
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

    /** Chamado quando o utilizador toca nos 3 pontos de uma faixa ("anchor" e o botao tocado). */
    public interface OnTrackMenuClickListener {
        void onTrackMenuClick(Track track, View anchor);
    }

    private static final int TYPE_TRACK = 0;
    private static final int TYPE_DISC = 1;

    /** Uma linha da lista: uma faixa, ou o cabecalho de um disco (track == null). */
    private static final class Row {
        final Track track;
        final int disc;

        Row(Track track, int disc) {
            this.track = track;
            this.disc = disc;
        }
    }

    private final LayoutInflater inflater;
    private List<Row> rows = new ArrayList<>();
    private OnPreviewClickListener previewListener;
    private OnTrackMenuClickListener menuListener;

    // Apenas uma faixa de cada vez tem preview ativa
    private String previewTrackId;
    private PreviewState previewState = PreviewState.IDLE;

    public TrackAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Track> newItems) {
        rows = buildRows(newItems);
        notifyDataSetChanged();
    }

    /** Ordena por disco e, so se houver mais de um disco, mete um cabecalho antes de cada um. */
    private static List<Row> buildRows(List<Track> tracks) {
        List<Row> out = new ArrayList<>();
        if (tracks == null) return out;

        List<Track> sorted = new ArrayList<>(tracks);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.getDiscNumber(), b.getDiscNumber()));   // estavel

        Set<Integer> discs = new HashSet<>();
        for (Track t : sorted) discs.add(t.getDiscNumber());
        boolean multiDisc = discs.size() > 1;

        int lastDisc = Integer.MIN_VALUE;
        for (Track t : sorted) {
            if (multiDisc && t.getDiscNumber() != lastDisc) {
                out.add(new Row(null, t.getDiscNumber()));
                lastDisc = t.getDiscNumber();
            }
            out.add(new Row(t, t.getDiscNumber()));
        }
        return out;
    }

    public void setOnPreviewClickListener(OnPreviewClickListener listener) {
        this.previewListener = listener;
    }

    public void setOnTrackMenuClickListener(OnTrackMenuClickListener listener) {
        this.menuListener = listener;
    }

    /** Atualiza o botao da faixa indicada; as restantes voltam a "play". */
    public void setPreviewState(String trackId, PreviewState state) {
        this.previewTrackId = trackId;
        this.previewState = state != null ? state : PreviewState.IDLE;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    /** A faixa, ou o numero do disco (Integer) se for um cabecalho. */
    @Override
    public Object getItem(int position) {
        Row row = rows.get(position);
        return row.track != null ? row.track : (Object) Integer.valueOf(row.disc);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).track != null ? TYPE_TRACK : TYPE_DISC;
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
        Row row = rows.get(position);
        if (row.track == null) {
            return discHeaderView(row.disc, convertView, parent);
        }

        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_track, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Track track = row.track;
        holder.number.setText(String.valueOf(track.getTrackNumber()));
        holder.title.setText(track.getTitle());
        holder.duration.setText(formatDuration(track.getDurationMs()));

        PreviewState state = Objects.equals(track.getSpotifyId(), previewTrackId)
                ? previewState : PreviewState.IDLE;
        bindPreviewButton(holder, state);
        holder.preview.setOnClickListener(v -> {
            if (previewListener != null) previewListener.onPreviewClick(track);
        });
        holder.menu.setOnClickListener(v -> {
            if (menuListener != null) menuListener.onTrackMenuClick(track, v);
        });
        return convertView;
    }

    private View discHeaderView(int disc, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_disc_header, parent, false);
        }
        ((TextView) convertView).setText(parent.getContext().getString(R.string.album_disc, disc));
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
        final ImageButton menu;

        ViewHolder(View row) {
            number = row.findViewById(R.id.txtTrackNumber);
            title = row.findViewById(R.id.txtTrackTitle);
            duration = row.findViewById(R.id.txtTrackDuration);
            preview = row.findViewById(R.id.btnPreview);
            previewLoading = row.findViewById(R.id.progressPreview);
            menu = row.findViewById(R.id.btnTrackMenu);
        }
    }
}
