-- pgvector 확장 (PostgreSQL 16 지원)
CREATE EXTENSION IF NOT EXISTS vector;

-- 피드백 임베딩
CREATE TABLE IF NOT EXISTS feedback_embeddings (
    id BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    academic_year INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    category VARCHAR(50),
    content_preview VARCHAR(200),
    embedding vector(3072),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_feedback_emb_school ON feedback_embeddings(school_id);
CREATE INDEX IF NOT EXISTS idx_feedback_emb_student ON feedback_embeddings(student_id);

-- 상담 임베딩
CREATE TABLE IF NOT EXISTS counseling_embeddings (
    id BIGSERIAL PRIMARY KEY,
    counseling_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    academic_year INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    category VARCHAR(50),
    content_preview VARCHAR(200),
    embedding vector(3072),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_counsel_emb_school ON counseling_embeddings(school_id);
CREATE INDEX IF NOT EXISTS idx_counsel_emb_student ON counseling_embeddings(student_id);

-- AI 생성 보고서
CREATE TABLE IF NOT EXISTS ai_generated_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    academic_year INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    prompt_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    model_name VARCHAR(50) NOT NULL DEFAULT 'gemini-2.5-flash',
    draft_text TEXT NOT NULL,
    reference_ids JSONB,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 교사 수정본
CREATE TABLE IF NOT EXISTS teacher_report_edits (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES ai_generated_reports(id),
    final_text TEXT NOT NULL,
    edit_distance INTEGER,
    edited_sections JSONB,
    edited_by BIGINT NOT NULL,
    edited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI 요청 감사 로그
CREATE TABLE IF NOT EXISTS ai_request_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    question TEXT NOT NULL,
    intent_type VARCHAR(30),
    used_tools TEXT,
    accessed_student_ids TEXT,
    response_summary VARCHAR(500),
    latency_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_log_school ON ai_request_logs(school_id);
CREATE INDEX IF NOT EXISTS idx_ai_log_user ON ai_request_logs(user_id);

-- 알림 억제
CREATE TABLE IF NOT EXISTS alert_suppressions (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    suppressed_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(teacher_id, student_id, rule_type)
);

-- 위험 알림 이력 (중복 방지용)
CREATE TABLE IF NOT EXISTS risk_alert_history (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    alert_date DATE NOT NULL,
    notification_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, rule_type, alert_date)
);
