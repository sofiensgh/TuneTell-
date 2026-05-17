package com.example.tunetell;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.tunetell.utils.AudDHelper;
import com.example.tunetell.utils.RecognitionResultDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private ImageButton btnRecognize;
    private TextView tvListenStatus;
    private View ring1, ring2, ring3;
    private BottomNavigationView bottomNavigation;

    private AudDHelper audDHelper;
    private boolean isRecognizing = false;
    private Handler autoStopHandler;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        autoStopHandler = new Handler();

        initViews();
        setupAudDHelper();
        setupBottomNavigation();
        setupClickListeners();
        
        // Entry Animation
        btnRecognize.setAlpha(0f);
        btnRecognize.setScaleX(0.5f);
        btnRecognize.setScaleY(0.5f);
        btnRecognize.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1000).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void initViews() {
        btnRecognize = findViewById(R.id.btnRecognize);
        tvListenStatus = findViewById(R.id.tvListenStatus);
        ring1 = findViewById(R.id.ring1);
        ring2 = findViewById(R.id.ring2);
        ring3 = findViewById(R.id.ring3);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.navigation_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                return true;
            } else if (itemId == R.id.navigation_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.navigation_discover) {
                startActivity(new Intent(this, DiscoverActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupAudDHelper() {
        audDHelper = new AudDHelper(this, new AudDHelper.RecognitionListener() {
            @Override
            public void onSuccess(String title, String artist, String album, String artworkUrl, String spotifyUrl, String youtubeUrl) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    RecognitionResultDialog dialog = new RecognitionResultDialog(title, artist, album, artworkUrl, spotifyUrl, youtubeUrl);
                    dialog.show(getSupportFragmentManager(), "recognition_result");
                    resetRecognitionButton();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    stopListeningAnimation();
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    resetRecognitionButton();
                });
            }

            @Override
            public void onStartListening() {
                runOnUiThread(() -> startListeningAnimation());
            }

            @Override
            public void onStopListening() {}
        });
    }

    private void setupClickListeners() {
        btnRecognize.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            }
            checkPermissionAndRecognize();
        });
    }

    private void startListeningAnimation() {
        isRecognizing = true;
        tvListenStatus.setVisibility(View.VISIBLE);
        tvListenStatus.setText("LISTENING...");

        startRingAnimation(ring1, 0, 1500);
        startRingAnimation(ring2, 500, 1500);
        startRingAnimation(ring3, 1000, 1500);

        btnRecognize.animate().rotationBy(3600f).setDuration(30000).setInterpolator(new LinearInterpolator()).start();
        btnRecognize.setEnabled(false);
    }

    private void startRingAnimation(View ring, int delay, int duration) {
        if (ring == null) return;
        ring.setScaleX(1f); ring.setScaleY(1f); ring.setAlpha(0.6f);
        ring.animate()
                .scaleX(2.5f).scaleY(2.5f).alpha(0f)
                .setDuration(duration).setStartDelay(delay)
                .withEndAction(() -> {
                    if (isRecognizing) startRingAnimation(ring, 0, duration);
                }).start();
    }

    private void stopListeningAnimation() {
        isRecognizing = false;
        btnRecognize.animate().cancel();
        btnRecognize.setRotation(0f);
        tvListenStatus.setVisibility(View.INVISIBLE);
    }

    private void resetRecognitionButton() {
        btnRecognize.setEnabled(true);
        isRecognizing = false;
        autoStopHandler.removeCallbacksAndMessages(null);
    }

    private void checkPermissionAndRecognize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            audDHelper.startRecognition();
            autoStopHandler.postDelayed(() -> {
                if (isRecognizing) audDHelper.stopAndRecognize();
            }, 10000);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            audDHelper.startRecognition();
            autoStopHandler.postDelayed(() -> {
                if (isRecognizing) audDHelper.stopAndRecognize();
            }, 10000);
        }
    }
}
