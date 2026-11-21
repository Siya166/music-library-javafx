package dev.tatenda.musiclibrary.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationsTest {

    @Test
    void parsesMinutesAndSeconds() {
        assertEquals(225, Durations.parse("3:45"));
        assertEquals(5, Durations.parse(" 0:05 "));
        assertEquals(6000, Durations.parse("100:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "345", "3:7", "3:60", "a:bc", "0:00", "-1:00"})
    void rejectsBadInput(String text) {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(text));
    }

    @Test
    void formatsShortAndLongDurations() {
        assertEquals("3:45", Durations.format(225));
        assertEquals("0:05", Durations.format(5));
        assertEquals("1:02:05", Durations.format(3725));
    }

    @Test
    void parseAndFormatRoundTrip() {
        for (int s = 1; s < 3600; s += 37) {
            assertEquals(s, Durations.parse(Durations.format(s)));
        }
    }
}
