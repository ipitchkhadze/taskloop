package com.example.tasklist.task;

import com.example.tasklist.board.Board;
import com.example.tasklist.board.BoardRepository;
import com.example.tasklist.board.api.BoardResponse;
import com.example.tasklist.board.api.SpawnBoardFromAdviceRequest;
import com.example.tasklist.board.api.SpawnBoardResponse;
import com.example.tasklist.lmstudio.AdvicePromptContext;
import com.example.tasklist.lmstudio.LmStudioClient;
import com.example.tasklist.task.api.CreateTaskRequest;
import com.example.tasklist.task.api.TaskResponse;
import com.example.tasklist.task.api.TaskTreeNodeResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TaskService {

    /**
     * Checklist rows from advice use {@code line_order} 0..n (first step first). Other tasks use {@code createdAt}/{@code id} (newest first).
     */
    private static final Comparator<Task> TASK_LIST_ORDER = (a, b) -> {
        Integer la = a.getLineOrder();
        Integer lb = b.getLineOrder();
        boolean ha = la != null;
        boolean hb = lb != null;
        if (ha && hb) {
            int c = Integer.compare(la, lb);
            if (c != 0) {
                return c;
            }
            return a.getId().compareTo(b.getId());
        }
        if (ha != hb) {
            return ha ? -1 : 1;
        }
        int c = b.getCreatedAt().compareTo(a.getCreatedAt());
        if (c != 0) {
            return c;
        }
        return b.getId().compareTo(a.getId());
    };

    private static final int BOARD_TITLE_MAX = 120;
    private static final int MAX_TASK_DEPTH = 16;

    private final TaskRepository tasks;
    private final BoardRepository boardRepository;
    private final LmStudioClient lmStudioClient;
    private final AdviceLineParser adviceLineParser;
    private final Counter adviceCompleted;

    public TaskService(
            TaskRepository tasks,
            BoardRepository boardRepository,
            LmStudioClient lmStudioClient,
            AdviceLineParser adviceLineParser,
            MeterRegistry meterRegistry) {
        this.tasks = tasks;
        this.boardRepository = boardRepository;
        this.lmStudioClient = lmStudioClient;
        this.adviceLineParser = adviceLineParser;
        this.adviceCompleted = Counter.builder("taskloop.advice.completed")
                .description("Task advice successfully persisted after LM Studio call")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(Pageable pageable, UUID boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new NoSuchElementException("board not found");
        }
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.asc("lineOrder").with(Sort.NullHandling.NULLS_LAST),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));
        return tasks.findByBoard_Id(boardId, sorted).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TaskTreeNodeResponse> getTaskTree(UUID boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new NoSuchElementException("board not found");
        }
        List<Task> all = tasks.findAllByBoardWithParents(boardId);
        all.sort(TASK_LIST_ORDER);
        Map<UUID, List<Task>> byParent = new HashMap<>();
        for (Task t : all) {
            UUID pid = t.getParent() != null ? t.getParent().getId() : null;
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(t);
        }
        for (List<Task> lst : byParent.values()) {
            lst.sort(TASK_LIST_ORDER);
        }
        List<Task> roots = byParent.getOrDefault(null, Collections.emptyList());
        return roots.stream().map(t -> toTreeNode(t, byParent)).toList();
    }

    private TaskTreeNodeResponse toTreeNode(Task task, Map<UUID, List<Task>> byParent) {
        List<Task> kids = byParent.getOrDefault(task.getId(), Collections.emptyList());
        List<TaskTreeNodeResponse> childNodes = kids.stream()
                .map(ch -> toTreeNode(ch, byParent))
                .toList();
        return new TaskTreeNodeResponse(
                task.getId(),
                task.getTitle(),
                task.isDone(),
                task.getCreatedAt(),
                task.getAdvice(),
                task.getAdviceAt(),
                task.getParent() != null ? task.getParent().getId() : null,
                childNodes);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Board board = boardRepository.findById(request.boardId())
                .orElseThrow(() -> new NoSuchElementException("board not found"));
        Task parent = null;
        if (request.parentTaskId() != null) {
            parent = tasks.findById(request.parentTaskId())
                    .orElseThrow(() -> new NoSuchElementException("parent task not found"));
            if (!parent.getBoard().getId().equals(board.getId())) {
                throw new IllegalArgumentException("Родительская задача принадлежит другой доске.");
            }
            if (depthFromRoot(parent) >= MAX_TASK_DEPTH) {
                throw new IllegalArgumentException(
                        "Достигнута максимальная глубина вложенности задач (" + MAX_TASK_DEPTH + ").");
            }
        }
        Task task = new Task(UUID.randomUUID(), request.title().trim(), board, parent);
        tasks.save(task);
        return toResponse(task);
    }

    /**
     * Depth of a task: root = 1, each level under parent adds 1.
     */
    private static int depthFromRoot(Task t) {
        int d = 1;
        Task p = t.getParent();
        while (p != null) {
            d++;
            p = p.getParent();
        }
        return d;
    }

    @Transactional
    public TaskResponse setDone(UUID id, boolean done) {
        Task task = tasks.findById(id).orElseThrow(() -> new NoSuchElementException("task not found"));
        task.setDone(done);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse requestAdvice(UUID id) {
        Task task = tasks.findById(id).orElseThrow(() -> new NoSuchElementException("task not found"));
        AdvicePromptContext ctx = adviceContextFor(task);
        String advice = lmStudioClient.suggestHowToComplete(ctx);
        task.setAdvice(advice);
        task.setAdviceAt(OffsetDateTime.now());
        tasks.save(task);
        adviceCompleted.increment();
        return toResponse(task);
    }

    private static AdvicePromptContext adviceContextFor(Task task) {
        String boardTitle = task.getBoard().getTitle();
        List<String> ancestors = ancestorTitlesRootFirst(task);
        return new AdvicePromptContext(boardTitle, ancestors, task.getTitle());
    }

    private static List<String> ancestorTitlesRootFirst(Task task) {
        List<Task> chain = new ArrayList<>();
        Task p = task.getParent();
        while (p != null) {
            chain.add(p);
            p = p.getParent();
        }
        Collections.reverse(chain);
        return chain.stream().map(Task::getTitle).toList();
    }

    @Transactional
    public SpawnBoardResponse spawnBoardFromAdvice(UUID taskId, SpawnBoardFromAdviceRequest req) {
        Task source = tasks.findById(taskId).orElseThrow(() -> new NoSuchElementException("task not found"));
        String advice = source.getAdvice();
        if (advice == null || advice.isBlank()) {
            throw new IllegalArgumentException("У задачи нет совета — сначала запросите совет.");
        }
        List<String> titles = adviceLineParser.parseItemTitles(advice);
        String boardTitle = resolveBoardTitle(source, req);
        Board board = new Board(UUID.randomUUID(), boardTitle);
        board.setSourceTaskId(taskId);
        boardRepository.save(board);
        int line = 0;
        for (String title : titles) {
            Task spawned = new Task(UUID.randomUUID(), title, board);
            spawned.setLineOrder(line++);
            tasks.save(spawned);
        }
        long taskCount = tasks.countByBoard_Id(board.getId());
        BoardResponse br = new BoardResponse(
                board.getId(),
                board.getTitle(),
                board.getCreatedAt(),
                taskCount,
                taskId,
                source.getBoard().getId());
        return new SpawnBoardResponse(br, titles.size());
    }

    private static String resolveBoardTitle(Task source, SpawnBoardFromAdviceRequest req) {
        if (req != null && req.title() != null && !req.title().isBlank()) {
            return trimLen(req.title().trim(), BOARD_TITLE_MAX);
        }
        String t = source.getTitle();
        if (t != null && !t.isBlank()) {
            return trimLen(t, BOARD_TITLE_MAX);
        }
        String adv = source.getAdvice();
        if (adv != null && !adv.isBlank()) {
            String oneLine = adv.replace('\n', ' ').trim();
            return trimLen(oneLine, BOARD_TITLE_MAX);
        }
        return "Доска";
    }

    private static String trimLen(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private TaskResponse toResponse(Task task) {
        UUID parentId = task.getParent() == null ? null : task.getParent().getId();
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.isDone(),
                task.getCreatedAt(),
                task.getAdvice(),
                task.getAdviceAt(),
                parentId);
    }
}
