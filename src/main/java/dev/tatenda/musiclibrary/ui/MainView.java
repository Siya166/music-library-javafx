package dev.tatenda.musiclibrary.ui;

import dev.tatenda.musiclibrary.model.Playlist;
import dev.tatenda.musiclibrary.model.Track;
import dev.tatenda.musiclibrary.service.Durations;
import dev.tatenda.musiclibrary.service.LibraryService;
import dev.tatenda.musiclibrary.service.ValidationException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.function.Function;

/**
 * The main window: playlists on the left, a searchable, sortable track table in the centre.
 *
 * <p>Search uses a {@link FilteredList} wrapped in a {@link SortedList}: typing updates the
 * filter predicate, and clicking a column header sorts the filtered rows, with no database
 * round trip.</p>
 */
final class MainView {

    private static final Playlist ALL_TRACKS = new Playlist(0, "All tracks");

    private final LibraryService service;
    private final Stage owner;
    private final BorderPane root = new BorderPane();
    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private final FilteredList<Track> filtered = new FilteredList<>(tracks, t -> true);
    private final TableView<Track> table = new TableView<>();
    private final ListView<Playlist> playlistList = new ListView<>();
    private final TextField search = new TextField();
    private final MenuButton addToPlaylist = new MenuButton("Add to playlist");
    private final Label status = new Label();

