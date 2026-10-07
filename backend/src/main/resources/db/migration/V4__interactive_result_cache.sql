CREATE TABLE IF NOT EXISTS interactive_result_cache (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    media_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    cache_key CHAR(64) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_interactive_cache (media_id, kind, cache_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
