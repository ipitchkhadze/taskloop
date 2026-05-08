package com.example.tasklist.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @EntityGraph(attributePaths = {"parent"})
    Page<Task> findByBoard_Id(UUID boardId, Pageable pageable);

    long countByBoard_Id(UUID boardId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN FETCH t.parent WHERE t.board.id = :boardId")
    List<Task> findAllByBoardWithParents(@Param("boardId") UUID boardId);
}
