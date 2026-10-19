package com.example.albumsnap.views.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.albumsnap.R;
import com.example.albumsnap.model.Album;
import com.example.albumsnap.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Linhas de albuns (capa, titulo, artista e icone "i"), usadas em Recent e na Library. */
public class AlbumAdapter extends BaseAdapter {

    /** Avisa quando se toca no icone "i" de uma linha. */
    public interface Listener {
        void onInfoClicked(Album album);
    }

    private static final int COVER_SIZE_PX = 200;

    private final LayoutInflater inflater;
    private final Listener listener;
    private List<Album> items = new ArrayList<>();

    public AlbumAdapter(Context context, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setItems(List<Album> newItems) {
        items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<Album>();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Album getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_album, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Album album = getItem(position);
        holder.title.setText(album.getTitle());
        holder.artist.setText(album.getArtistNames());
        ImageLoader.load(album.getCoverUrl(), holder.cover, R.drawable.ic_album_placeholder, COVER_SIZE_PX);
        holder.info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onInfoClicked(album);
            }
        });
        return convertView;
    }

    private static class ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView artist;
        final ImageButton info;

        ViewHolder(View row) {
            cover = row.findViewById(R.id.imgCover);
            title = row.findViewById(R.id.txtTitle);
            artist = row.findViewById(R.id.txtArtist);
            info = row.findViewById(R.id.btnInfo);
        }
    }
}
