-- =====================================================
-- V2: 개인정보 암호화 컬럼 변경 (SSCM-55)
-- AES-256-GCM 암호문은 Base64 인코딩되어 원본보다 길어짐
-- =====================================================

-- 1. users: email, phone → TEXT로 확장 (암호문은 Base64로 원본보다 길어짐)
ALTER TABLE users ALTER COLUMN email TYPE TEXT;
ALTER TABLE users ALTER COLUMN phone TYPE TEXT;

-- 2. email_hash 컬럼 추가 (SHA-256, 조회/UNIQUE용)
ALTER TABLE users ADD COLUMN email_hash VARCHAR(64);

-- 3. 기존 email의 UNIQUE 제약 제거 (암호문은 매번 다르므로)
ALTER TABLE users DROP CONSTRAINT users_email_key;

-- 4. email_hash에 UNIQUE 인덱스 생성
CREATE UNIQUE INDEX idx_users_email_hash ON users(email_hash);

COMMENT ON COLUMN users.email IS '로그인 이메일 — AES-256-GCM 암호화됨';
COMMENT ON COLUMN users.email_hash IS '이메일 SHA-256 해시 — 조회/UNIQUE 용도';
COMMENT ON COLUMN users.name IS '사용자 이름 — 평문 (검색 필요)';
COMMENT ON COLUMN users.phone IS '연락처 — AES-256-GCM 암호화됨';
