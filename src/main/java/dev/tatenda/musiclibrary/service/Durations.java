package dev.tatenda.musiclibrary.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts between seconds and the "m:ss" / "h:mm:ss" text people type and read. */
public final class Durations {

    private static final Pattern MIN_SEC = Pattern.compile("^(\\d{1,3}):([0-5]\\d)$");

    private Durations() {
    }

    /**
     * Parses "3:45" into 225 seconds.
     *
     * @throws IllegalArgumentException if the text is not in m:ss form or is zero
     */
    public static int parse(String text) {
        Matcher m = MIN_SEC.matcher(text == null ? "" : text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Duration must look like 3:45");
        }
        int seconds = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        if (seconds == 0) {
            throw new IllegalArgumentException("Duration must be more than 0:00");
        }
        return seconds;
    }

    /** 225 becomes "3:45"; 3725 becomes "1:02:05". */
    public static String format(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
