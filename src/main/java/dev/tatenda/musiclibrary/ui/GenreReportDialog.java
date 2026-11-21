package dev.tatenda.musiclibrary.ui;

import dev.tatenda.musiclibrary.model.GenreSummary;
import dev.tatenda.musiclibrary.service.Durations;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.List;

/** Modal report of tracks, total time and average rating per genre (a SQL GROUP BY). */
final class GenreReportDialog extends Dialog<Void> {

    GenreReportDialog(Window owner, List<GenreSummary> rows) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Genre report");
        setHeaderText("Your library by genre");

        TableView<GenreSummary> table = new TableView<>(FXCollections.observableArrayList(rows));
        TableColumn<GenreSummary, String> genre = new TableColumn<>("Genre");
        genre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().genre()));
        TableColumn<GenreSummary, Integer> count = new TableColumn<>("Tracks");
        count.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().trackCount()));
        TableColumn<GenreSummary, String> time = new TableColumn<>("Total time");
        time.setCellValueFactory(c -> new SimpleStringProperty(Durations.format(c.getValue().totalSeconds())));
        TableColumn<GenreSummary, String> avg = new TableColumn<>("Average rating");
        avg.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f", c.getValue().averageRating())));
        table.getColumns().addAll(List.of(genre, count, time, avg));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefSize(460, 260);

        getDialogPane().setContent(table);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
