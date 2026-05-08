package com.example.tasklist.board.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBoardRequest(
        @NotBlank
        @Size(max = 120)
        String title
) {
}
