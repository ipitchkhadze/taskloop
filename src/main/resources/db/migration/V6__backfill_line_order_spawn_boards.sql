-- Legacy boards from "spawn from advice" had root tasks without line_order, so the UI showed
-- checklist steps in reverse (created_at DESC, id DESC). Recover step order: first step = line_order 0.
-- Only touches boards where every root task still has line_order NULL (full legacy batch).
WITH roots AS (
    SELECT
        t.id,
        t.board_id,
        ROW_NUMBER() OVER (
            PARTITION BY t.board_id
            ORDER BY t.created_at DESC, t.id DESC
        ) AS rn_wrong
    FROM tasks t
    INNER JOIN boards b ON b.id = t.board_id AND b.source_task_id IS NOT NULL
    WHERE t.parent_task_id IS NULL
      AND t.line_order IS NULL
),
board_ok AS (
    SELECT b.id AS board_id
    FROM boards b
    WHERE b.source_task_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM tasks t
          WHERE t.board_id = b.id
            AND t.parent_task_id IS NULL
            AND t.line_order IS NOT NULL
      )
),
roots_filtered AS (
    SELECT r.id, r.board_id, r.rn_wrong
    FROM roots r
    INNER JOIN board_ok ok ON ok.board_id = r.board_id
),
counts AS (
    SELECT board_id, COUNT(*)::int AS cnt
    FROM roots_filtered
    GROUP BY board_id
)
UPDATE tasks t
SET line_order = c.cnt - rf.rn_wrong
FROM roots_filtered rf
INNER JOIN counts c ON c.board_id = rf.board_id
WHERE t.id = rf.id;
