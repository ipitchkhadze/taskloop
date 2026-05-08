package com.example.tasklist.board.api;

import jakarta.validation.constraints.Size;

public record SpawnBoardFromAdviceRequest(
        @Size(max = 120)
        String title
) {
}
