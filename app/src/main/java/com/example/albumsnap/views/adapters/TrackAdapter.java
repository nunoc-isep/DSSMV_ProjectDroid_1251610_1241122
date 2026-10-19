package com.example.albumsnap.views.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.albumsnap.R;
import com.example.albumsnap.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Linhas de faixas: numero, titulo e duracao (mm:ss). */
public class TrackAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private List<Track> items = new ArrayList<>();

    public TrackAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Track> newItems) {
        items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<Track>();
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
        return false;   // as faixas nao sao clicaveis (ainda)
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

        Track track = getItem(position);
        holder.number.setText(String.valueOf(track.getTrackNumber()));
        holder.title.setText(track.getTitle());
        holder.duration.setText(formatDuration(track.getDurationMs()));
        return convertView;
    }

    static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static class ViewHolder {
        final TextView number;
        final TextView title;
        final TextView duration;

        ViewHolder(View row) {
            number = row.findViewById(R.id.txtTrackNumber);
            title = row.findViewById(R.id.txtTrackTitle);
            duration = row.findViewById(R.id.txtTrackDuration);
        }
    }
}
