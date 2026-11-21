package dev.tatenda.musiclibrary.repository;

/**
 * Unchecked wrapper for SQL errors, so callers are not forced to handle
 * {@link java.sql.SQLException} and the interfaces stay free of JDBC types.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
