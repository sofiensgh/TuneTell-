package com.example.tunetell;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
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
import com.example.tunetell.utils.RecognitionResultDialog;
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
    private ImageButton btnRecognize, btnLibrary, btnLogout;
    private TextView tvListeningStatus;
    private View ringContainer;
    private View ring1, ring2, ring3;
    private TextView btnClearAll;

    // Animations
    private Animation fadeInUp, fadeOutDown;
    private Animation buttonPulse;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Recognition
    private AudDHelper audDHelper;
    private boolean isRecognizing = false;
    private Handler autoStopHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        autoStopHandler = new Handler();

        initViews();
        initAnimations();
        setupRecyclerView();
        setupAudDHelper();
        setupClickListeners();
        loadTracks();
        startWelcomeAnimation();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        btnRecognize = findViewById(R.id.btnRecognize);
        btnLibrary = findViewById(R.id.btnLibrary);
        btnLogout = findViewById(R.id.btnLogout);
        tvListeningStatus = findViewById(R.id.tvListeningStatus);
        ringContainer = findViewById(R.id.ringContainer);
        ring1 = findViewById(R.id.ring1);
        ring2 = findViewById(R.id.ring2);
        ring3 = findViewById(R.id.ring3);
        btnClearAll = findViewById(R.id.btnClearAll);
    }

    private void initAnimations() {
        try {
            fadeInUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
            fadeOutDown = AnimationUtils.loadAnimation(this, R.anim.fade_out_down);
            buttonPulse = AnimationUtils.loadAnimation(this, R.anim.button_pulse);
        } catch (Exception e) {
            fadeInUp = null;
            fadeOutDown = null;
            buttonPulse = null;
        }
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
            public void onSuccess(String title, String artist, String album, String artworkUrl, String spotifyUrl, String youtubeUrl) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    resetRecognitionButton();

                    RecognitionResultDialog dialog = new RecognitionResultDialog(title, artist, album, artworkUrl, spotifyUrl, youtubeUrl);
                    dialog.setOnTrackSavedListener(() -> loadTracks());
                    dialog.show(getSupportFragmentManager(), "recognition_result");
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    animateFailure();
                    showErrorDialog(errorMessage);
                    resetRecognitionButton();
                });
            }

            @Override
            public void onStartListening() {
                runOnUiThread(() -> startListeningAnimation());
            }

            @Override
            public void onStopListening() {
                // Notified when recognition session ends
            }
        });
    }

    private void setupClickListeners() {
        btnRecognize.setOnClickListener(v -> {
            animateButtonPress(v);
            performHapticFeedback();
            checkPermissionAndRecognize();
        });

        btnLibrary.setOnClickListener(v -> {
            animateButtonPress(v);
            performHapticFeedback();
            Intent intent = new Intent(MainActivity.this, LibraryActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            animateButtonPress(v);
            performHapticFeedback();
            logout();
        });

        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> clearAllTracks());
        }
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

        if (ringContainer != null) {
            ringContainer.setVisibility(View.VISIBLE);
            ringContainer.setAlpha(0f);
            ringContainer.animate().alpha(1f).setDuration(300).start();
        }

        startRingAnimation(ring1, 0);
        startRingAnimation(ring2, 400);
        startRingAnimation(ring3, 800);

        if (buttonPulse != null && btnRecognize != null) {
            btnRecognize.startAnimation(buttonPulse);
        }

        if (tvListeningStatus != null) {
            tvListeningStatus.setText("LISTENING... (15 sec)");
            if (fadeInUp != null) tvListeningStatus.startAnimation(fadeInUp);
        }

        if (btnRecognize != null) {
            btnRecognize.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start();
            btnRecognize.setEnabled(false);
        }

        Toast.makeText(this, "🎧 Listening for 15 seconds...\nPlay a popular song loudly", Toast.LENGTH_LONG).show();
    }

    private void startRingAnimation(View ring, int delay) {
        if (ring == null) return;
        ring.setScaleX(1f);
        ring.setScaleY(1f);
        ring.setAlpha(0.6f);
        ring.animate()
                .scaleX(1.8f)
                .scaleY(1.8f)
                .alpha(0f)
                .setDuration(1500)
                .setStartDelay(delay)
                .withEndAction(() -> {
                    if (isRecognizing) startRingAnimation(ring, 0);
                })
                .start();
    }

    private void stopListeningAnimation() {
        if (ringContainer != null) {
            ringContainer.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> ringContainer.setVisibility(View.GONE));
        }
        if (btnRecognize != null) {
            btnRecognize.clearAnimation();
            btnRecognize.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
        }
        if (tvListeningStatus != null) {
            tvListeningStatus.setText("Tap to Identify");
            if (fadeOutDown != null) tvListeningStatus.startAnimation(fadeOutDown);
        }
    }

    private void animateSuccess() {
        if (btnRecognize != null) {
            btnRecognize.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200)
                    .withEndAction(() -> btnRecognize.animate().scaleX(1f).scaleY(1f).setDuration(200).start())
                    .start();
        }
    }

    private void animateFailure() {
        if (btnRecognize != null) {
            btnRecognize.animate().rotationBy(10f).setDuration(50)
                    .withEndAction(() -> btnRecognize.animate().rotationBy(-20f).setDuration(100)
                            .withEndAction(() -> btnRecognize.animate().rotationBy(10f).setDuration(50).start()).start())
                    .start();
        }
    }

    private void animateButtonPress(View button) {
        button.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction(() -> button.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    private void resetRecognitionButton() {
        if (btnRecognize != null) {
            btnRecognize.setEnabled(true);
        }
        isRecognizing = false;
        autoStopHandler.removeCallbacksAndMessages(null);
    }

    private void checkPermissionAndRecognize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startRecognition();
        }
    }

    private void startRecognition() {
        if (isRecognizing) return;
        audDHelper.startRecognition();
        autoStopHandler.postDelayed(() -> {
            if (isRecognizing) audDHelper.stopAndRecognize();
        }, 17000);
    }

    private void loadTracks() {
        db.collection("tracks").orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    trackList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        MusicTrack track = doc.toObject(MusicTrack.class);
                        track.setId(doc.getId());
                        trackList.add(track);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showTrackDetailsDialog(MusicTrack track) {
        String date = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(new Date(track.getTimestamp()));

        String[] options = {"🎬 Listen on YouTube", "🎧 Listen on Spotify", "🔍 Search Google", "❌ Delete Song"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("🎵 " + track.getTitle())
                .setMessage("🎤 Artist: " + track.getArtist() + "\n\n📅 " + date)
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
                                loadTracks();
                                Toast.makeText(this, "Deleted: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void clearAllTracks() {
        if (trackList.isEmpty()) {
            Toast.makeText(this, "No songs to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Library")
                .setMessage("Delete all " + trackList.size() + " songs?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    for (MusicTrack t : trackList) {
                        db.collection("tracks").document(t.getId()).delete();
                    }
                    Toast.makeText(this, "Library cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showErrorDialog(String error) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("❌ Recognition Failed")
                .setMessage(error)
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
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecognition();
        } else {
            Toast.makeText(this, "Microphone permission is required to recognize songs", Toast.LENGTH_LONG).show();
        }
    }
}