-- =====================================================
-- V8: 피드백 학기 필드 추가 + 알림 타입 확장
-- =====================================================

-- 1. feedbacks 테이블에 year, semester 추가 (분석용 학기 단위 집계 지원)
ALTER TABLE feedbacks ADD COLUMN year INTEGER;
ALTER TABLE feedbacks ADD COLUMN semester INTEGER CHECK (semester IN (1, 2));

-- 기존 데이터: createdAt 기준으로 year 추정, semester는 NULL 허용
UPDATE feedbacks SET year = EXTRACT(YEAR FROM created_at)::INTEGER WHERE year IS NULL;

CREATE INDEX idx_feedbacks_year_semester ON feedbacks(year, semester);

COMMENT ON COLUMN feedbacks.year IS '학년도';
COMMENT ON COLUMN feedbacks.semester IS '학기 (1 또는 2)';

-- 2. notifications 타입 제약 확장 (RECORD_UPDATE 추가)
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('SCORE_UPDATE', 'FEEDBACK_NEW', 'RECORD_UPDATE', 'COUNSEL_UPDATE', 'SYSTEM'));

-- 3. notifications reference_type 제약 확장 (RECORD 추가)
--    기존에 CHECK 제약이 없으면 스킵 (VARCHAR 필드이므로)
