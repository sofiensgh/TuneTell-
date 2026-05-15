package com.example.tunetell.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tunetell.R;
import com.example.tunetell.models.MusicTrack;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.ViewHolder> {

    private List<MusicTrack> tracks;
    private OnTrackClickListener clickListener;

    public interface OnTrackClickListener {
        void onTrackClick(MusicTrack track);
    }

    // Constructor that accepts both track list and click listener
    public TrackAdapter(List<MusicTrack> tracks, OnTrackClickListener clickListener) {
        this.tracks = tracks;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicTrack track = tracks.get(position);
        holder.tvTitle.setText(track.getTitle());
        holder.tvArtist.setText(track.getArtist());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String date = sdf.format(new Date(track.getTimestamp()));
        holder.tvDate.setText(date);

        String[] icons = {"🎵", "🎤", "🎧", "🎸", "🎹", "🥁"};
        int randomIcon = (int) (Math.random() * icons.length);
        holder.tvSongIcon.setText(icons[randomIcon]);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTrackClick(track);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvArtist, tvDate, tvSongIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvSongIcon = itemView.findViewById(R.id.tvSongIcon);
        }
    }
}