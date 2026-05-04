-- =====================================================
-- V7: PII 암호화 제거 — 데이터 분석 요구사항 대응
-- 비밀번호 해시(BCrypt)와 토큰 해시(SHA-256)만 유지.
-- 기존 암호화된 데이터는 복구 불가 → 시드 데이터로 재생성 필요.
-- =====================================================

-- 1. users: email_hash, phone_hash 컬럼 제거
DROP INDEX IF EXISTS idx_users_email_hash;
DROP INDEX IF EXISTS idx_users_phone_hash;
ALTER TABLE users DROP COLUMN IF EXISTS email_hash;
ALTER TABLE users DROP COLUMN IF EXISTS phone_hash;

-- 2. users: email → VARCHAR(255)로 복원, UNIQUE 제약 추가
--    기존 암호화된 데이터 초기화 (복호화 불가)
UPDATE users SET email = NULL WHERE email IS NOT NULL AND length(email) > 255;
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(255);
CREATE UNIQUE INDEX idx_users_email ON users(email) WHERE email IS NOT NULL;

-- 3. users: phone → VARCHAR(20)으로 복원
UPDATE users SET phone = NULL WHERE phone IS NOT NULL AND length(phone) > 20;
ALTER TABLE users ALTER COLUMN phone TYPE VARCHAR(20);
CREATE UNIQUE INDEX idx_users_phone ON users(phone) WHERE phone IS NOT NULL;

-- 4. invite_tokens: phone_hash → phone 컬��� 변경
DELETE FROM invite_tokens;
ALTER TABLE invite_tokens RENAME COLUMN phone_hash TO phone;
ALTER TABLE invite_tokens ALTER COLUMN phone TYPE VARCHAR(20);
DROP INDEX IF EXISTS idx_invite_tokens_phone_hash;
CREATE INDEX idx_invite_tokens_phone ON invite_tokens(phone);

-- 5. 컬럼 코멘트 갱신
COMMENT ON COLUMN users.email IS '로그인 이메일 — 평문 저장';
COMMENT ON COLUMN users.phone IS '연락처 — 평문 저장';
COMMENT ON COLUMN invite_tokens.phone IS '전화번호 — OTP 발송 대상 조회용';
