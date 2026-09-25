-- V5__add_occurred_on_to_status_history.sql

ALTER TABLE status_history ADD COLUMN occurred_on DATE;

CREATE INDEX idx_status_history_occurred_on ON status_history(occurred_on);
