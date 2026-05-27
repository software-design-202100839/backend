-- =====================================================
-- V9: 다중 학교 멀티테넌시
-- 목적: School 엔티티 추가 + users/classes에 school_id FK
-- 설계: Shared-Database, Discriminator-Column 전략
--       scores/feedbacks 등은 student→user→school FK 체인으로 추론
-- =====================================================

-- 1. schools 테이블
CREATE TABLE schools (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    address    VARCHAR(500),
    phone      VARCHAR(20),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_schools_updated_at
    BEFORE UPDATE ON schools FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE schools IS '학교 (멀티테넌시 루트 엔티티)';

-- 2. 기본 학교 (기존 데이터 마이그레이션용)
INSERT INTO schools (name, code) VALUES ('기본학교', 'DEFAULT');

-- 3. users에 school_id 추가
ALTER TABLE users ADD COLUMN school_id BIGINT REFERENCES schools(id);
UPDATE users SET school_id = (SELECT id FROM schools WHERE code = 'DEFAULT');
ALTER TABLE users ALTER COLUMN school_id SET NOT NULL;
CREATE INDEX idx_users_school ON users(school_id);

-- 4. classes에 school_id 추가
ALTER TABLE classes ADD COLUMN school_id BIGINT REFERENCES schools(id);
UPDATE classes SET school_id = (SELECT id FROM schools WHERE code = 'DEFAULT');
ALTER TABLE classes ALTER COLUMN school_id SET NOT NULL;
CREATE INDEX idx_classes_school ON classes(school_id);
