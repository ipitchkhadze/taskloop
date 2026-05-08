package com.example.tasklist.board;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "boards")
public class Board {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "source_task_id")
    private UUID sourceTaskId;

    protected Board() {
    }

    public Board(UUID id, String title) {
        this.id = id;
        this.title = title;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getSourceTaskId() {
        return sourceTaskId;
    }

    public void setSourceTaskId(UUID sourceTaskId) {
        this.sourceTaskId = sourceTaskId;
    }
}
