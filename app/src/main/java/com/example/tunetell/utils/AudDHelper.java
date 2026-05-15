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
        void onSuccess(String title, String artist, String album, String artworkUrl);
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

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;

            Log.d(TAG, "Recording started");

            if (listener != null) {
                listener.onStartListening();
            }

            mainHandler.postDelayed(() -> {
                if (isRecording) {
                    stopAndRecognize();
                }
            }, 8000);

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
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
            Log.e(TAG, "Error: " + e.getMessage());
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

                Log.d(TAG, "File size: " + audioFile.length() + " bytes");

                if (audioFile.length() < 5000) {
                    postFailure("Recording too short. Please try again.");
                    return;
                }

                FileInputStream fis = new FileInputStream(audioFile);
                byte[] audioBytes = new byte[(int) audioFile.length()];
                fis.read(audioBytes);
                fis.close();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("api_token", API_TOKEN)
                        .addFormDataPart("file", "recording.m4a",
                                RequestBody.create(MediaType.parse("audio/m4a"), audioBytes))
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
                        String responseBody = response.body().string();
                        Log.d(TAG, "Raw Response: " + responseBody);
                        parseResponseManual(responseBody);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                postFailure("Error: " + e.getMessage());
            }
        }).start();
    }

    private void parseResponseManual(String json) {
        try {
            Log.d(TAG, "Parsing response");

            // Check if response contains "success"
            if (json.contains("\"status\":\"success\"")) {
                // Try to extract title and artist
                String title = extractJsonValue(json, "title");
                String artist = extractJsonValue(json, "artist");

                if (title != null && !title.isEmpty() && !title.equals("null")) {
                    Log.d(TAG, "Found: " + title + " - " + artist);
                    postSuccess(title, artist, "", "");
                } else {
                    postFailure("No song recognized. Try a different song.");
                }
            } else if (json.contains("\"error\"")) {
                String error = extractJsonValue(json, "error");
                postFailure("API Error: " + (error != null ? error : "Unknown"));
            } else {
                postFailure("No match found. Try again.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            postFailure("Parse error: " + e.getMessage());
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) {
                // Try without quotes for value
                search = "\"" + key + "\":";
                start = json.indexOf(search);
                if (start == -1) return null;
                start += search.length();
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                if (end == -1) return null;
                String value = json.substring(start, end).trim();
                if (value.startsWith("\"")) value = value.substring(1);
                if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
                return value;
            }

            start += search.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private void postSuccess(String title, String artist, String album, String artworkUrl) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onSuccess(title, artist, album, artworkUrl);
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