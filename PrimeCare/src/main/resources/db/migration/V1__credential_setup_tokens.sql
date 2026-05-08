CREATE TABLE IF NOT EXISTS credential_setup_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    delivery_channel VARCHAR(16) NULL,
    delivery_target VARCHAR(255) NULL,
    requested_by_user_id BIGINT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    revoked BIT NOT NULL DEFAULT 0,
    revoked_at DATETIME NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_credential_setup_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_credential_setup_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_credential_setup_user ON credential_setup_tokens(user_id, purpose, expires_at);
CREATE INDEX idx_credential_setup_expires ON credential_setup_tokens(expires_at);
