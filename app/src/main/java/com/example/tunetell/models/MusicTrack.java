package com.example.tunetell.models;

public class MusicTrack {
    private String id;
    private String title;
    private String artist;
    private long timestamp;
    private String spotifyUrl;
    private String youtubeUrl;
    private String albumName;
    private String artworkUrl;

    // Empty constructor required for Firestore
    public MusicTrack() {}

    public MusicTrack(String id, String title, String artist) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.timestamp = System.currentTimeMillis();
        this.spotifyUrl = "";
        this.youtubeUrl = "";
        this.albumName = "";
        this.artworkUrl = "";
    }

    // Constructor with all fields
    public MusicTrack(String id, String title, String artist, String albumName, String artworkUrl, String spotifyUrl, String youtubeUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.timestamp = System.currentTimeMillis();
        this.albumName = albumName;
        this.artworkUrl = artworkUrl;
        this.spotifyUrl = spotifyUrl;
        this.youtubeUrl = youtubeUrl;
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getTimestamp() { return timestamp; }
    public String getSpotifyUrl() { return spotifyUrl; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getAlbumName() { return albumName; }
    public String getArtworkUrl() { return artworkUrl; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }
}