-- =====================================================
-- SSCM (Student Score & Counseling Management)
-- Flyway Migration V1: 초기 스키마 생성
-- 작성자: 이백엔드 (Backend Lee)
-- 작성일: 2025-03-12
-- =====================================================

-- =====================================================
-- 1. users (사용자 기본 테이블)
-- =====================================================
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20)     NOT NULL CHECK (role IN ('TEACHER', 'STUDENT', 'PARENT')),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_deleted_at ON users(deleted_at);

COMMENT ON TABLE users IS '사용자 기본 정보 (교사/학생/학부모 공통)';
COMMENT ON COLUMN users.email IS '로그인 이메일 — Sprint 3에서 암호화 적용 예정';
COMMENT ON COLUMN users.name IS '사용자 이름 — Sprint 3에서 암호화 적용 예정';
COMMENT ON COLUMN users.phone IS '연락처 — Sprint 3에서 암호화 적용 예정';
COMMENT ON COLUMN users.deleted_at IS 'Soft Delete: NULL이면 활성, 값이 있으면 삭제됨';

-- =====================================================
-- 2. teachers (교사 상세)
-- =====================================================
CREATE TABLE teachers (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    department      VARCHAR(50),
    homeroom_grade  INTEGER,
    homeroom_class  INTEGER
);

COMMENT ON TABLE teachers IS '교사 상세 정보';
COMMENT ON COLUMN teachers.department IS '담당 교과 (예: 수학, 영어)';
COMMENT ON COLUMN teachers.homeroom_grade IS '담임 학년 (NULL이면 담임 아님)';
COMMENT ON COLUMN teachers.homeroom_class IS '담임 반';

-- =====================================================
-- 3. students (학생 상세)
-- =====================================================
CREATE TABLE students (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    grade           INTEGER         NOT NULL CHECK (grade BETWEEN 1 AND 3),
    class_num       INTEGER         NOT NULL,
    student_num     INTEGER         NOT NULL,
    admission_year  INTEGER         NOT NULL
);

CREATE UNIQUE INDEX idx_students_grade_class_num ON students(grade, class_num, student_num);

COMMENT ON TABLE students IS '학생 상세 정보';
COMMENT ON COLUMN students.grade IS '학년 (1~3)';
COMMENT ON COLUMN students.class_num IS '반';
COMMENT ON COLUMN students.student_num IS '번호';

