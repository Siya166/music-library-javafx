package dev.tatenda.musiclibrary.repository.jdbc;

import dev.tatenda.musiclibrary.model.Playlist;
import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.repository.PlaylistRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** JDBC implementation of {@link PlaylistRepository}. */
public final class JdbcPlaylistRepository implements PlaylistRepository {

    private final Database db;

    public JdbcPlaylistRepository(Database db) {
        this.db = db;
    }

    @Override
    public List<Playlist> findAll() {
        return db.inTransaction(c -> {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name FROM playlists ORDER BY name")) {
                List<Playlist> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new Playlist(rs.getLong("id"), rs.getString("name")));
                }
                return result;
            }
        });
    }

    @Override
    public Playlist create(String name) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO playlists (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new Playlist(keys.getLong(1), name);
                }
            }
        });
    }

    @Override
    public void rename(long playlistId, String newName) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE playlists SET name = ? WHERE id = ?")) {
                ps.setString(1, newName);
                ps.setLong(2, playlistId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public void delete(long playlistId) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM playlists WHERE id = ?")) {
                ps.setLong(1, playlistId);
                return ps.executeUpdate(); // playlist_tracks rows go too (ON DELETE CASCADE)
            }
        });
    }

    @Override
    public void addTrack(long playlistId, long trackId) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position)
                    VALUES (?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_tracks WHERE playlist_id = ?))""")) {
                ps.setLong(1, playlistId);
                ps.setLong(2, trackId);
                ps.setLong(3, playlistId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public void removeTrack(long playlistId, long trackId) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?")) {
                ps.setLong(1, playlistId);
                ps.setLong(2, trackId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public List<Track> tracksIn(long playlistId) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(JdbcTrackRepository.SELECT_TRACK + """
                    JOIN playlist_tracks pt ON pt.track_id = t.id
                    WHERE pt.playlist_id = ?
                    ORDER BY pt.position""")) {
                ps.setLong(1, playlistId);
                return JdbcTrackRepository.readTracks(ps.executeQuery());
            }
        });
    }
}
