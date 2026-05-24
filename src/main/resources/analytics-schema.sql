-- ============================================================
-- SSCM Analytics DB 스키마
-- 목적: 운영 DB의 데이터를 집계하여 대시보드 조회용으로 저장
-- 사용: 별도 PostgreSQL 인스턴스 (sscm_analytics, port 5433)
-- ============================================================

-- 1. 학생 성적 요약
-- 언제 갱신? Score가 등록/수정/삭제될 때
-- 무엇을 보여줌? 학생이 해당 학기에 몇 과목을 수강하고, 평균이 얼마인지
CREATE TABLE IF NOT EXISTS student_score_summary (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL,
    student_name    VARCHAR(100) NOT NULL,
    academic_year   INTEGER NOT NULL,
    semester        INTEGER NOT NULL,
    subject_count   INTEGER NOT NULL DEFAULT 0,     -- 수강 과목 수
    total_score     DECIMAL(7,2) NOT NULL DEFAULT 0, -- 총점
    average_score   DECIMAL(5,2) NOT NULL DEFAULT 0, -- 평균 점수
    highest_score   DECIMAL(5,2),                    -- 최고점
    lowest_score    DECIMAL(5,2),                    -- 최저점
    average_grade   VARCHAR(5),                      -- 평균 등급 (A+, B 등)
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)      -- upsert 키: 같은 학생+학기면 갱신
);
CREATE INDEX IF NOT EXISTS idx_score_summary_student ON student_score_summary(student_id);
CREATE INDEX IF NOT EXISTS idx_score_summary_year ON student_score_summary(academic_year, semester);


