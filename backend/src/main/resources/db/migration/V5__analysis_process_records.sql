CREATE TABLE IF NOT EXISTS analysis_process_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    media_id BIGINT NOT NULL,
    goal_hash CHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    stage VARCHAR(64) NULL,
    message VARCHAR(1000) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_analysis_process_scope (media_id, goal_hash, mode, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
