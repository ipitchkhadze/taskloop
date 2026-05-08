package com.example.tasklist.task;

import com.example.tasklist.board.api.SpawnBoardFromAdviceRequest;
import com.example.tasklist.board.api.SpawnBoardResponse;
import com.example.tasklist.task.api.CreateTaskRequest;
import com.example.tasklist.task.api.TaskResponse;
import com.example.tasklist.task.api.TasksPageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public TasksPageResponse list(
            @RequestParam UUID boardId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return TasksPageResponse.from(tasks.list(pageable, boardId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return tasks.create(request);
    }

    @PatchMapping("/{id}/done")
    public TaskResponse markDone(@PathVariable("id") UUID id, @RequestBody DonePatch body) {
        return tasks.setDone(id, body.done());
    }

    @PostMapping("/{id}/advice")
    public TaskResponse advice(@PathVariable("id") UUID id) {
        return tasks.requestAdvice(id);
    }

    @PostMapping("/{id}/spawn-board-from-advice")
    @ResponseStatus(HttpStatus.CREATED)
    public SpawnBoardResponse spawnBoardFromAdvice(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) SpawnBoardFromAdviceRequest body) {
        return tasks.spawnBoardFromAdvice(id, body != null ? body : new SpawnBoardFromAdviceRequest(null));
    }

    public record DonePatch(boolean done) {
    }
}
