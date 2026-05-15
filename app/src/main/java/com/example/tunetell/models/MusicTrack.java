package com.example.tunetell.models;

public class MusicTrack {
    private String id;
    private String title;
    private String artist;
    private long timestamp;

    public MusicTrack() {}

    public MusicTrack(String id, String title, String artist) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getTimestamp() { return timestamp; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}