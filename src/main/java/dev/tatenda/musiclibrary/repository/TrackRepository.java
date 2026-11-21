package dev.tatenda.musiclibrary.repository;

import dev.tatenda.musiclibrary.model.GenreSummary;
import dev.tatenda.musiclibrary.model.Track;

import java.util.List;
import java.util.Optional;

/**
 * Storage for tracks. The service and UI depend on this interface, not on JDBC, so the
 * storage can be swapped (for example for an in-memory fake in tests) without changing them.
 * This is the dependency inversion principle.
 */
public interface TrackRepository {

    List<Track> findAll();

    Optional<Track> findById(long id);

    /** Inserts a new track (id 0) or updates an existing one, returning it with its id. */
    Track save(Track track);

    void delete(long id);

    List<GenreSummary> summariseByGenre();

    int count();
}
