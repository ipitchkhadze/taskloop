package com.example.tasklist.board.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameBoardRequest(
        @NotBlank
        @Size(max = 120)
        String title
) {
}