    MainView(LibraryService service, Stage owner) {
        this.service = service;
        this.owner = owner;
        root.setTop(buildToolbar());
        root.setLeft(buildPlaylistPane());
        root.setCenter(buildTable());
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(6, 12, 8, 12));
        reloadPlaylists();
        playlistList.getSelectionModel().select(0);
    }

    Parent root() {
        return root;
    }

    private HBox buildToolbar() {
        search.setPromptText("Search title, artist, album, genre or year");
        search.setPrefWidth(340);
        search.textProperty().addListener((obs, old, text) -> {
            filtered.setPredicate(t -> LibraryService.matches(t, text));
            updateStatus();
        });

        Button add = new Button("Add track");
        add.setOnAction(e -> editTrack(null));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> selectedTrack().ifPresent(this::editTrack));
        Button delete = new Button("Delete");
        delete.setOnAction(e -> selectedTrack().ifPresent(this::deleteTrack));
        Button report = new Button("Genre report");
        report.setOnAction(e -> new GenreReportDialog(owner, service.genreReport()).showAndWait());

        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(edit.disableProperty());
        addToPlaylist.disableProperty().bind(edit.disableProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, search, spacer, add, edit, delete, addToPlaylist, report);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private VBox buildPlaylistPane() {
        Label heading = new Label("Playlists");
        heading.getStyleClass().add("heading");
        playlistList.getSelectionModel().selectedItemProperty().addListener((obs, old, p) -> reloadTracks());
        VBox.setVgrow(playlistList, Priority.ALWAYS);

        Button create = new Button("New");
        create.setOnAction(e -> createPlaylist());
        Button remove = new Button("Delete");
        remove.setOnAction(e -> deletePlaylist());
        remove.disableProperty().bind(playlistList.getSelectionModel().selectedIndexProperty().lessThan(1));
        Button removeTrack = new Button("Remove track");
        removeTrack.setOnAction(e -> removeFromPlaylist());
        removeTrack.disableProperty().bind(remove.disableProperty()
                .or(table.getSelectionModel().selectedItemProperty().isNull()));

        VBox pane = new VBox(8, heading, playlistList, new HBox(6, create, remove), removeTrack);
        pane.setPadding(new Insets(0, 0, 0, 12));
        pane.setPrefWidth(220);
        return pane;
    }

    private TableView<Track> buildTable() {
        table.getColumns().add(column("Title", Track::title, 280));
        table.getColumns().add(column("Artist", Track::artist, 180));
        table.getColumns().add(column("Album", Track::album, 170));
        table.getColumns().add(column("Genre", Track::genre, 100));

        TableColumn<Track, Integer> year = new TableColumn<>("Year");
        year.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().year()));
        year.setPrefWidth(60);
        table.getColumns().add(year);

        // Sort length by the number of seconds, but display it as m:ss.
        TableColumn<Track, Integer> length = new TableColumn<>("Length");
        length.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().durationSeconds()));
        length.setCellFactory(col -> new FormattedCell<>(Durations::format));
        length.setPrefWidth(70);
        table.getColumns().add(length);

        TableColumn<Track, Integer> rating = new TableColumn<>("Rating");
        rating.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().rating()));
        rating.setCellFactory(col -> new FormattedCell<>(r -> "★".repeat(r) + "☆".repeat(5 - r)));
        rating.setPrefWidth(90);
        table.getColumns().add(rating);

        SortedList<Track> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No tracks match your search"));
        table.setRowFactory(tv -> {
            TableRow<Track> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    editTrack(row.getItem());
                }
            });
            return row;
        });
        return table;
    }

    private static TableColumn<Track, String> column(String name, Function<Track, String> getter, double width) {
        TableColumn<Track, String> col = new TableColumn<>(name);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    // Actions

    private void editTrack(Track existing) {
        new TrackDialog(owner, existing).showAndWait().ifPresent(track -> {
            try {
                Track saved = service.saveTrack(track);
                reloadTracks();
                table.getItems().stream().filter(t -> t.id() == saved.id()).findFirst()
                        .ifPresent(t -> table.getSelectionModel().select(t));
            } catch (ValidationException ex) {
                showError("Could not save track", ex.getMessage());
            }
        });
    }

    private void deleteTrack(Track track) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + track.title() + "\"? This also removes it from every playlist.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(owner);
        confirm.setHeaderText("Delete track");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            service.deleteTrack(track.id());
            reloadTracks();
        }
    }

    private void createPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle("New playlist");
        dialog.setHeaderText("Name your playlist");
        dialog.showAndWait().ifPresent(name -> {
            try {
                Playlist created = service.createPlaylist(name);
                reloadPlaylists();
                playlistList.getSelectionModel().select(created);
            } catch (ValidationException ex) {
                showError("Could not create playlist", ex.getMessage());
            }
        });
    }

    private void deletePlaylist() {
        Playlist p = playlistList.getSelectionModel().getSelectedItem();
        service.deletePlaylist(p.id());
        reloadPlaylists();
        playlistList.getSelectionModel().select(0);
    }

    private void removeFromPlaylist() {
        Playlist p = playlistList.getSelectionModel().getSelectedItem();
        selectedTrack().ifPresent(t -> {
            service.removeFromPlaylist(p.id(), t.id());
            reloadTracks();
        });
    }

    // Loading

    private void reloadPlaylists() {
        Playlist selected = playlistList.getSelectionModel().getSelectedItem();
        ObservableList<Playlist> items = FXCollections.observableArrayList(ALL_TRACKS);
        items.addAll(service.allPlaylists());
        playlistList.setItems(items);
        if (selected != null && items.contains(selected)) {
            playlistList.getSelectionModel().select(selected);
        }

        addToPlaylist.getItems().clear();
        for (Playlist p : service.allPlaylists()) {
            MenuItem item = new MenuItem(p.name());
            item.setOnAction(e -> selectedTrack().ifPresent(t -> service.addToPlaylist(p.id(), t.id())));
            addToPlaylist.getItems().add(item);
        }
    }

    private void reloadTracks() {
        Playlist p = playlistList.getSelectionModel().getSelectedItem();
        tracks.setAll(p == null || p.id() == 0 ? service.allTracks() : service.tracksIn(p.id()));
        updateStatus();
    }

    private void updateStatus() {
        status.setText(String.format("%d of %d tracks shown · %s total",
                filtered.size(), tracks.size(), Durations.format(service.totalSeconds(filtered))));
    }

    private Optional<Track> selectedTrack() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setHeaderText(header);
        alert.showAndWait();
    }
}
