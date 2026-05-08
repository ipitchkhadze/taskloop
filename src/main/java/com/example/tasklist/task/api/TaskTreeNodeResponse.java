package com.example.tasklist.task.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TaskTreeNodeResponse(
        UUID id,
        String title,
        boolean done,
        OffsetDateTime createdAt,
        String advice,
        OffsetDateTime adviceAt,
        UUID parentTaskId,
        List<TaskTreeNodeResponse> children
) {
}
