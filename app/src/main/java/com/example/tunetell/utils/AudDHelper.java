package com.example.tunetell.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudDHelper {

    private static final String TAG = "AudDHelper";

    private static final String API_TOKEN = "b824219c4c7071e06f40b218965e4531";
    private static final String API_URL = "https://api.audd.io/";

    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private RecognitionListener listener;
    private boolean isRecording = false;
    private Handler mainHandler;
    private OkHttpClient client;
    private Context context;

    public interface RecognitionListener {
        void onSuccess(String title, String artist, String album, String artworkUrl, String spotifyUrl, String youtubeUrl);
        void onFailure(String errorMessage);
        void onStartListening();
        void onStopListening();
    }

    public AudDHelper(Context context, RecognitionListener listener) {
        this.context = context;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = new OkHttpClient();
        this.audioFilePath = context.getExternalFilesDir(null) + "/recording.m4a";
    }

    public void startRecognition() {
        if (isRecording) return;

        try {
            // Clean up any existing media recorder
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing old recorder: " + e.getMessage());
                }
                mediaRecorder = null;
            }

            File oldFile = new File(audioFilePath);
            if (oldFile.exists()) {
                oldFile.delete();
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(48000);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioEncodingBitRate(192000);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.d(TAG, "Recording started successfully");

            if (listener != null) {
                listener.onStartListening();
            }

            mainHandler.postDelayed(() -> {
                if (isRecording) {
                    stopAndRecognize();
                }
            }, 10000);

        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage(), e);
            if (listener != null) {
                listener.onFailure("Failed to start: " + e.getMessage());
            }
        }
    }

    public void stopAndRecognize() {
        if (!isRecording) return;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            isRecording = false;
            Log.d(TAG, "Recording stopped");

            if (listener != null) {
                listener.onStopListening();
            }

            recognizeAudio();

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage(), e);
            if (listener != null) {
                listener.onFailure("Error: " + e.getMessage());
            }
        }
    }

    public void stopRecognition() {
        if (isRecording) {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    mediaRecorder = null;
                }
                isRecording = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void recognizeAudio() {
        new Thread(() -> {
            try {
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists()) {
                    Log.e(TAG, "Audio file not found at: " + audioFilePath);
                    postFailure("Audio file not found");
                    return;
                }

                long fileSize = audioFile.length();
                Log.d(TAG, "Audio file size: " + fileSize + " bytes");
                if (fileSize < 5000) {
                    Log.e(TAG, "Recording too short: " + fileSize + " bytes");
                    postFailure("Recording too short. Please try again.");
                    return;
                }

                FileInputStream fis = new FileInputStream(audioFile);
                byte[] audioBytes = new byte[(int) fileSize];
                fis.read(audioBytes);
                fis.close();

                Log.d(TAG, "Sending audio to API, size: " + audioBytes.length + " bytes");

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("api_token", API_TOKEN)
                        .addFormDataPart("file", "recording.m4a",
                                RequestBody.create(MediaType.parse("audio/m4a"), audioBytes))
                        .addFormDataPart("return", "spotify,apple_music,deezer")
                        .build();

                Request request = new Request.Builder()
                        .url(API_URL)
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Network error: " + e.getMessage(), e);
                        postFailure("Network error: " + e.getMessage() + ". Check your internet connection.");
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String json = response.body().string();
                        Log.d(TAG, "API Response code: " + response.code());
                        Log.d(TAG, "API Response: " + json);

                        if (!response.isSuccessful()) {
                            Log.e(TAG, "API request failed with code: " + response.code());
                            postFailure("API error: " + response.code());
                            return;
                        }

                        parseResponse(json);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in recognizeAudio: " + e.getMessage(), e);
                postFailure("Error: " + e.getMessage());
            }
        }).start();
    }

    private void parseResponse(String json) {
        try {
            if (json == null || json.isEmpty()) {
                postFailure("Empty response");
                return;
            }

            Log.d(TAG, "Parsing JSON response");

            // Try Gson parsing first
            try {
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                String status = jsonObject.has("status") ? jsonObject.get("status").getAsString() : "";

                if ("success".equals(status)) {
                    JsonObject result = jsonObject.has("result") ? jsonObject.getAsJsonObject("result") : null;

                    if (result != null) {
                        String title = result.has("title") ? result.get("title").getAsString() : "";
                        String artist = result.has("artist") ? result.get("artist").getAsString() : "";
                        String album = result.has("album") ? result.get("album").getAsString() : "";

                        String artworkUrl = "";
                        // Try multiple fields for artwork
                        if (result.has("cover_art")) {
                            artworkUrl = result.get("cover_art").getAsString();
                        } else if (result.has("spotify") && result.getAsJsonObject("spotify").has("album")) {
                            JsonObject spotify = result.getAsJsonObject("spotify");
                            JsonObject spotifyAlbum = spotify.getAsJsonObject("album");
                            if (spotifyAlbum.has("images") && spotifyAlbum.getAsJsonArray("images").size() > 0) {
                                artworkUrl = spotifyAlbum.getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString();
                            }
                        } else if (result.has("apple_music") && result.getAsJsonObject("apple_music").has("album")) {
                            JsonObject apple = result.getAsJsonObject("apple_music");
                            JsonObject appleAlbum = apple.getAsJsonObject("album");
                            if (appleAlbum.has("artwork")) {
                                JsonObject artwork = appleAlbum.getAsJsonObject("artwork");
                                if (artwork.has("url")) {
                                    artworkUrl = artwork.get("url").getAsString();
                                }
                            }
                        } else if (result.has("deezer") && result.getAsJsonObject("deezer").has("album")) {
                            JsonObject deezer = result.getAsJsonObject("deezer");
                            JsonObject deezerAlbum = deezer.getAsJsonObject("album");
                            if (deezerAlbum.has("cover")) {
                                artworkUrl = deezerAlbum.get("cover").getAsString();
                            } else if (deezerAlbum.has("cover_big")) {
                                artworkUrl = deezerAlbum.get("cover_big").getAsString();
                            }
                        }

                        // Filter out audio files and non-image URLs
                        if (artworkUrl != null && (artworkUrl.contains(".m4a") || artworkUrl.contains(".aac") ||
                                artworkUrl.contains(".mp3") || artworkUrl.contains(".wav"))) {
                            artworkUrl = "";
                        }

                        String spotifyUrl = "https://open.spotify.com/search/" + (title != null ? title.replace(" ", "%20") : "");
                        String youtubeUrl = "https://www.youtube.com/results?search_query=" +
                                (title != null ? title.replace(" ", "+") : "") + "+" + (artist != null ? artist.replace(" ", "+") : "");

                        if (title != null && !title.isEmpty()) {
                            Log.d(TAG, "Song recognized: " + title + " by " + artist);
                            postSuccess(title, artist != null ? artist : "Unknown",
                                    album != null ? album : "",
                                    artworkUrl != null ? artworkUrl : "",
                                    spotifyUrl, youtubeUrl);
                        } else {
                            postFailure("No song recognized");
                        }
                    } else {
                        postFailure("No result in response");
                    }
                } else {
                    String error = jsonObject.has("error") ? jsonObject.get("error").getAsString() : "No match found";
                    Log.e(TAG, "API returned error: " + error);
                    postFailure(error);
                }
            } catch (Exception gsonError) {
                Log.e(TAG, "Gson parsing failed, trying fallback: " + gsonError.getMessage());
                // Fallback to simple string parsing
                parseResponseFallback(json);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage(), e);
            postFailure("Parse error: " + e.getMessage());
        }
    }

    private void parseResponseFallback(String json) {
        try {
            String title = extractValue(json, "title");
            String artist = extractValue(json, "artist");
            String album = extractValue(json, "album");

            String artworkUrl = "";
            if (json.contains("\"cover_art\"")) {
                artworkUrl = extractValue(json, "cover_art");
            }
            if ((artworkUrl == null || artworkUrl.isEmpty()) && json.contains("\"url\"")) {
                artworkUrl = extractValue(json, "url");
            }
            // Filter out audio files
            if (artworkUrl != null && (artworkUrl.contains(".m4a") || artworkUrl.contains(".aac"))) {
                artworkUrl = "";
            }

            String spotifyUrl = "https://open.spotify.com/search/" + (title != null ? title.replace(" ", "%20") : "");
            String youtubeUrl = "https://www.youtube.com/results?search_query=" +
                    (title != null ? title.replace(" ", "+") : "") + "+" + (artist != null ? artist.replace(" ", "+") : "");

            if (title != null && !title.isEmpty()) {
                Log.d(TAG, "Song recognized (fallback): " + title + " by " + artist);
                postSuccess(title, artist != null ? artist : "Unknown",
                        album != null ? album : "",
                        artworkUrl != null ? artworkUrl : "",
                        spotifyUrl, youtubeUrl);
            } else {
                postFailure("No song recognized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback parsing also failed: " + e.getMessage());
            postFailure("Could not parse response");
        }
    }

    private String extractValue(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) return "";
            start += search.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    private void postSuccess(String title, String artist, String album, String artworkUrl, String spotifyUrl, String youtubeUrl) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onSuccess(title, artist, album, artworkUrl, spotifyUrl, youtubeUrl);
            }
        });
    }

    private void postFailure(String error) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFailure(error);
            }
        });
    }
}