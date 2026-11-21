package dev.tatenda.musiclibrary.service;

import dev.tatenda.musiclibrary.model.GenreSummary;
import dev.tatenda.musiclibrary.model.Playlist;
import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.repository.PlaylistRepository;
import dev.tatenda.musiclibrary.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the business rules with in-memory fakes instead of a database. This is possible only
 * because the service depends on interfaces (dependency inversion).
 */
class LibraryServiceTest {

    private FakeTracks tracks;
    private FakePlaylists playlists;
    private LibraryService service;

    @BeforeEach
    void setUp() {
        tracks = new FakeTracks();
        playlists = new FakePlaylists();
        service = new LibraryService(tracks, playlists);
    }

    private static Track valid() {
        return new Track(0, "Clair de Lune", "Claude Debussy", "Suite bergamasque", "Impressionist", 1905, 300, 5);
    }

    @Test
    void validTrackIsSavedTrimmed() {
        Track input = new Track(0, "  Clair de Lune ", " Claude Debussy", "Suite bergamasque ", "Impressionist", 1905, 300, 5);
        Track saved = service.saveTrack(input);
        assertEquals("Clair de Lune", saved.title());
        assertEquals("Claude Debussy", saved.artist());
    }

    @Test
    void allProblemsAreReportedTogether() {
        Track bad = new Track(0, " ", "", "Album", "Pop", 3000, 0, 7);
        ValidationException ex = assertThrows(ValidationException.class, () -> service.saveTrack(bad));
        assertEquals(5, ex.problems().size(), ex.problems().toString());
        assertTrue(tracks.store.isEmpty());
    }

    @Test
    void searchMatchesEveryWordAcrossFields() {
        Track t = valid();
        assertTrue(LibraryService.matches(t, "debussy clair"));
        assertTrue(LibraryService.matches(t, "IMPRESSIONIST"));
        assertTrue(LibraryService.matches(t, "1905"));
        assertTrue(LibraryService.matches(t, "  "));
        assertFalse(LibraryService.matches(t, "debussy mozart"));
    }

    @Test
    void searchFiltersSavedTracks() {
        service.saveTrack(valid());
        service.saveTrack(new Track(0, "Für Elise", "Ludwig van Beethoven", "Piano Works", "Classical", 1810, 175, 4));
        assertEquals(List.of("Für Elise"), service.search("beethoven").stream().map(Track::title).toList());
    }

    @Test
    void playlistNamesMustBeUniqueIgnoringCase() {
        service.createPlaylist("Study");
        assertThrows(ValidationException.class, () -> service.createPlaylist("study "));
        assertThrows(ValidationException.class, () -> service.createPlaylist("   "));
    }

    @Test
    void seedRunsOnlyOnce() {
        service.seedIfEmpty();
        int first = tracks.count();
        service.seedIfEmpty();
        assertEquals(first, tracks.count());
        assertTrue(first > 5);
        assertEquals(1, playlists.findAll().size());
    }

    @Test
    void totalSecondsAddsDurations() {
        Track shortTrack = new Track(2, "Für Elise", "Beethoven", "Piano Works", "Classical", 1810, 175, 4);
        assertEquals(475, service.totalSeconds(List.of(valid(), shortTrack)));
    }

    // In-memory fakes

    static final class FakeTracks implements TrackRepository {
        final Map<Long, Track> store = new LinkedHashMap<>();
        long nextId = 1;

        public List<Track> findAll() { return new ArrayList<>(store.values()); }
        public Optional<Track> findById(long id) { return Optional.ofNullable(store.get(id)); }
        public Track save(Track t) {
            Track saved = t.isNew() ? t.withId(nextId++) : t;
            store.put(saved.id(), saved);
            return saved;
        }
        public void delete(long id) { store.remove(id); }
        public List<GenreSummary> summariseByGenre() { return List.of(); }
        public int count() { return store.size(); }
    }

    static final class FakePlaylists implements PlaylistRepository {
        final Map<Long, Playlist> store = new LinkedHashMap<>();
        final Map<Long, List<Long>> members = new LinkedHashMap<>();
        long nextId = 1;

        public List<Playlist> findAll() { return new ArrayList<>(store.values()); }
        public Playlist create(String name) {
            Playlist p = new Playlist(nextId++, name);
            store.put(p.id(), p);
            return p;
        }
        public void rename(long id, String name) { store.put(id, new Playlist(id, name)); }
        public void delete(long id) { store.remove(id); members.remove(id); }
        public void addTrack(long p, long t) { members.computeIfAbsent(p, k -> new ArrayList<>()).add(t); }
        public void removeTrack(long p, long t) { members.getOrDefault(p, new ArrayList<>()).remove(t); }
        public List<Track> tracksIn(long p) { return List.of(); }
    }
}
