package dev.tatenda.musiclibrary.ui;

import dev.tatenda.musiclibrary.repository.jdbc.Database;
import dev.tatenda.musiclibrary.repository.jdbc.JdbcPlaylistRepository;
import dev.tatenda.musiclibrary.repository.jdbc.JdbcTrackRepository;
import dev.tatenda.musiclibrary.service.LibraryService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

/**
 * Entry point and composition root: the only class that knows which concrete classes are used.
 * Everything else receives its dependencies through constructors.
 */
public final class MusicLibraryApp extends Application {

    private Database database;

    @Override
    public void start(Stage stage) {
        database = Database.openFile(databasePath());
        LibraryService service = new LibraryService(
                new JdbcTrackRepository(database), new JdbcPlaylistRepository(database));
        service.seedIfEmpty();

        MainView view = new MainView(service, stage);
        Scene scene = new Scene(view.root(), 1200, 640);
        scene.getStylesheets().add(MusicLibraryApp.class.getResource("/dev/tatenda/musiclibrary/app.css").toExternalForm());
        stage.setTitle("Music Library");
        stage.setScene(scene);
        stage.show();
    }

    /** ~/.music-library/library.db by default; override with -Dmusiclibrary.db=path. */
    private static String databasePath() {
        String override = System.getProperty("musiclibrary.db");
        if (override != null) {
            return override;
        }
        File dir = Path.of(System.getProperty("user.home"), ".music-library").toFile();
        dir.mkdirs();
        return new File(dir, "library.db").getPath();
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
