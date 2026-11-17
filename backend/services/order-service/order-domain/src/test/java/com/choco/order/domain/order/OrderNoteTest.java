package com.choco.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order note value object")
class OrderNoteTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    @DisplayName("trims the text")
    void trimsText() {
        OrderNote note = new OrderNote(UUID.randomUUID(), "  hello  ", "admin", NOW);
        assertThat(note.text()).isEqualTo("hello");
    }

    @Test
    @DisplayName("rejects a blank note")
    void rejectsBlank() {
        assertThatThrownBy(() -> new OrderNote(UUID.randomUUID(), "   ", "admin", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a note longer than the limit")
    void rejectsTooLong() {
        String tooLong = "x".repeat(1_001);
        assertThatThrownBy(() -> new OrderNote(UUID.randomUUID(), tooLong, "admin", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a missing id or timestamp")
    void rejectsMissingFields() {
        assertThatThrownBy(() -> new OrderNote(null, "x", "admin", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderNote(UUID.randomUUID(), "x", "admin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
