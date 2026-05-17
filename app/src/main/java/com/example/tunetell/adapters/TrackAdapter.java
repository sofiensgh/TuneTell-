package com.example.tunetell.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.tunetell.R;
import com.example.tunetell.models.MusicTrack;
import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.ViewHolder> {

    private final List<MusicTrack> tracks;
    private final OnTrackClickListener clickListener;

    public interface OnTrackClickListener {
        void onTrackClick(MusicTrack track);
        default void onTrackOptions(MusicTrack track, View anchorView) {}
    }

    public TrackAdapter(List<MusicTrack> tracks, OnTrackClickListener clickListener) {
        this.tracks = tracks != null ? new ArrayList<>(tracks) : new ArrayList<>();
        this.clickListener = clickListener;
    }

    public void updateTracks(List<MusicTrack> newTracks) {
        final List<MusicTrack> oldTracks = new ArrayList<>(this.tracks);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldTracks.size();
            }

            @Override
            public int getNewListSize() {
                return newTracks.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = oldTracks.get(oldItemPosition).getId();
                String newId = newTracks.get(newItemPosition).getId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MusicTrack oldTrack = oldTracks.get(oldItemPosition);
                MusicTrack newTrack = newTracks.get(newItemPosition);
                return oldTrack.getTitle().equals(newTrack.getTitle()) &&
                       oldTrack.getArtist().equals(newTrack.getArtist()) &&
                       (oldTrack.getArtworkUrl() != null ? oldTrack.getArtworkUrl().equals(newTrack.getArtworkUrl()) : newTrack.getArtworkUrl() == null);
            }
        });

        this.tracks.clear();
        this.tracks.addAll(newTracks);
        diffResult.dispatchUpdatesTo(this);
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

        if (track.getArtworkUrl() != null && !track.getArtworkUrl().isEmpty()) {
            holder.ivArtwork.setVisibility(View.VISIBLE);
            holder.tvSongIcon.setVisibility(View.GONE);
            
            Glide.with(holder.itemView.getContext())
                    .load(track.getArtworkUrl())
                    .transition(DrawableTransitionOptions.withCrossFade(400))
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .centerCrop()
                    .into(holder.ivArtwork);
        } else {
            holder.ivArtwork.setVisibility(View.GONE);
            holder.tvSongIcon.setVisibility(View.VISIBLE);
            holder.tvSongIcon.setText("🎵");
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTrackClick(track);
            }
        });

        if (holder.ivMore != null) {
            holder.ivMore.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTrackOptions(track, v);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvTitle, tvArtist, tvSongIcon;
        public final ImageView ivArtwork, ivMore;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvSongIcon = itemView.findViewById(R.id.tvSongIcon);
            ivArtwork = itemView.findViewById(R.id.ivArtwork);
            ivMore = itemView.findViewById(R.id.ivMore);
        }
    }
}
