CREATE TABLE boards (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO boards (id, title)
VALUES ('00000000-0000-4000-8000-000000000001', 'По умолчанию');

ALTER TABLE tasks ADD COLUMN board_id UUID;

UPDATE tasks SET board_id = '00000000-0000-4000-8000-000000000001' WHERE board_id IS NULL;

ALTER TABLE tasks ALTER COLUMN board_id SET NOT NULL;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE;

ALTER TABLE boards ADD COLUMN source_task_id UUID NULL;

ALTER TABLE boards
    ADD CONSTRAINT fk_boards_source_task FOREIGN KEY (source_task_id) REFERENCES tasks (id) ON DELETE SET NULL;
