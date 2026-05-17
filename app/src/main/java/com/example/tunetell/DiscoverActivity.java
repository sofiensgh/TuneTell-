package com.example.tunetell;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tunetell.adapters.TrackAdapter;
import com.example.tunetell.models.MusicTrack;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class DiscoverActivity extends AppCompatActivity {

    private RecyclerView recyclerViewTrends;
    private TrackAdapter trendsAdapter;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);

        db = FirebaseFirestore.getInstance();

        setupViews();
        setupBottomNavigation();
        loadData();
    }

    private void setupViews() {
        recyclerViewTrends = findViewById(R.id.recyclerViewTrends);
        recyclerViewTrends.setLayoutManager(new GridLayoutManager(this, 2));
        trendsAdapter = new TrackAdapter(new ArrayList<>(), null);
        recyclerViewTrends.setAdapter(trendsAdapter);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.navigation_discover);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.navigation_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.navigation_discover) {
                return true;
            }
            return false;
        });
    }

    private void loadData() {
        db.collection("tracks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<MusicTrack> tracks = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        MusicTrack track = new MusicTrack();
                        track.setId(doc.getId());
                        track.setTitle(doc.getString("title") != null ? doc.getString("title") : "Unknown");
                        track.setArtist(doc.getString("artist") != null ? doc.getString("artist") : "Unknown");
                        track.setArtworkUrl(doc.getString("artworkUrl") != null ? doc.getString("artworkUrl") : "");
                        tracks.add(track);
                    }
                    trendsAdapter.updateTracks(tracks);
                });
    }
}
