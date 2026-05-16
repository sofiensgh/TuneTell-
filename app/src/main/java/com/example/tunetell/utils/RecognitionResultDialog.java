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
import com.example.tunetell.models.MusicTrack;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
        this.artworkUrl = artworkUrl;
        this.spotifyUrl = spotifyUrl;
        this.youtubeUrl = youtubeUrl;
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

        // Set text data
        tvSongTitle.setText(title);
        tvArtistName.setText(artist);
        tvAlbumName.setText(album != null && !album.isEmpty() ? album : "Single");

        // Load album cover using AsyncTask
        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            progressCover.setVisibility(View.VISIBLE);
            new DownloadImageTask(ivAlbumCover, progressCover).execute(artworkUrl);
        } else {
            progressCover.setVisibility(View.GONE);
            ivAlbumCover.setImageResource(R.drawable.ic_music_placeholder);
        }

        // Spotify button
        btnSpotify.setOnClickListener(v -> {
            String url = (spotifyUrl != null && !spotifyUrl.isEmpty()) ?
                    spotifyUrl : "https://open.spotify.com/search/" + title.replace(" ", "%20");
            openLink(url);
        });

        // YouTube button
        btnYouTube.setOnClickListener(v -> {
            String url = (youtubeUrl != null && !youtubeUrl.isEmpty()) ?
                    youtubeUrl : "https://www.youtube.com/results?search_query=" + title.replace(" ", "+") + "+" + artist.replace(" ", "+");
            openLink(url);
        });

        // Save to Library button
        btnSaveToLibrary.setOnClickListener(v -> {
            saveToFirestore();
            dialog.dismiss();
        });

        // Dismiss button
        btnDismiss.setOnClickListener(v -> dialog.dismiss());

        // Close button
        btnClose.setOnClickListener(v -> dialog.dismiss());
    }

    private void openLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void saveToFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = db.collection("tracks").document().getId();
        MusicTrack track = new MusicTrack(id, title, artist, album, artworkUrl, spotifyUrl, youtubeUrl);

        db.collection("tracks").document(id).set(track)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "✅ Saved to library!", Toast.LENGTH_SHORT).show();
                    if (savedListener != null) {
                        savedListener.onTrackSaved();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to save", Toast.LENGTH_SHORT).show()
                );
    }

    private static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView imageView;
        private ProgressBar progressBar;

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