-- V4__import_batch_idempotency.sql

CREATE TABLE import_batches (
    id VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    state VARCHAR(50) NOT NULL,
    expiry TIMESTAMP NOT NULL,
    result_summary TEXT,
    CONSTRAINT fk_import_batches_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_import_batches_user ON import_batches(user_id);
CREATE UNIQUE INDEX idx_import_batches_user_hash ON import_batches(user_id, manifest_hash);
