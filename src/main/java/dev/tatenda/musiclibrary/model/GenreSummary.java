package dev.tatenda.musiclibrary.model;

/** One row of the "tracks per genre" report, computed in SQL with GROUP BY. */
public record GenreSummary(String genre, int trackCount, int totalSeconds, double averageRating) {
}
