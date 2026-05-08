package com.example.tasklist.board.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BoardResponse(
        UUID id,
        String title,
        OffsetDateTime createdAt,
        long taskCount,
        UUID sourceTaskId,
        UUID sourceBoardId) {
}
