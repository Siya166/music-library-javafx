package dev.tatenda.musiclibrary.repository.jdbc;

import dev.tatenda.musiclibrary.model.GenreSummary;
import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.repository.TrackRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link TrackRepository}.
 *
 * <p>Every query uses a {@link PreparedStatement} with ? placeholders, never string
 * concatenation, which prevents SQL injection.</p>
 */
public final class JdbcTrackRepository implements TrackRepository {

    static final String SELECT_TRACK = """
            SELECT t.id, t.title, ar.name AS artist, al.title AS album, t.genre,
                   t.release_year, t.duration_seconds, t.rating
            FROM tracks t
            JOIN albums al  ON al.id = t.album_id
            JOIN artists ar ON ar.id = al.artist_id
            """;

    private final Database db;

    public JdbcTrackRepository(Database db) {
        this.db = db;
    }

    @Override
    public List<Track> findAll() {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT_TRACK + " ORDER BY ar.name, al.title, t.title")) {
                return readTracks(ps.executeQuery());
            }
        });
    }

    @Override
    public Optional<Track> findById(long id) {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT_TRACK + " WHERE t.id = ?")) {
                ps.setLong(1, id);
                return readTracks(ps.executeQuery()).stream().findFirst();
            }
        });
    }

    @Override
    public Track save(Track track) {
        return db.inTransaction(c -> {
            long albumId = findOrCreateAlbum(c, track.album(), findOrCreateArtist(c, track.artist()));
            if (track.isNew()) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO tracks (title, album_id, genre, release_year, duration_seconds, rating)
                        VALUES (?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                    bindTrack(ps, track, albumId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        return track.withId(keys.getLong(1));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE tracks SET title = ?, album_id = ?, genre = ?, release_year = ?,
                                      duration_seconds = ?, rating = ?
                    WHERE id = ?""")) {
                bindTrack(ps, track, albumId);
                ps.setLong(7, track.id());
                if (ps.executeUpdate() == 0) {
                    throw new IllegalArgumentException("No track with id " + track.id());
                }
            }
            deleteOrphans(c);
            return track;
        });
    }

    @Override
    public void delete(long id) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM tracks WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            deleteOrphans(c);
            return null;
        });
    }

    @Override
    public List<GenreSummary> summariseByGenre() {
        return db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT genre, COUNT(*) AS tracks, SUM(duration_seconds) AS seconds, AVG(rating) AS avg_rating
                    FROM tracks
                    GROUP BY genre
                    ORDER BY tracks DESC, genre""")) {
                ResultSet rs = ps.executeQuery();
                List<GenreSummary> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new GenreSummary(rs.getString("genre"), rs.getInt("tracks"),
                            rs.getInt("seconds"), rs.getDouble("avg_rating")));
                }
                return result;
            }
        });
    }

    @Override
    public int count() {
        return db.inTransaction(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tracks")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    static List<Track> readTracks(ResultSet rs) throws SQLException {
        List<Track> tracks = new ArrayList<>();
        while (rs.next()) {
            tracks.add(new Track(rs.getLong("id"), rs.getString("title"), rs.getString("artist"),
                    rs.getString("album"), rs.getString("genre"), rs.getInt("release_year"),
                    rs.getInt("duration_seconds"), rs.getInt("rating")));
        }
        rs.close();
        return tracks;
    }

    private static void bindTrack(PreparedStatement ps, Track t, long albumId) throws SQLException {
        ps.setString(1, t.title());
        ps.setLong(2, albumId);
        ps.setString(3, t.genre());
        ps.setInt(4, t.year());
        ps.setInt(5, t.durationSeconds());
        ps.setInt(6, t.rating());
    }

    private static long findOrCreateArtist(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM artists WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO artists (name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long findOrCreateAlbum(Connection c, String title, long artistId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM albums WHERE title = ? AND artist_id = ?")) {
            ps.setString(1, title);
            ps.setLong(2, artistId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO albums (title, artist_id) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setLong(2, artistId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Removes albums with no tracks and artists with no albums, so the lists stay tidy. */
    private static void deleteOrphans(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT album_id FROM tracks)");
            st.executeUpdate("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artist_id FROM albums)");
        }
    }
}
