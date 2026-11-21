package dev.tatenda.musiclibrary.repository;

import dev.tatenda.musiclibrary.model.GenreSummary;
import dev.tatenda.musiclibrary.model.Playlist;
import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.repository.jdbc.Database;
import dev.tatenda.musiclibrary.repository.jdbc.JdbcPlaylistRepository;
import dev.tatenda.musiclibrary.repository.jdbc.JdbcTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a real SQLite database held in memory, so the SQL itself is tested. */
class JdbcRepositoryTest {

    private Database db;
    private TrackRepository tracks;
    private PlaylistRepository playlists;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        tracks = new JdbcTrackRepository(db);
        playlists = new JdbcPlaylistRepository(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static Track track(String title, String artist, String album, String genre, int seconds, int rating) {
        return new Track(0, title, artist, album, genre, 2000, seconds, rating);
    }

    @Test
    void savedTrackGetsIdAndCanBeReadBack() {
        Track saved = tracks.save(track("Song", "Artist", "Album", "Pop", 200, 4));
        assertTrue(saved.id() > 0);
        assertEquals(saved, tracks.findById(saved.id()).orElseThrow());
    }

    @Test
    void artistsAndAlbumsAreSharedNotDuplicated() throws Exception {
        tracks.save(track("One", "Artist", "Album", "Pop", 100, 3));
        tracks.save(track("Two", "artist", "album", "Pop", 100, 3)); // names match case-insensitively
        assertEquals(1, countRows("artists"));
        assertEquals(1, countRows("albums"));
    }

    @Test
    void updateChangesFieldsAndMovesTrackToNewAlbum() throws Exception {
        Track saved = tracks.save(track("Song", "Artist", "Old album", "Pop", 200, 2));
        tracks.save(new Track(saved.id(), "Song (remaster)", "Artist", "New album", "Rock", 2001, 210, 5));
        Track reloaded = tracks.findById(saved.id()).orElseThrow();
        assertEquals("Song (remaster)", reloaded.title());
        assertEquals("New album", reloaded.album());
        assertEquals(5, reloaded.rating());
        assertEquals(1, countRows("albums"), "the empty old album should be removed");
    }

    @Test
    void updatingMissingTrackFails() {
        Track ghost = new Track(999, "Ghost", "A", "B", "Pop", 2000, 100, 1);
        assertThrows(IllegalArgumentException.class, () -> tracks.save(ghost));
    }

    @Test
    void deleteRemovesTrackAndOrphanedArtist() throws Exception {
        Track saved = tracks.save(track("Song", "Artist", "Album", "Pop", 200, 2));
        tracks.delete(saved.id());
        assertTrue(tracks.findById(saved.id()).isEmpty());
        assertEquals(0, countRows("artists"));
    }

    @Test
    void genreSummaryGroupsInSql() {
        tracks.save(track("A", "X", "Y", "Jazz", 100, 4));
        tracks.save(track("B", "X", "Y", "Jazz", 200, 2));
        tracks.save(track("C", "X", "Y", "Pop", 50, 5));
        List<GenreSummary> report = tracks.summariseByGenre();
        assertEquals(new GenreSummary("Jazz", 2, 300, 3.0), report.get(0));
        assertEquals(new GenreSummary("Pop", 1, 50, 5.0), report.get(1));
    }

    @Test
    void databaseConstraintsRejectBadData() {
        // The service validates first, but the schema is a second line of defence.
        assertThrows(DataAccessException.class, () -> tracks.save(track("Bad", "X", "Y", "Pop", 0, 3)));
        assertThrows(DataAccessException.class, () -> tracks.save(track("Bad", "X", "Y", "Pop", 10, 9)));
    }

    @Test
    void failedSaveIsRolledBack() throws Exception {
        assertThrows(DataAccessException.class, () -> tracks.save(track("Bad", "New artist", "Y", "Pop", 0, 3)));
        assertEquals(0, countRows("artists"), "the artist row from the failed save must not remain");
    }

    @Test
    void playlistKeepsOrderAndIgnoresDuplicates() {
        Track a = tracks.save(track("A", "X", "Y", "Pop", 100, 3));
        Track b = tracks.save(track("B", "X", "Y", "Pop", 100, 3));
        Playlist p = playlists.create("Mix");
        playlists.addTrack(p.id(), b.id());
        playlists.addTrack(p.id(), a.id());
        playlists.addTrack(p.id(), b.id());
        assertEquals(List.of("B", "A"), playlists.tracksIn(p.id()).stream().map(Track::title).toList());
    }

    @Test
    void deletingTrackRemovesItFromPlaylists() {
        Track a = tracks.save(track("A", "X", "Y", "Pop", 100, 3));
        Playlist p = playlists.create("Mix");
        playlists.addTrack(p.id(), a.id());
        tracks.delete(a.id());
        assertTrue(playlists.tracksIn(p.id()).isEmpty());
    }

    @Test
    void playlistsCanBeRenamedAndDeleted() {
        Playlist p = playlists.create("Old");
        playlists.rename(p.id(), "New");
        assertEquals(List.of(new Playlist(p.id(), "New")), playlists.findAll());
        playlists.delete(p.id());
        assertTrue(playlists.findAll().isEmpty());
    }

    @Test
    void searchTextIsNeverExecutedAsSql() {
        Track saved = tracks.save(track("Robert'); DROP TABLE tracks;--", "X", "Y", "Pop", 100, 3));
        assertEquals("Robert'); DROP TABLE tracks;--", tracks.findById(saved.id()).orElseThrow().title());
        assertEquals(1, tracks.count());
    }

    private int countRows(String table) {
        return db.inTransaction(c -> {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }
}
