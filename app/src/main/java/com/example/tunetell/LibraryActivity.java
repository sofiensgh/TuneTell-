package com.example.tunetell;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tunetell.adapters.TrackAdapter;
import com.example.tunetell.models.MusicTrack;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TrackAdapter adapter;
    private List<MusicTrack> allTracks;
    private List<MusicTrack> filteredTracks;
    private EditText etSearch;
    private TextView tvSongCount;
    private LinearLayout emptyState;
    private Button btnClearAll, btnBack, btnGoRecognize;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initViews();
        setupRecyclerView();
        setupSearch();
        setupClickListeners();
        loadAllTracks();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewLibrary);
        etSearch = findViewById(R.id.etSearch);
        tvSongCount = findViewById(R.id.tvSongCount);
        emptyState = findViewById(R.id.emptyState);
        btnClearAll = findViewById(R.id.btnClearAll);
        btnBack = findViewById(R.id.btnBack);
        btnGoRecognize = findViewById(R.id.btnGoRecognize);
    }

    private void setupRecyclerView() {
        allTracks = new ArrayList<>();
        filteredTracks = new ArrayList<>();
        adapter = new TrackAdapter(filteredTracks, track -> {
            showTrackDetailsDialog(track);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTracks(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupClickListeners() {
        btnClearAll.setOnClickListener(v -> clearAllTracks());
        btnBack.setOnClickListener(v -> finish());
        btnGoRecognize.setOnClickListener(v -> finish());
    }

    private void loadAllTracks() {
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        db.collection("tracks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    allTracks.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        MusicTrack track = doc.toObject(MusicTrack.class);
                        track.setId(doc.getId());
                        allTracks.add(track);
                    }

                    filterTracks("");
                    updateEmptyState();
                });
    }

    private void filterTracks(String query) {
        filteredTracks.clear();

        if (query.isEmpty()) {
            filteredTracks.addAll(allTracks);
        } else {
            String lowerQuery = query.toLowerCase();
            for (MusicTrack track : allTracks) {
                if (track.getTitle().toLowerCase().contains(lowerQuery) ||
                        track.getArtist().toLowerCase().contains(lowerQuery)) {
                    filteredTracks.add(track);
                }
            }
        }

        adapter.notifyDataSetChanged();
        tvSongCount.setText(filteredTracks.size() + " songs");
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (allTracks.isEmpty()) {
            emptyState.setVisibility(android.view.View.VISIBLE);
            recyclerView.setVisibility(android.view.View.GONE);
        } else {
            emptyState.setVisibility(android.view.View.GONE);
            recyclerView.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void showTrackDetailsDialog(MusicTrack track) {
        String[] options = {"🎬 Listen on YouTube", "🎧 Listen on Spotify", "🔍 Search Google", "❌ Delete Song"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("🎵 " + track.getTitle())
                .setMessage("🎤 Artist: " + track.getArtist())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openYouTubeLink(track);
                            break;
                        case 1:
                            openSpotifyLink(track);
                            break;
                        case 2:
                            openGoogleSearch(track);
                            break;
                        case 3:
                            deleteTrack(track);
                            break;
                    }
                })
                .show();
    }

    private void openYouTubeLink(MusicTrack track) {
        String searchQuery = track.getTitle() + " " + track.getArtist() + " official audio";
        String url = "https://www.youtube.com/results?search_query=" + searchQuery.replace(" ", "+");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void openSpotifyLink(MusicTrack track) {
        String searchQuery = track.getTitle() + " " + track.getArtist();
        String url = "https://open.spotify.com/search/" + searchQuery.replace(" ", "%20");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void openGoogleSearch(MusicTrack track) {
        String searchQuery = track.getTitle() + " " + track.getArtist();
        String url = "https://www.google.com/search?q=" + searchQuery.replace(" ", "+");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void deleteTrack(MusicTrack track) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Song")
                .setMessage("Are you sure you want to delete \"" + track.getTitle() + "\"?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    db.collection("tracks").document(track.getId()).delete()
                            .addOnSuccessListener(aVoid -> {
                                loadAllTracks();
                                Toast.makeText(this, "Deleted: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                            );
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void clearAllTracks() {
        if (allTracks.isEmpty()) {
            Toast.makeText(this, "No songs to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Library")
                .setMessage("Delete all " + allTracks.size() + " songs?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    for (MusicTrack track : allTracks) {
                        db.collection("tracks").document(track.getId()).delete();
                    }
                    Toast.makeText(this, "Library cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }
}