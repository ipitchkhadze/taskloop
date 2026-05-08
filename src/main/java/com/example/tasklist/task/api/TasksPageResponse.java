package com.example.tasklist.task.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record TasksPageResponse(
        List<TaskResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static TasksPageResponse from(Page<TaskResponse> page) {
        return new TasksPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
