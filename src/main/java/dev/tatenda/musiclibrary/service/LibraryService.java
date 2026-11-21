package dev.tatenda.musiclibrary.service;

import dev.tatenda.musiclibrary.model.GenreSummary;
import dev.tatenda.musiclibrary.model.Playlist;
import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.repository.PlaylistRepository;
import dev.tatenda.musiclibrary.repository.TrackRepository;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Business rules for the music library: validation, search and reports.
 *
 * <p>Depends only on the repository interfaces, which are passed in through the constructor
 * (constructor injection). The UI talks to this class and never touches SQL.</p>
 */
public final class LibraryService {

    private final TrackRepository tracks;
    private final PlaylistRepository playlists;

    public LibraryService(TrackRepository tracks, PlaylistRepository playlists) {
        this.tracks = tracks;
        this.playlists = playlists;
    }

    public List<Track> allTracks() {
        return tracks.findAll();
    }

    /** Validates, tidies (trims whitespace) and saves a track. */
    public Track saveTrack(Track input) {
        Track track = new Track(input.id(), trim(input.title()), trim(input.artist()), trim(input.album()),
                trim(input.genre()), input.year(), input.durationSeconds(), input.rating());
        List<String> problems = validate(track);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return tracks.save(track);
    }

    public void deleteTrack(long id) {
        tracks.delete(id);
    }

    /** Every rule a track must meet, as messages a user can act on. */
    public static List<String> validate(Track t) {
        List<String> problems = new ArrayList<>();
        if (t.title() == null || t.title().isBlank()) problems.add("Title is required");
        if (t.artist() == null || t.artist().isBlank()) problems.add("Artist is required");
        if (t.album() == null || t.album().isBlank()) problems.add("Album is required");
        if (t.genre() == null || t.genre().isBlank()) problems.add("Genre is required");
        int thisYear = Year.now().getValue();
        if (t.year() < 1600 || t.year() > thisYear) problems.add("Year must be between 1600 and " + thisYear);
        if (t.durationSeconds() <= 0) problems.add("Duration must be more than 0:00");
        if (t.rating() < 0 || t.rating() > 5) problems.add("Rating must be from 0 to 5");
        return problems;
    }

    /**
     * True if every word of {@code query} appears in the title, artist, album or genre,
     * ignoring case. "beethoven sym" matches "Symphony No. 5" by Beethoven.
     */
    public static boolean matches(Track t, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = String.join(" ", t.title(), t.artist(), t.album(), t.genre(), String.valueOf(t.year()))
                .toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    public List<Track> search(String query) {
        return tracks.findAll().stream().filter(t -> matches(t, query)).toList();
    }

    public List<GenreSummary> genreReport() {
        return tracks.summariseByGenre();
    }

    public int totalSeconds(List<Track> list) {
        return list.stream().mapToInt(Track::durationSeconds).sum();
    }

    // Playlists

    public List<Playlist> allPlaylists() {
        return playlists.findAll();
    }

    public Playlist createPlaylist(String name) {
        String clean = trim(name);
        if (clean == null || clean.isBlank()) {
            throw new ValidationException(List.of("Playlist name is required"));
        }
        boolean exists = playlists.findAll().stream().anyMatch(p -> p.name().equalsIgnoreCase(clean));
        if (exists) {
            throw new ValidationException(List.of("A playlist called \"" + clean + "\" already exists"));
        }
        return playlists.create(clean);
    }

    public void deletePlaylist(long id) {
        playlists.delete(id);
    }

    public void addToPlaylist(long playlistId, long trackId) {
        playlists.addTrack(playlistId, trackId);
    }

    public void removeFromPlaylist(long playlistId, long trackId) {
        playlists.removeTrack(playlistId, trackId);
    }

    public List<Track> tracksIn(long playlistId) {
        return playlists.tracksIn(playlistId);
    }

    /** Adds a few public-domain classical recordings on first run so the app is not empty. */
    public void seedIfEmpty() {
        if (tracks.count() > 0) {
            return;
        }
        List<Track> samples = List.of(
                new Track(0, "Symphony No. 5 in C minor: I. Allegro con brio", "Ludwig van Beethoven", "Symphony No. 5", "Classical", 1808, 452, 5),
                new Track(0, "Für Elise", "Ludwig van Beethoven", "Piano Works", "Classical", 1810, 175, 4),
                new Track(0, "Eine kleine Nachtmusik: I. Allegro", "Wolfgang Amadeus Mozart", "Serenades", "Classical", 1787, 350, 5),
                new Track(0, "Requiem: Lacrimosa", "Wolfgang Amadeus Mozart", "Requiem in D minor", "Choral", 1791, 196, 4),
                new Track(0, "Cello Suite No. 1: Prelude", "Johann Sebastian Bach", "Cello Suites", "Baroque", 1720, 152, 5),
                new Track(0, "Air on the G String", "Johann Sebastian Bach", "Orchestral Suite No. 3", "Baroque", 1730, 320, 4),
                new Track(0, "The Four Seasons: Spring I. Allegro", "Antonio Vivaldi", "The Four Seasons", "Baroque", 1725, 210, 4),
                new Track(0, "Clair de Lune", "Claude Debussy", "Suite bergamasque", "Impressionist", 1905, 300, 5),
                new Track(0, "Gymnopédie No. 1", "Erik Satie", "Gymnopédies", "Impressionist", 1888, 185, 3),
                new Track(0, "Nocturne Op. 9 No. 2", "Frédéric Chopin", "Nocturnes", "Romantic", 1832, 270, 5),
                new Track(0, "The Blue Danube", "Johann Strauss II", "Waltzes", "Romantic", 1866, 600, 3));
        samples.forEach(tracks::save);
        Playlist focus = playlists.create("Study focus");
        tracks.findAll().stream()
                .filter(t -> t.genre().equals("Baroque") || t.genre().equals("Impressionist"))
                .forEach(t -> playlists.addTrack(focus.id(), t.id()));
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
