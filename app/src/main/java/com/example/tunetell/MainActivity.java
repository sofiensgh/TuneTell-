package com.example.tunetell;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tunetell.adapters.TrackAdapter;
import com.example.tunetell.models.MusicTrack;
import com.example.tunetell.utils.AudDHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // UI Components
    private RecyclerView recyclerView;
    private TrackAdapter adapter;
    private List<MusicTrack> trackList;
    private Button btnRecognize, btnLibrary, btnLogout;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Recognition
    private AudDHelper audDHelper;
    private boolean isRecognizing = false;
    private Handler autoStopHandler;
    private Animation pulseAnimation;
    private ValueAnimator glowAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        autoStopHandler = new Handler();

        // Initialize UI Components
        initViews();

        // Initialize Animations
        initAnimations();

        // Initialize RecyclerView
        setupRecyclerView();

        // Initialize AudD Helper
        setupAudDHelper();

        // Set Click Listeners
        setupClickListeners();

        // Load saved tracks
        loadTracks();

        // Start welcome animation
        startWelcomeAnimation();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        btnRecognize = findViewById(R.id.btnRecognize);
        btnLibrary = findViewById(R.id.btnLibrary);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void initAnimations() {
        try {
            pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation);
        } catch (Exception e) {
            pulseAnimation = null;
        }

        // Create glow animation for button
        glowAnimator = ValueAnimator.ofFloat(0f, 1f, 0f);
        glowAnimator.setDuration(1500);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        glowAnimator.addUpdateListener(animation -> {
            if (btnRecognize != null && isRecognizing) {
                float value = (float) animation.getAnimatedValue();
                float elevation = 8f + (value * 12f);
                btnRecognize.setElevation(elevation);
            }
        });
    }

    private void setupRecyclerView() {
        trackList = new ArrayList<>();
        adapter = new TrackAdapter(trackList, track -> {
            showTrackDetailsDialog(track);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupAudDHelper() {
        audDHelper = new AudDHelper(this, new AudDHelper.RecognitionListener() {
            @Override
            public void onSuccess(String title, String artist, String album, String artworkUrl) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    Toast.makeText(MainActivity.this,
                            "🎉 Recognized: " + title + "\n🎤 Artist: " + artist,
                            Toast.LENGTH_LONG).show();
                    saveRecognizedSong(title, artist);
                    resetRecognitionButton();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    showErrorDialog(errorMessage);
                    resetRecognitionButton();
                });
            }

            @Override
            public void onStartListening() {
                runOnUiThread(() -> {
                    startListeningAnimation();
                });
            }

            @Override
            public void onStopListening() {
                runOnUiThread(() -> {
                    // Recognition stopped
                });
            }
        });
    }

    private void setupClickListeners() {
        btnRecognize.setOnClickListener(v -> {
            performHapticFeedback();
            checkPermissionAndRecognize();
        });

        btnLibrary.setOnClickListener(v -> {
            performHapticFeedback();
            showLibraryDialog();
        });

        btnLogout.setOnClickListener(v -> {
            performHapticFeedback();
            logout();
        });
    }

    private void startWelcomeAnimation() {
        if (btnRecognize != null) {
            btnRecognize.setAlpha(0f);
            btnRecognize.setScaleX(0.5f);
            btnRecognize.setScaleY(0.5f);
            btnRecognize.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(800)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void startListeningAnimation() {
        isRecognizing = true;

        if (pulseAnimation != null) {
            btnRecognize.startAnimation(pulseAnimation);
        }

        if (glowAnimator != null) {
            glowAnimator.start();
        }

        btnRecognize.setText("🎤");
        btnRecognize.setEnabled(false);

        Toast.makeText(this,
                "🎧 Listening for 8 seconds...\nPlay a song near your phone",
                Toast.LENGTH_LONG).show();
    }

    private void stopListeningAnimation() {
        if (btnRecognize != null) {
            btnRecognize.clearAnimation();
        }
        if (glowAnimator != null) {
            glowAnimator.cancel();
        }
        if (btnRecognize != null) {
            btnRecognize.setElevation(8f);
        }
    }

    private void resetRecognitionButton() {
        if (btnRecognize != null) {
            btnRecognize.setText("🎤");
            btnRecognize.setEnabled(true);
        }
        isRecognizing = false;
        autoStopHandler.removeCallbacksAndMessages(null);

        if (btnRecognize != null) {
            btnRecognize.setScaleX(1f);
            btnRecognize.setScaleY(1f);
        }
    }

    private void checkPermissionAndRecognize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                showPermissionRationaleDialog();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO_PERMISSION);
            }
        } else {
            startRecognition();
        }
    }

    private void showPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🎤 Microphone Permission")
                .setMessage("TuneTell needs microphone access to listen and identify songs playing around you.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_RECORD_AUDIO_PERMISSION);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startRecognition() {
        if (isRecognizing) return;
        audDHelper.startRecognition();

        autoStopHandler.postDelayed(() -> {
            if (isRecognizing) {
                audDHelper.stopAndRecognize();
            }
        }, 12000);
    }

    private void saveRecognizedSong(String title, String artist) {
        String id = db.collection("tracks").document().getId();
        MusicTrack track = new MusicTrack(id, title, artist);

        db.collection("tracks").document(id).set(track)
                .addOnSuccessListener(aVoid -> {
                    loadTracks();
                    animateNewTrackEntry();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save history", Toast.LENGTH_SHORT).show()
                );
    }

    private void animateNewTrackEntry() {
        if (recyclerView != null && recyclerView.getChildCount() > 0) {
            android.view.View firstChild = recyclerView.getChildAt(0);
            if (firstChild != null) {
                firstChild.setScaleX(0.8f);
                firstChild.setScaleY(0.8f);
                firstChild.setAlpha(0f);
                firstChild.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(400)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        }
    }

    private void loadTracks() {
        db.collection("tracks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        trackList.clear();
                        for (QueryDocumentSnapshot document : value) {
                            MusicTrack track = document.toObject(MusicTrack.class);
                            track.setId(document.getId());
                            trackList.add(track);
                        }
                        adapter.notifyDataSetChanged();

                        if (trackList.isEmpty()) {
                            // Empty state
                        }
                    }
                });
    }

    private void showTrackDetailsDialog(MusicTrack track) {
        String date = new SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                .format(new Date(track.getTimestamp()));

        new MaterialAlertDialogBuilder(this)
                .setTitle("🎵 " + track.getTitle())
                .setMessage("🎤 Artist: " + track.getArtist() + "\n\n📅 " + date)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLibraryDialog() {
        if (trackList.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("📚 My Library")
                    .setMessage("Your library is empty. Start recognizing songs!")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            StringBuilder library = new StringBuilder();
            for (int i = 0; i < Math.min(trackList.size(), 10); i++) {
                MusicTrack track = trackList.get(i);
                library.append("🎵 ").append(track.getTitle())
                        .append(" - ").append(track.getArtist())
                        .append("\n");
            }
            if (trackList.size() > 10) {
                library.append("\n... and ").append(trackList.size() - 10).append(" more");
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("📚 My Library (" + trackList.size() + " songs)")
                    .setMessage(library.toString())
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Clear All", (dialog, which) -> clearAllTracks())
                    .show();
        }
    }

    private void clearAllTracks() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Library")
                .setMessage("Are you sure you want to delete all recognized songs?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    for (MusicTrack track : trackList) {
                        db.collection("tracks").document(track.getId()).delete();
                    }
                    Toast.makeText(this, "Library cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showErrorDialog(String error) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("❌ Recognition Failed")
                .setMessage(error + "\n\nTry:\n• Playing a more popular song\n• Increasing volume\n• Moving closer to the sound source")
                .setPositiveButton("Try Again", (dialog, which) -> checkPermissionAndRecognize())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btnRecognize != null) {
            btnRecognize.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    private void logout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecognition();
            } else {
                Toast.makeText(this, "Microphone permission required for song recognition",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audDHelper != null) {
            audDHelper.stopRecognition();
        }
        if (glowAnimator != null) {
            glowAnimator.cancel();
        }
        if (autoStopHandler != null) {
            autoStopHandler.removeCallbacksAndMessages(null);
        }
    }
}