-- =====================================================
-- 4. parents (학부모 상세)
-- =====================================================
CREATE TABLE parents (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE parents IS '학부모 상세 정보';

-- =====================================================
-- 5. parent_student (학부모-학생 M:N 관계)
-- =====================================================
CREATE TABLE parent_student (
    parent_id       BIGINT          NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    student_id      BIGINT          NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    relationship    VARCHAR(20)     NOT NULL CHECK (relationship IN ('FATHER', 'MOTHER', 'GUARDIAN')),
    PRIMARY KEY (parent_id, student_id)
);

COMMENT ON TABLE parent_student IS '학부모-학생 관계 (M:N)';

-- =====================================================
-- 6. subjects (과목)
-- =====================================================
CREATE TABLE subjects (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    code            VARCHAR(20)     NOT NULL UNIQUE,
    description     TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE subjects IS '과목 마스터 테이블';

-- 기본 과목 데이터 삽입
INSERT INTO subjects (name, code) VALUES
    ('국어', 'KOR'),
    ('수학', 'MATH'),
    ('영어', 'ENG'),
    ('사회', 'SOC'),
    ('과학', 'SCI'),
    ('역사', 'HIST'),
    ('도덕', 'ETH'),
    ('체육', 'PE'),
    ('음악', 'MUS'),
    ('미술', 'ART'),
    ('기술·가정', 'TECH'),
    ('정보', 'INFO');

-- =====================================================
-- 7. scores (성적)
-- =====================================================
CREATE TABLE scores (
    id              BIGSERIAL       PRIMARY KEY,
    student_id      BIGINT          NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    subject_id      BIGINT          NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    teacher_id      BIGINT          NOT NULL REFERENCES teachers(id),
    year            INTEGER         NOT NULL,
    semester        INTEGER         NOT NULL CHECK (semester IN (1, 2)),
    score           DECIMAL(5, 2)   NOT NULL CHECK (score >= 0 AND score <= 100),
    grade_letter    VARCHAR(5),
    rank            INTEGER,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          REFERENCES users(id),
    updated_by      BIGINT          REFERENCES users(id),
    UNIQUE (student_id, subject_id, year, semester)
);

CREATE INDEX idx_scores_student_semester ON scores(student_id, year, semester);
CREATE INDEX idx_scores_subject_semester ON scores(subject_id, year, semester);

COMMENT ON TABLE scores IS '학생 성적 (과목별, 학기별)';
COMMENT ON COLUMN scores.grade_letter IS '등급 (A+~F) — 애플리케이션 레벨에서 자동 계산';
COMMENT ON COLUMN scores.rank IS '석차 — 애플리케이션 레벨에서 자동 계산';

-- =====================================================
-- 8. student_records (학생부)
-- =====================================================
CREATE TABLE student_records (
    id              BIGSERIAL       PRIMARY KEY,
    student_id      BIGINT          NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    year            INTEGER         NOT NULL,
    semester        INTEGER         NOT NULL CHECK (semester IN (1, 2)),
    category        VARCHAR(50)     NOT NULL CHECK (category IN (
                        'ATTENDANCE', 'SPECIAL_NOTE', 'AWARD', 'VOLUNTEER', 'OTHER'
                    )),
    content         JSONB           NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          REFERENCES users(id),
    updated_by      BIGINT          REFERENCES users(id)
);

CREATE INDEX idx_student_records_student ON student_records(student_id, year, semester);
CREATE INDEX idx_student_records_category ON student_records(category);
CREATE INDEX idx_student_records_content ON student_records USING GIN (content);

COMMENT ON TABLE student_records IS '학생부 (출결, 특기사항, 수상, 봉사 등)';
COMMENT ON COLUMN student_records.content IS 'JSONB: 카테고리별 유연한 데이터 저장';

-- =====================================================
-- 9. feedbacks (피드백)
-- =====================================================
CREATE TABLE feedbacks (
    id                      BIGSERIAL       PRIMARY KEY,
    student_id              BIGINT          NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    teacher_id              BIGINT          NOT NULL REFERENCES teachers(id),
    category                VARCHAR(30)     NOT NULL CHECK (category IN (
                                'ACADEMIC', 'BEHAVIOR', 'ATTENDANCE', 'ATTITUDE', 'GENERAL'
                            )),
    content                 TEXT            NOT NULL,
    is_visible_to_student   BOOLEAN         NOT NULL DEFAULT FALSE,
    is_visible_to_parent    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feedbacks_student ON feedbacks(student_id, created_at DESC);
CREATE INDEX idx_feedbacks_teacher ON feedbacks(teacher_id);
CREATE INDEX idx_feedbacks_category ON feedbacks(category);

COMMENT ON TABLE feedbacks IS '교사 → 학생 피드백';
COMMENT ON COLUMN feedbacks.is_visible_to_student IS 'TRUE이면 학생이 조회 가능';
COMMENT ON COLUMN feedbacks.is_visible_to_parent IS 'TRUE이면 학부모가 조회 가능';

-- =====================================================
-- 10. counselings (상담 내역)
-- =====================================================
CREATE TABLE counselings (
    id                  BIGSERIAL       PRIMARY KEY,
    student_id          BIGINT          NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    teacher_id          BIGINT          NOT NULL REFERENCES teachers(id),
    counsel_date        DATE            NOT NULL,
    category            VARCHAR(30)     NOT NULL CHECK (category IN (
                            'ACADEMIC', 'CAREER', 'BEHAVIOR', 'PERSONAL', 'OTHER'
                        )),
    content             TEXT            NOT NULL,
    next_plan           TEXT,
    next_counsel_date   DATE,
    is_shared           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_counselings_student ON counselings(student_id, counsel_date DESC);
CREATE INDEX idx_counselings_teacher ON counselings(teacher_id);
CREATE INDEX idx_counselings_shared ON counselings(is_shared);
CREATE INDEX idx_counselings_category ON counselings(category);

COMMENT ON TABLE counselings IS '교사-학생 상담 내역';
COMMENT ON COLUMN counselings.is_shared IS 'TRUE이면 다른 교사들도 조회 가능';
COMMENT ON COLUMN counselings.next_plan IS '후속 상담 계획';

-- =====================================================
-- 11. notifications (알림)
-- =====================================================
CREATE TABLE notifications (
    id              BIGSERIAL       PRIMARY KEY,
    recipient_id    BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(30)     NOT NULL CHECK (type IN (
                        'SCORE_UPDATE', 'FEEDBACK_NEW', 'COUNSEL_UPDATE', 'SYSTEM'
                    )),
    title           VARCHAR(200)    NOT NULL,
    message         TEXT            NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    BIGINT,
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, is_read, created_at DESC);

COMMENT ON TABLE notifications IS '사용자 알림';
COMMENT ON COLUMN notifications.reference_type IS '참조 엔티티 타입 (SCORE, FEEDBACK, COUNSEL 등)';
COMMENT ON COLUMN notifications.reference_id IS '참조 엔티티 ID — 알림 클릭 시 해당 화면으로 이동';

-- =====================================================
-- updated_at 자동 갱신 트리거
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_scores_updated_at
    BEFORE UPDATE ON scores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_student_records_updated_at
    BEFORE UPDATE ON student_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_feedbacks_updated_at
    BEFORE UPDATE ON feedbacks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_counselings_updated_at
    BEFORE UPDATE ON counselings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
