package com.example.tunetell;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
            public void onSuccess(String title, String artist, String album, String artworkUrl) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    animateSuccess();
                    saveRecognizedSong(title, artist);
                    resetRecognitionButton();
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
            showLibraryDialog();
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
            tvListeningStatus.setText("LISTENING...");
            if (fadeInUp != null) tvListeningStatus.startAnimation(fadeInUp);
        }

        if (btnRecognize != null) {
            btnRecognize.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start();
            btnRecognize.setEnabled(false);
        }
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
        }, 12000);
    }

    private void saveRecognizedSong(String title, String artist) {
        String id = db.collection("tracks").document().getId();
        MusicTrack track = new MusicTrack(id, title, artist);
        db.collection("tracks").document(id).set(track)
                .addOnSuccessListener(aVoid -> loadTracks());
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
        new MaterialAlertDialogBuilder(this)
                .setTitle("🎵 " + track.getTitle())
                .setMessage("🎤 Artist: " + track.getArtist() + "\n\n📅 " + date)
                .setPositiveButton("OK", null)
                .setNeutralButton("Delete", (dialog, which) -> deleteTrack(track))
                .show();
    }

    private void deleteTrack(MusicTrack track) {
        db.collection("tracks").document(track.getId()).delete()
                .addOnSuccessListener(aVoid -> loadTracks());
    }

    private void showLibraryDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("📚 My Library")
                .setMessage(trackList.isEmpty() ? "Your library is empty." : "You have " + trackList.size() + " discovered songs.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void clearAllTracks() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Library")
                .setMessage("Delete all songs?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    for (MusicTrack t : trackList) db.collection("tracks").document(t.getId()).delete();
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
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecognition();
        }
    }
}
