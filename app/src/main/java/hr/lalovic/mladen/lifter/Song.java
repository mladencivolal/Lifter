package hr.lalovic.mladen.lifter;

import android.net.Uri;

import java.io.Serializable;

public class Song implements Serializable {

    public final long albumId;
    public final long id;
    private String data;
    private String title;
    private String album;
    private String artist;
    private long duration;
    private Uri albumArt;

    public Uri getAlbumArt() {
        return albumArt;
    }

    public void setAlbumArt(Uri albumArt) {
        this.albumArt = albumArt;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Song(long _id, long _albumId, String data, String title, String album, String artist, long duration) {
        this.id = _id;
        this.albumId = _albumId;
        this.title = title;
        this.album = album;
        this.data = data;
        this.artist = artist;
        this.duration = duration;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public long getId() {
        return id;
    }
}

