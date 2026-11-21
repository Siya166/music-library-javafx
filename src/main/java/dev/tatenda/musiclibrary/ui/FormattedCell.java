package dev.tatenda.musiclibrary.ui;

import javafx.scene.control.TableCell;

import java.util.function.Function;

/** A table cell that keeps the raw value (for sorting) but displays formatted text. */
final class FormattedCell<S, T> extends TableCell<S, T> {

    private final Function<T, String> formatter;

    FormattedCell(Function<T, String> formatter) {
        this.formatter = formatter;
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : formatter.apply(item));
    }
}
