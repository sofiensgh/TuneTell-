package com.example.tunetell.utils;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import com.example.tunetell.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class RecognitionResultDialog extends AppCompatDialogFragment {

    private String title;
    private String artist;
    private String album;
    private String artworkUrl;
    private String spotifyUrl;
    private String youtubeUrl;

    private OnTrackSavedListener savedListener;

    public interface OnTrackSavedListener {
        void onTrackSaved();
    }

    public RecognitionResultDialog(String title, String artist, String album,
                                   String artworkUrl, String spotifyUrl, String youtubeUrl) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        // Filter out audio files (only keep image URLs) - more permissive filtering
        this.artworkUrl = filterArtworkUrl(artworkUrl);
        this.spotifyUrl = spotifyUrl;
        this.youtubeUrl = youtubeUrl;
    }

    private String filterArtworkUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        // Filter out audio files
        if (url.contains(".m4a") || url.contains(".aac") || url.contains(".mp3") || url.contains(".wav")) {
            return "";
        }
        // Accept image URLs - more permissive to handle various image formats
        if (url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png") || url.contains(".webp") ||
            url.contains(".gif") || url.contains(".bmp") || url.contains("image") || url.contains("cover") ||
            url.contains("artwork") || url.contains("spotify") || url.contains("itunes")) {
            return url;
        }
        // If it's a valid URL that doesn't contain audio extensions, accept it
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "";
    }

    public void setOnTrackSavedListener(OnTrackSavedListener listener) {
        this.savedListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_recognition_result, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        initViews(view, dialog);

        return dialog;
    }

    private void initViews(View view, Dialog dialog) {
        ImageView ivAlbumCover = view.findViewById(R.id.ivAlbumCover);
        ProgressBar progressCover = view.findViewById(R.id.progressCover);
        TextView tvSongTitle = view.findViewById(R.id.tvSongTitle);
        TextView tvArtistName = view.findViewById(R.id.tvArtistName);
        TextView tvAlbumName = view.findViewById(R.id.tvAlbumName);
        Button btnSpotify = view.findViewById(R.id.btnSpotify);
        Button btnYouTube = view.findViewById(R.id.btnYouTube);
        Button btnSaveToLibrary = view.findViewById(R.id.btnSaveToLibrary);
        Button btnDismiss = view.findViewById(R.id.btnDismiss);
        View btnClose = view.findViewById(R.id.btnClose);

        tvSongTitle.setText(title);
        tvArtistName.setText(artist);
        tvAlbumName.setText(album != null && !album.isEmpty() ? album : "Single");

        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            progressCover.setVisibility(View.VISIBLE);
            new DownloadImageTask(ivAlbumCover, progressCover).execute(artworkUrl);
        } else {
            progressCover.setVisibility(View.GONE);
            ivAlbumCover.setImageResource(R.drawable.ic_music_placeholder);
        }

        btnSpotify.setOnClickListener(v -> openLink(spotifyUrl));
        btnYouTube.setOnClickListener(v -> openLink(youtubeUrl));

        btnSaveToLibrary.setOnClickListener(v -> {
            btnSaveToLibrary.setEnabled(false);
            btnSaveToLibrary.setText("Saving...");
            saveToFirestore(dialog);
        });

        btnDismiss.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());
    }

    private void openLink(String url) {
        if (url == null || url.isEmpty()) {
            url = "https://www.google.com/search?q=" + title.replace(" ", "+");
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void saveToFirestore(Dialog dialog) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseAuth mAuth = FirebaseAuth.getInstance();

            if (mAuth.getCurrentUser() == null) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Please login first", Toast.LENGTH_SHORT).show();
                }
                resetSaveButton(dialog);
                return;
            }

            String id = db.collection("tracks").document().getId();

            Map<String, Object> trackData = new HashMap<>();
            trackData.put("title", title != null ? title : "Unknown");
            trackData.put("artist", artist != null ? artist : "Unknown");
            trackData.put("timestamp", System.currentTimeMillis());
            // Only save if it's a valid image URL
            if (artworkUrl != null && !artworkUrl.isEmpty()) {
                trackData.put("artworkUrl", artworkUrl);
            }

            db.collection("tracks").document(id).set(trackData)
                    .addOnSuccessListener(aVoid -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "✅ Saved to library!", Toast.LENGTH_SHORT).show();
                        }
                        if (savedListener != null) {
                            savedListener.onTrackSaved();
                        }
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("SaveTrack", "Error: " + e.getMessage());
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        resetSaveButton(dialog);
                    });
        } catch (Exception e) {
            Log.e("SaveTrack", "Exception: " + e.getMessage(), e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Failed to save", Toast.LENGTH_SHORT).show();
            }
            resetSaveButton(dialog);
        }
    }

    private void resetSaveButton(Dialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            Button btnSaveToLibrary = dialog.findViewById(R.id.btnSaveToLibrary);
            if (btnSaveToLibrary != null) {
                btnSaveToLibrary.setEnabled(true);
                btnSaveToLibrary.setText("Save to Library");
            }
        }
    }

    private static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        private final ProgressBar progressBar;

        public DownloadImageTask(ImageView imageView, ProgressBar progressBar) {
            this.imageView = imageView;
            this.progressBar = progressBar;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String imageUrl = urls[0];
            Bitmap bitmap = null;
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            progressBar.setVisibility(View.GONE);
            if (result != null) {
                imageView.setImageBitmap(result);
            } else {
                imageView.setImageResource(R.drawable.ic_music_placeholder);
            }
        }
    }
}