-- 2. 학생 기록 요약 (출결, 수상, 봉사, 세특, 종합의견)
-- 언제 갱신? StudentRecord가 등록될 때
-- 무엇을 보여줌? 학생의 학기별 각 카테고리 기록이 몇 건인지
CREATE TABLE IF NOT EXISTS student_attendance_summary (
    id                    BIGSERIAL PRIMARY KEY,
    student_id            BIGINT NOT NULL,
    academic_year         INTEGER NOT NULL,
    semester              INTEGER NOT NULL,
    attendance_count      INTEGER NOT NULL DEFAULT 0,  -- 출결 기록 수
    award_count           INTEGER NOT NULL DEFAULT 0,  -- 수상 수
    volunteer_count       INTEGER NOT NULL DEFAULT 0,  -- 봉사 수
    special_note_count    INTEGER NOT NULL DEFAULT 0,  -- 세특(교과 특기사항) 수
    general_opinion_count INTEGER NOT NULL DEFAULT 0,  -- 종합의견 수
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
CREATE INDEX IF NOT EXISTS idx_attend_summary_student ON student_attendance_summary(student_id);


-- 3. 학생 피드백 요약
-- 언제 갱신? Feedback이 등록될 때
-- 무엇을 보여줌? 학생이 학기별로 어떤 카테고리의 피드백을 몇 건 받았는지
CREATE TABLE IF NOT EXISTS student_feedback_summary (
    id                   BIGSERIAL PRIMARY KEY,
    student_id           BIGINT NOT NULL,
    academic_year        INTEGER NOT NULL,
    semester             INTEGER NOT NULL,
    total_feedback_count INTEGER NOT NULL DEFAULT 0,   -- 전체 피드백 수
    academic_count       INTEGER NOT NULL DEFAULT 0,   -- 학업 관련
    behavior_count       INTEGER NOT NULL DEFAULT 0,   -- 행동 관련
    attendance_count     INTEGER NOT NULL DEFAULT 0,   -- 출결 관련
    attitude_count       INTEGER NOT NULL DEFAULT 0,   -- 태도 관련
    general_count        INTEGER NOT NULL DEFAULT 0,   -- 일반
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
CREATE INDEX IF NOT EXISTS idx_feedback_summary_student ON student_feedback_summary(student_id);


-- 4. 학생 상담 요약
-- 언제 갱신? Counseling이 등록/수정될 때
-- 무엇을 보여줌? 학생이 학기별로 어떤 상담을 몇 번 받았고, 마지막 상담일이 언제인지
CREATE TABLE IF NOT EXISTS student_counseling_summary (
    id                   BIGSERIAL PRIMARY KEY,
    student_id           BIGINT NOT NULL,
    academic_year        INTEGER NOT NULL,
    semester             INTEGER NOT NULL,
    total_counsel_count  INTEGER NOT NULL DEFAULT 0,
    academic_count       INTEGER NOT NULL DEFAULT 0,   -- 학업 상담
    career_count         INTEGER NOT NULL DEFAULT 0,   -- 진로 상담
    behavior_count       INTEGER NOT NULL DEFAULT 0,   -- 행동 상담
    personal_count       INTEGER NOT NULL DEFAULT 0,   -- 개인 상담
    other_count          INTEGER NOT NULL DEFAULT 0,   -- 기타
    last_counsel_date    DATE,                         -- 마지막 상담일
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
CREATE INDEX IF NOT EXISTS idx_counsel_summary_student ON student_counseling_summary(student_id);


-- 5. 과목 통계
-- 언제 갱신? Score가 등록/수정/삭제될 때
-- 무엇을 보여줌? 해당 과목을 수강한 학생 수, 평균, 표준편차, 등급 분포
CREATE TABLE IF NOT EXISTS subject_statistics (
    id              BIGSERIAL PRIMARY KEY,
    subject_id      BIGINT NOT NULL,
    subject_name    VARCHAR(100) NOT NULL,
    academic_year   INTEGER NOT NULL,
    semester        INTEGER NOT NULL,
    student_count   INTEGER NOT NULL DEFAULT 0,        -- 수강 학생 수
    average_score   DECIMAL(5,2) NOT NULL DEFAULT 0,
    max_score       DECIMAL(5,2),
    min_score       DECIMAL(5,2),
    std_deviation   DECIMAL(5,2),                      -- 표준편차 (점수 분포가 얼마나 퍼져있는지)
    grade_a_count   INTEGER NOT NULL DEFAULT 0,        -- A+, A 등급 학생 수
    grade_b_count   INTEGER NOT NULL DEFAULT 0,
    grade_c_count   INTEGER NOT NULL DEFAULT 0,
    grade_d_count   INTEGER NOT NULL DEFAULT 0,
    grade_f_count   INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (subject_id, academic_year, semester)
);
CREATE INDEX IF NOT EXISTS idx_subject_stats_year ON subject_statistics(academic_year, semester);


-- 6. 학생 학습 대시보드 (종합 뷰)
-- 언제 갱신? 위 테이블 중 하나라도 갱신될 때 함께 갱신
-- 무엇을 보여줌? 학생 한 명의 학기별 학습 현황을 한눈에
CREATE TABLE IF NOT EXISTS student_learning_dashboard (
    id                    BIGSERIAL PRIMARY KEY,
    student_id            BIGINT NOT NULL,
    student_name          VARCHAR(100) NOT NULL,
    academic_year         INTEGER NOT NULL,
    semester              INTEGER NOT NULL,
    -- 성적
    avg_score             DECIMAL(5,2),                -- 전 과목 평균
    score_trend           VARCHAR(10),                 -- 전 학기 대비 추이: UP, DOWN, STABLE
    -- 학생부 기록
    attendance_count      INTEGER NOT NULL DEFAULT 0,
    award_count           INTEGER NOT NULL DEFAULT 0,
    -- 피드백
    total_feedback_count  INTEGER NOT NULL DEFAULT 0,
    -- 상담
    total_counsel_count   INTEGER NOT NULL DEFAULT 0,
    last_counsel_date     DATE,
    -- 위험도 지표
    risk_level            VARCHAR(10) DEFAULT 'LOW',   -- LOW, MEDIUM, HIGH
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, academic_year, semester)
);
CREATE INDEX IF NOT EXISTS idx_dashboard_student ON student_learning_dashboard(student_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_risk ON student_learning_dashboard(risk_level);
