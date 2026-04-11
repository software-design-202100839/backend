-- =====================================================
-- V3: 낙관적 락 version 컬럼 추가 (SSCM-58)
-- 동시 수정 lost-update 방지. JPA @Version + Hibernate가
-- UPDATE 시 WHERE version = ? 조건으로 충돌 검출.
-- =====================================================

ALTER TABLE scores          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE feedbacks       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE counselings     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE student_records ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN scores.version          IS '낙관적 락 버전 (JPA @Version)';
COMMENT ON COLUMN feedbacks.version       IS '낙관적 락 버전 (JPA @Version)';
COMMENT ON COLUMN counselings.version     IS '낙관적 락 버전 (JPA @Version)';
COMMENT ON COLUMN student_records.version IS '낙관적 락 버전 (JPA @Version)';
