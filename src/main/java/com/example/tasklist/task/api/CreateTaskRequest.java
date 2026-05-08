package com.example.tasklist.task.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTaskRequest(
        @NotNull
        UUID boardId,
        @NotBlank
        @Size(max = 200)
        String title,
        UUID parentTaskId
) {
}
