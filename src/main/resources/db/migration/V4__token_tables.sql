-- =====================================================
-- V4: Redis 제거 — 토큰 저장소를 PostgreSQL로 이전 (SSCM-66)
-- refresh_tokens: RT 저장 (Refresh Token Rotation)
-- token_blacklist: AT 블랙리스트 (로그아웃)
-- =====================================================

-- 1. Refresh Token 저장소
CREATE TABLE refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)     NOT NULL UNIQUE,  -- SHA-256 (원문 저장 안 함)
    expires_at  TIMESTAMPTZ     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

COMMENT ON TABLE refresh_tokens IS 'Refresh Token 저장소 — Redis 대체. SHA-256 해시만 저장';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256(refreshToken) — 원문은 클라이언트에만 존재';

-- 2. Access Token 블랙리스트
CREATE TABLE token_blacklist (
    id          BIGSERIAL       PRIMARY KEY,
    token_hash  VARCHAR(64)     NOT NULL UNIQUE,  -- SHA-256
    expires_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_token_blacklist_expires_at ON token_blacklist(expires_at);

COMMENT ON TABLE token_blacklist IS 'AT 블랙리스트 — 로그아웃된 토큰. @Scheduled 배치로 만료 항목 삭제';
COMMENT ON COLUMN token_blacklist.token_hash IS 'SHA-256(accessToken)';
