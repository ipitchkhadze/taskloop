package com.example.tasklist.task.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        boolean done,
        OffsetDateTime createdAt,
        String advice,
        OffsetDateTime adviceAt,
        UUID parentTaskId
) {
}
