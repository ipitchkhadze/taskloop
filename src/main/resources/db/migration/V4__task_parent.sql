ALTER TABLE tasks ADD COLUMN parent_task_id UUID NULL;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_parent
    FOREIGN KEY (parent_task_id) REFERENCES tasks(id) ON DELETE CASCADE;

CREATE INDEX ix_tasks_parent_id ON tasks(parent_task_id);
