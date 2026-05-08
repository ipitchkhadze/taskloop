package com.example.tasklist.board;

import com.example.tasklist.board.api.CreateBoardRequest;
import com.example.tasklist.board.api.BoardResponse;
import com.example.tasklist.board.api.RenameBoardRequest;
import com.example.tasklist.task.TaskService;
import com.example.tasklist.task.api.TaskTreeNodeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

    private final BoardService boards;
    private final TaskService tasks;

    public BoardController(BoardService boards, TaskService tasks) {
        this.boards = boards;
        this.tasks = tasks;
    }

    @GetMapping
    public List<BoardResponse> list() {
        return boards.listBoards();
    }

    @GetMapping("/{boardId}/tasks/tree")
    public List<TaskTreeNodeResponse> taskTree(@PathVariable("boardId") UUID boardId) {
        return tasks.getTaskTree(boardId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse create(@Valid @RequestBody CreateBoardRequest request) {
        return boards.createBoard(request);
    }

    @PatchMapping("/{id}")
    public BoardResponse rename(@PathVariable("id") UUID id, @Valid @RequestBody RenameBoardRequest request) {
        return boards.renameBoard(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        boards.deleteBoard(id);
    }
}
