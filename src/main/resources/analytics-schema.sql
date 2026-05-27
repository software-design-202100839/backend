-- ============================================================
-- SSCM Analytics DB 스키마
-- 목적: 운영 DB의 데이터를 집계하여 대시보드 조회용으로 저장
-- 사용: 별도 PostgreSQL 인스턴스 (sscm_analytics, port 5433)
-- ============================================================

-- 1. 학생 성적 요약
CREATE TABLE IF NOT EXISTS student_score_summary (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL,
    student_name    VARCHAR(100) NOT NULL,
    school_id       BIGINT,
    academic_year   INTEGER NOT NULL,
    semester        INTEGER NOT NULL,
    subject_count   INTEGER NOT NULL DEFAULT 0,
    total_score     DECIMAL(7,2) NOT NULL DEFAULT 0,
    average_score   DECIMAL(5,2) NOT NULL DEFAULT 0,
    highest_score   DECIMAL(5,2),
    lowest_score    DECIMAL(5,2),
    average_grade   VARCHAR(5),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
ALTER TABLE student_score_summary ADD COLUMN IF NOT EXISTS school_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_score_summary_student ON student_score_summary(student_id);
CREATE INDEX IF NOT EXISTS idx_score_summary_year ON student_score_summary(academic_year, semester);
CREATE INDEX IF NOT EXISTS idx_score_summary_school ON student_score_summary(school_id);


-- 2. 학생 기록 요약 (출결, 수상, 봉사, 세특, 종합의견)
CREATE TABLE IF NOT EXISTS student_attendance_summary (
    id                    BIGSERIAL PRIMARY KEY,
    student_id            BIGINT NOT NULL,
    school_id             BIGINT,
    academic_year         INTEGER NOT NULL,
    semester              INTEGER NOT NULL,
    attendance_count      INTEGER NOT NULL DEFAULT 0,
    award_count           INTEGER NOT NULL DEFAULT 0,
    volunteer_count       INTEGER NOT NULL DEFAULT 0,
    special_note_count    INTEGER NOT NULL DEFAULT 0,
    general_opinion_count INTEGER NOT NULL DEFAULT 0,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
ALTER TABLE student_attendance_summary ADD COLUMN IF NOT EXISTS school_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_attend_summary_student ON student_attendance_summary(student_id);
CREATE INDEX IF NOT EXISTS idx_attend_summary_school ON student_attendance_summary(school_id);


-- 3. 학생 피드백 요약
CREATE TABLE IF NOT EXISTS student_feedback_summary (
    id                   BIGSERIAL PRIMARY KEY,
    student_id           BIGINT NOT NULL,
    school_id            BIGINT,
    academic_year        INTEGER NOT NULL,
    semester             INTEGER NOT NULL,
    total_feedback_count INTEGER NOT NULL DEFAULT 0,
    academic_count       INTEGER NOT NULL DEFAULT 0,
    behavior_count       INTEGER NOT NULL DEFAULT 0,
    attendance_count     INTEGER NOT NULL DEFAULT 0,
    attitude_count       INTEGER NOT NULL DEFAULT 0,
    general_count        INTEGER NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
ALTER TABLE student_feedback_summary ADD COLUMN IF NOT EXISTS school_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_feedback_summary_student ON student_feedback_summary(student_id);
CREATE INDEX IF NOT EXISTS idx_feedback_summary_school ON student_feedback_summary(school_id);


-- 4. 학생 상담 요약
CREATE TABLE IF NOT EXISTS student_counseling_summary (
    id                   BIGSERIAL PRIMARY KEY,
    student_id           BIGINT NOT NULL,
    school_id            BIGINT,
    academic_year        INTEGER NOT NULL,
    semester             INTEGER NOT NULL,
    total_counsel_count  INTEGER NOT NULL DEFAULT 0,
    academic_count       INTEGER NOT NULL DEFAULT 0,
    career_count         INTEGER NOT NULL DEFAULT 0,
    behavior_count       INTEGER NOT NULL DEFAULT 0,
    personal_count       INTEGER NOT NULL DEFAULT 0,
    other_count          INTEGER NOT NULL DEFAULT 0,
    last_counsel_date    DATE,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
ALTER TABLE student_counseling_summary ADD COLUMN IF NOT EXISTS school_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_counsel_summary_student ON student_counseling_summary(student_id);
CREATE INDEX IF NOT EXISTS idx_counsel_summary_school ON student_counseling_summary(school_id);


-- 5. 과목 통계
CREATE TABLE IF NOT EXISTS subject_statistics (
    id              BIGSERIAL PRIMARY KEY,
    subject_id      BIGINT NOT NULL,
    subject_name    VARCHAR(100) NOT NULL,
    school_id       BIGINT,
    academic_year   INTEGER NOT NULL,
    semester        INTEGER NOT NULL,
    student_count   INTEGER NOT NULL DEFAULT 0,
    average_score   DECIMAL(5,2) NOT NULL DEFAULT 0,
    max_score       DECIMAL(5,2),
    min_score       DECIMAL(5,2),
    std_deviation   DECIMAL(5,2),
    grade_a_count   INTEGER NOT NULL DEFAULT 0,
    grade_b_count   INTEGER NOT NULL DEFAULT 0,
    grade_c_count   INTEGER NOT NULL DEFAULT 0,
    grade_d_count   INTEGER NOT NULL DEFAULT 0,
    grade_f_count   INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (subject_id, academic_year, semester)
);
ALTER TABLE subject_statistics ADD COLUMN IF NOT EXISTS school_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_subject_stats_year ON subject_statistics(academic_year, semester);
CREATE INDEX IF NOT EXISTS idx_subject_stats_school ON subject_statistics(school_id);


-- 6. 학생 학습 대시보드 (종합 뷰)
CREATE TABLE IF NOT EXISTS student_learning_dashboard (
    id                    BIGSERIAL PRIMARY KEY,
    student_id            BIGINT NOT NULL,
    student_name          VARCHAR(100) NOT NULL,
    school_id             BIGINT,
    academic_year         INTEGER NOT NULL,
    semester              INTEGER NOT NULL,
    avg_score             DECIMAL(5,2),
    score_trend           VARCHAR(10),
    attendance_count      INTEGER NOT NULL DEFAULT 0,
    award_count           INTEGER NOT NULL DEFAULT 0,
    total_feedback_count  INTEGER NOT NULL DEFAULT 0,
    total_counsel_count   INTEGER NOT NULL DEFAULT 0,
    last_counsel_date     DATE,
    risk_level            VARCHAR(10) DEFAULT 'LOW',
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
ALTER TABLE student_learning_dashboard ADD COLUMN IF NOT EXISTS school_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_dashboard_student ON student_learning_dashboard(student_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_risk ON student_learning_dashboard(risk_level);
CREATE INDEX IF NOT EXISTS idx_dashboard_school ON student_learning_dashboard(school_id);
