package com.example.tasklist.task;

import com.example.tasklist.board.Board;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private boolean done = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "advice", columnDefinition = "TEXT")
    private String advice;

    @Column(name = "advice_at")
    private OffsetDateTime adviceAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parent;

    /**
     * Order from parsed advice / checklist (0-based). Null for normal tasks — then {@link #createdAt} defines order.
     */
    @Column(name = "line_order")
    private Integer lineOrder;

    protected Task() {
    }

    public Task(UUID id, String title, Board board) {
        this(id, title, board, null);
    }

    public Task(UUID id, String title, Board board, Task parent) {
        this.id = id;
        this.title = title;
        this.board = board;
        this.parent = parent;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDone() {
        return done;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Board getBoard() {
        return board;
    }

    public Task getParent() {
        return parent;
    }

    public void setParent(Task parent) {
        this.parent = parent;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public OffsetDateTime getAdviceAt() {
        return adviceAt;
    }

    public void setAdviceAt(OffsetDateTime adviceAt) {
        this.adviceAt = adviceAt;
    }

    public Integer getLineOrder() {
        return lineOrder;
    }

    public void setLineOrder(Integer lineOrder) {
        this.lineOrder = lineOrder;
    }
}
