CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email);

CREATE TABLE IF NOT EXISTS urls (
    id BIGINT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    original_url TEXT NOT NULL,
    owner_id BIGINT REFERENCES users(id),
    password_hash VARCHAR(100),
    expires_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_urls_code ON urls(code);
CREATE INDEX IF NOT EXISTS idx_urls_owner_created ON urls(owner_id, created_at DESC);

CREATE TABLE IF NOT EXISTS custom_domains (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(255) NOT NULL,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    verify_token VARCHAR(64) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_domains_domain ON custom_domains(domain);

CREATE TABLE IF NOT EXISTS click_events (
    id BIGSERIAL PRIMARY KEY,
    url_code VARCHAR(20) NOT NULL,
    clicked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    referrer TEXT,
    ip_hash VARCHAR(64),
    device_type VARCHAR(20),
    browser VARCHAR(50),
    os VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_click_events_code_time ON click_events(url_code, clicked_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_click_events_dedup ON click_events(url_code, clicked_at, ip_hash);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT,
    actor_email VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    target VARCHAR(100),
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
