package com.example.tunetell;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tunetell.adapters.TrackAdapter;
import com.example.tunetell.models.MusicTrack;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TrackAdapter adapter;
    private List<MusicTrack> trackList;
    private TextView tvTotalSongs, tvThisWeek, btnClearAll;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = FirebaseFirestore.getInstance();

        initViews();
        setupRecyclerView();
        setupBottomNavigation();
        loadHistory();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewHistory);
        tvTotalSongs = findViewById(R.id.tvTotalSongs);
        tvThisWeek = findViewById(R.id.tvThisWeek);
        btnClearAll = findViewById(R.id.btnClearAll);
    }

    private void setupRecyclerView() {
        trackList = new ArrayList<>();
        adapter = new TrackAdapter(trackList, new TrackAdapter.OnTrackClickListener() {
            @Override
            public void onTrackClick(MusicTrack track) {
                showDeleteDialog(track);
            }

            @Override
            public void onTrackOptions(MusicTrack track, View anchorView) {
                showDeleteDialog(track);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.navigation_history);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.navigation_history) {
                return true;
            } else if (itemId == R.id.navigation_discover) {
                startActivity(new Intent(this, DiscoverActivity.class));
                finish();
                return true;
            }
            return false;
        });

        btnClearAll.setOnClickListener(v -> clearAllTracks());
    }

    private void loadHistory() {
        db.collection("tracks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    trackList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        MusicTrack track = new MusicTrack();
                        track.setId(doc.getId());
                        track.setTitle(doc.getString("title") != null ? doc.getString("title") : "Unknown");
                        track.setArtist(doc.getString("artist") != null ? doc.getString("artist") : "Unknown");
                        track.setArtworkUrl(doc.getString("artworkUrl") != null ? doc.getString("artworkUrl") : "");
                        Long timestamp = doc.getLong("timestamp");
                        track.setTimestamp(timestamp != null ? timestamp : 0);
                        trackList.add(track);
                    }

                    adapter.updateTracks(trackList);
                    tvTotalSongs.setText(String.valueOf(trackList.size()));

                    // Calculate this week's count
                    long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
                    long thisWeekCount = 0;
                    for (MusicTrack track : trackList) {
                        if (track.getTimestamp() > oneWeekAgo) {
                            thisWeekCount++;
                        }
                    }
                    tvThisWeek.setText(String.valueOf(thisWeekCount));
                });
    }

    private void showDeleteDialog(MusicTrack track) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Song")
                .setMessage("Remove \"" + track.getTitle() + "\" from history?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTrack(track))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTrack(MusicTrack track) {
        db.collection("tracks").document(track.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    loadHistory();
                });
    }

    private void clearAllTracks() {
        if (trackList.isEmpty()) {
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Delete all " + trackList.size() + " songs?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    for (MusicTrack track : trackList) {
                        db.collection("tracks").document(track.getId()).delete();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
