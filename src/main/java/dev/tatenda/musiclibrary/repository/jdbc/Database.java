package dev.tatenda.musiclibrary.repository.jdbc;

import dev.tatenda.musiclibrary.repository.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection used by the app.
 *
 * <p>A desktop app with one user needs no connection pool. One shared connection also lets
 * tests use an in-memory database, which only lives as long as its connection.</p>
 */
public final class Database implements AutoCloseable {

    /** A piece of work that runs inside a transaction. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Connection connection;

    private Database(String url) {
        try {
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON"); // SQLite leaves FK checks off by default
            }
            createSchema();
        } catch (SQLException e) {
            throw new DataAccessException("Could not open database " + url, e);
        }
    }

    /** Opens (or creates) a database file. */
    public static Database openFile(String path) {
        return new Database("jdbc:sqlite:" + path);
    }

    /** A fresh, empty database in memory: used by tests. */
    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    Connection connection() {
        return connection;
    }

    /** Runs {@code work} in a transaction: all of it is saved, or none of it is. */
    public synchronized <T> T inTransaction(Work<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

    private void createSchema() throws SQLException {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/dev/tatenda/musiclibrary/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql missing from resources");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read schema.sql", e);
        }
        try (Statement st = connection.createStatement()) {
            for (String statement : sql.replaceAll("--[^\n]*", "").split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Could not close database", e);
        }
    }
}
