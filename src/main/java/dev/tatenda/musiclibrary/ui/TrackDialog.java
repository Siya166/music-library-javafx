package dev.tatenda.musiclibrary.ui;

import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.service.Durations;
import dev.tatenda.musiclibrary.service.LibraryService;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal window for adding or editing a track.
 *
 * <p>APPLICATION_MODAL blocks the main window until the dialog closes, so the user cannot
 * change the selection underneath an edit in progress. Input is checked before the dialog
 * closes, and every problem is listed at once instead of one at a time.</p>
 */
final class TrackDialog extends Dialog<Track> {

    private final TextField title = new TextField();
    private final TextField artist = new TextField();
    private final TextField album = new TextField();
    private final ComboBox<String> genre = new ComboBox<>();
    private final TextField year = new TextField();
    private final TextField length = new TextField();
    private final Spinner<Integer> rating = new Spinner<>(0, 5, 3);
    private final Label errors = new Label();

    TrackDialog(Window owner, Track existing) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(existing == null ? "Add track" : "Edit track");
        setHeaderText(existing == null ? "Add a track to your library" : "Edit \"" + existing.title() + "\"");

        genre.setEditable(true);
        genre.getItems().addAll("Classical", "Baroque", "Romantic", "Impressionist", "Choral",
                "Jazz", "Pop", "Rock", "Hip hop", "Amapiano", "Gospel");
        length.setPromptText("3:45");
        year.setPromptText(String.valueOf(Year.now().getValue()));
        errors.getStyleClass().add("errors");

        if (existing != null) {
            title.setText(existing.title());
            artist.setText(existing.artist());
            album.setText(existing.album());
            genre.setValue(existing.genre());
            year.setText(String.valueOf(existing.year()));
            length.setText(Durations.format(existing.durationSeconds()));
            rating.getValueFactory().setValue(existing.rating());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        String[] labels = {"Title", "Artist", "Album", "Genre", "Year", "Length (m:ss)", "Rating (0-5)"};
        Node[] fields = {title, artist, album, genre, year, length, rating};
        for (int i = 0; i < labels.length; i++) {
            grid.addRow(i, new Label(labels[i]), fields[i]);
        }
        grid.add(errors, 0, labels.length, 2, 1);
        title.setPrefColumnCount(28);
        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        long id = existing == null ? 0 : existing.id();
        // Stop the dialog closing while the input is invalid.
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            List<String> problems = new ArrayList<>();
            Track t = readTrack(id, problems);
            if (t != null) {
                problems.addAll(LibraryService.validate(t));
            }
            if (!problems.isEmpty()) {
                errors.setText(String.join("\n", problems));
                event.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK ? readTrack(id, new ArrayList<>()) : null);
        setOnShown(e -> title.requestFocus());
    }

    private Track readTrack(long id, List<String> problems) {
        int y = 0;
        int seconds = 0;
        try {
            y = Integer.parseInt(year.getText().trim());
        } catch (NumberFormatException e) {
            problems.add("Year must be a number");
        }
        try {
            seconds = Durations.parse(length.getText());
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
        }
        if (!problems.isEmpty()) {
            return null;
        }
        String g = genre.getEditor().getText();
        return new Track(id, title.getText(), artist.getText(), album.getText(),
                g == null || g.isBlank() ? genre.getValue() : g, y, seconds, rating.getValue());
    }
}
