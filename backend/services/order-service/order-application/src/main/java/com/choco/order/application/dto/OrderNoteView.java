package com.choco.order.application.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderNoteView(UUID id, String text, Instant createdAt) {
}
