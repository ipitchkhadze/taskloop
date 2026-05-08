package com.example.tasklist.board;

import com.example.tasklist.board.api.BoardResponse;
import com.example.tasklist.board.api.CreateBoardRequest;
import com.example.tasklist.board.api.RenameBoardRequest;
import com.example.tasklist.task.TaskRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tasklist.web.ConflictException;

import java.util.List;
import java.util.UUID;

@Service
public class BoardService {

    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;

    public BoardService(BoardRepository boardRepository, TaskRepository taskRepository) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> listBoards() {
        return boardRepository.findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BoardResponse createBoard(CreateBoardRequest request) {
        Board board = new Board(UUID.randomUUID(), request.title().trim());
        boardRepository.save(board);
        return toResponse(board);
    }

    @Transactional
    public BoardResponse renameBoard(UUID id, RenameBoardRequest request) {
        Board board = boardRepository.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("board not found"));
        board.setTitle(request.title().trim());
        boardRepository.save(board);
        return toResponse(board);
    }

    @Transactional
    public void deleteBoard(UUID id) {
        if (BoardDefaults.DEFAULT_BOARD_ID.equals(id)) {
            throw new ConflictException("Нельзя удалить доску по умолчанию.");
        }
        if (!boardRepository.existsById(id)) {
            throw new java.util.NoSuchElementException("board not found");
        }
        boardRepository.deleteById(id);
    }

    private BoardResponse toResponse(Board board) {
        UUID sourceTaskId = board.getSourceTaskId();
        UUID sourceBoardId = null;
        if (sourceTaskId != null) {
            sourceBoardId = taskRepository
                    .findById(sourceTaskId)
                    .map(t -> t.getBoard().getId())
                    .orElse(null);
        }
        return new BoardResponse(
                board.getId(),
                board.getTitle(),
                board.getCreatedAt(),
                taskRepository.countByBoard_Id(board.getId()),
                sourceTaskId,
                sourceBoardId);
    }
}
