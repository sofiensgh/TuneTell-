package com.example.tunetell.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

    // YOUR API TOKEN
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

        Log.d(TAG, "Audio file path: " + audioFilePath);
    }

    public void startRecognition() {
        if (isRecording) return;

        try {
            File oldFile = new File(audioFilePath);
            if (oldFile.exists()) {
                oldFile.delete();
            }

            // Optimized recording settings for better recognition
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);      // CD Quality
            mediaRecorder.setAudioChannels(2);               // Stereo for better detection
            mediaRecorder.setAudioEncodingBitRate(192000);   // High bitrate
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;

            Log.d(TAG, "Recording started with optimized settings");

            if (listener != null) {
                listener.onStartListening();
            }

            // Increased to 15 seconds for better detection
            mainHandler.postDelayed(() -> {
                if (isRecording) {
                    Log.d(TAG, "Auto-stopping recording after 15 seconds");
                    stopAndRecognize();
                }
            }, 15000);

        } catch (Exception e) {
            Log.e(TAG, "Error starting: " + e.getMessage());
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
            Log.e(TAG, "Error stopping: " + e.getMessage());
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
                    postFailure("Audio file not found");
                    return;
                }

                long fileSize = audioFile.length();
                Log.d(TAG, "File size: " + fileSize + " bytes");

                if (fileSize < 15000) {
                    postFailure("Recording too short or quiet. Please try:\n• Play song louder\n• Record for 15 seconds\n• Move closer to speaker");
                    return;
                }

                FileInputStream fis = new FileInputStream(audioFile);
                byte[] audioBytes = new byte[(int) fileSize];
                fis.read(audioBytes);
                fis.close();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("api_token", API_TOKEN)
                        .addFormDataPart("file", "recording.m4a",
                                RequestBody.create(MediaType.parse("audio/m4a"), audioBytes))
                        .addFormDataPart("return", "spotify,apple_music")
                        .build();

                Request request = new Request.Builder()
                        .url(API_URL)
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Network error: " + e.getMessage());
                        postFailure("Network error: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String json = response.body().string();
                        Log.d(TAG, "Raw Response: " + json);
                        parseResponse(json);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                postFailure("Error: " + e.getMessage());
            }
        }).start();
    }

    private void parseResponse(String json) {
        try {
            if (json == null || json.isEmpty()) {
                postFailure("Empty response from server");
                return;
            }

            // Check for success status
            if (json.contains("\"status\":\"success\"")) {
                // Extract song information
                String title = extractJsonValue(json, "title");
                String artist = extractJsonValue(json, "artist");
                String album = extractJsonValue(json, "album");

                // Extract artwork URL
                String artworkUrl = "";
                if (json.contains("\"cover_art\"")) {
                    artworkUrl = extractJsonValue(json, "cover_art");
                }
                if ((artworkUrl == null || artworkUrl.isEmpty()) && json.contains("\"artwork\"")) {
                    artworkUrl = extractJsonValue(json, "url");
                }

                if (title != null && artist != null && !title.equals("null") && !title.isEmpty()) {
                    // Create Spotify and YouTube links
                    String spotifyUrl = "https://open.spotify.com/search/" + title.replace(" ", "%20");
                    String youtubeUrl = "https://www.youtube.com/results?search_query=" +
                            title.replace(" ", "+") + "+" + artist.replace(" ", "+");

                    Log.d(TAG, "Success: " + title + " - " + artist);
                    postSuccess(title, artist, album != null ? album : "",
                            artworkUrl != null ? artworkUrl : "", spotifyUrl, youtubeUrl);
                } else {
                    postFailure("Could not identify the song.\n\nTips:\n• Play a popular song\n• Increase volume\n• Record for 15 seconds");
                }
            } else if (json.contains("\"error\"")) {
                String error = extractJsonValue(json, "error");
                postFailure("API Error: " + (error != null ? error : "Unknown"));
            } else {
                postFailure("No song recognized.\n\nTry these popular songs:\n• Bohemian Rhapsody - Queen\n• Shape of You - Ed Sheeran\n• Blinding Lights - The Weeknd");
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            postFailure("Failed to parse response. Please try again.");
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) {
                search = "\"" + key + "\":";
                start = json.indexOf(search);
                if (start == -1) return "";
                start += search.length();
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                if (end == -1) return "";
                String value = json.substring(start, end).trim();
                if (value.startsWith("\"")) value = value.substring(1);
                if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
                if (value.equals("null")) return "";
                return value;
            }

            start += search.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            String value = json.substring(start, end);
            if (value.equals("null")) return "";
            return value;
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