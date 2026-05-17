package com.example.tunetell.models;

public class MusicTrack {
    private String id;
    private String title;
    private String artist;
    private long timestamp;
    private String artworkUrl;

    // Empty constructor required for Firestore
    public MusicTrack() {}

    public MusicTrack(String id, String title, String artist) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.timestamp = System.currentTimeMillis();
        this.artworkUrl = "";
    }

    public MusicTrack(String id, String title, String artist, String artworkUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.timestamp = System.currentTimeMillis();
        this.artworkUrl = filterArtworkUrl(artworkUrl);
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getTimestamp() { return timestamp; }
    public String getArtworkUrl() { return artworkUrl; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setArtworkUrl(String artworkUrl) {
        this.artworkUrl = filterArtworkUrl(artworkUrl);
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
}