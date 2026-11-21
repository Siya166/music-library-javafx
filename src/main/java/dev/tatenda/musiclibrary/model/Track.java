package dev.tatenda.musiclibrary.model;

/**
 * A track as the rest of the application sees it: artist and album are plain names.
 * The repository maps these to the normalised artists and albums tables.
 *
 * @param id an id of 0 means "not saved yet"
 */
public record Track(long id, String title, String artist, String album, String genre,
                    int year, int durationSeconds, int rating) {

    public Track withId(long newId) {
        return new Track(newId, title, artist, album, genre, year, durationSeconds, rating);
    }

    public boolean isNew() {
        return id == 0;
    }
}
