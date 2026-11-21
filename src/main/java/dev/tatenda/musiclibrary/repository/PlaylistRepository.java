package dev.tatenda.musiclibrary.repository;

import dev.tatenda.musiclibrary.model.Playlist;
import dev.tatenda.musiclibrary.model.Track;

import java.util.List;

/** Storage for playlists and their (ordered) tracks. */
public interface PlaylistRepository {

    List<Playlist> findAll();

    Playlist create(String name);

    void rename(long playlistId, String newName);

    void delete(long playlistId);

    /** Appends a track to the end of a playlist. Adding a track that is already there does nothing. */
    void addTrack(long playlistId, long trackId);

    void removeTrack(long playlistId, long trackId);

    List<Track> tracksIn(long playlistId);
}
