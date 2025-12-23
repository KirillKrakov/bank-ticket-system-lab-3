CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_CLIENT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_app_user_username ON app_user(username);
CREATE INDEX idx_app_user_email ON app_user(email);
CREATE INDEX idx_app_user_created_at ON app_user(created_at DESC);