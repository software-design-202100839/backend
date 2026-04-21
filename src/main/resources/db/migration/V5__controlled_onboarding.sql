-- =====================================================
-- V5: Controlled Onboarding — 사전 등록 기반 계정 활성화 (SSCM-66)
-- 자유 회원가입 폐기. Admin이 등록한 전화번호로만 OTP 인증 후 계정 활성화.
-- =====================================================

-- 1. users 테이블 변경
--    1-1. ADMIN role 허용 (CHECK 제약 확장)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT', 'PARENT'));

--    1-2. phone_hash 추가 (SHA-256, OTP 발송 대상 확인 + 사전 등록 여부 조회)
ALTER TABLE users ADD COLUMN phone_hash VARCHAR(64);
CREATE UNIQUE INDEX idx_users_phone_hash ON users(phone_hash);
COMMENT ON COLUMN users.phone_hash IS '전화번호 SHA-256 해시 — OTP 발송 대상 조회용';

--    1-3. 계정 활성화 여부 분리
--         is_active: 관리자가 계정을 비활성화(정지)할 수 있는 필드 (기존)
--         is_activated: 사용자가 OTP 인증 후 이메일+비밀번호를 직접 설정했는지 여부 (신규)
ALTER TABLE users ADD COLUMN is_activated BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN users.is_activated IS '계정 활성화 여부 — OTP 인증 + ID/PW 설정 완료 시 TRUE';
COMMENT ON COLUMN users.is_active IS '관리자 활성화 상태 — FALSE면 로그인 불가 (정지/탈퇴)';

--    1-4. 로그인 잠금 (5회 실패 → 30분 잠금)
ALTER TABLE users ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN login_locked_until TIMESTAMPTZ;
COMMENT ON COLUMN users.failed_login_count IS '연속 로그인 실패 횟수. 성공 시 0 초기화';
COMMENT ON COLUMN users.login_locked_until IS '잠금 해제 시각. NULL이면 잠금 없음';

--    1-5. email, password_hash를 NULL 허용으로 변경
--         (Admin 등록 시점엔 이메일/비밀번호 없음 — 활성화 시 본인이 직접 설정)
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- 2. invite_tokens 테이블 생성 (OTP 코드 저장)
CREATE TABLE invite_tokens (
    id            BIGSERIAL       PRIMARY KEY,
    phone_hash    VARCHAR(64)     NOT NULL,
    otp_code      VARCHAR(6)      NOT NULL,
    purpose       VARCHAR(20)     NOT NULL CHECK (purpose IN ('ACTIVATE', 'PW_RESET')),
    expires_at    TIMESTAMPTZ     NOT NULL,
    used_at       TIMESTAMPTZ,
    attempt_count INTEGER         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invite_tokens_phone_hash ON invite_tokens(phone_hash);
CREATE INDEX idx_invite_tokens_expires_at ON invite_tokens(expires_at);

COMMENT ON TABLE invite_tokens IS 'SMS OTP 토큰 — 계정 활성화 및 비밀번호 초기화용';
COMMENT ON COLUMN invite_tokens.phone_hash IS 'SHA-256(phone) — 사전 등록 번호 매칭';
COMMENT ON COLUMN invite_tokens.purpose IS 'ACTIVATE: 최초 활성화, PW_RESET: 비밀번호 찾기';
COMMENT ON COLUMN invite_tokens.attempt_count IS 'OTP 실패 횟수. 5회 초과 시 폐기';
COMMENT ON COLUMN invite_tokens.used_at IS '사용 완료 시각. NULL이면 미사용';
