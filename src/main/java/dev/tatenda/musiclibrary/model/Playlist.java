package dev.tatenda.musiclibrary.model;

public record Playlist(long id, String name) {

    @Override
    public String toString() {
        return name; // shown as-is in the JavaFX ListView
    }
}
