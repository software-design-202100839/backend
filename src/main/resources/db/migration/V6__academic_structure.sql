-- =====================================================
-- V6: 학년도 기반 권한 구조 + 감사 로그 (SSCM-66)
-- classes, student_enrollments, teacher_assignments, audit_logs 신규
-- teachers, students 수정, counselings.is_shared 제거
-- student_records 카테고리 재정의 (세특 구분)
-- =====================================================

-- =====================================================
-- 1. teachers 수정 — homeroom 컬럼 제거 (classes 테이블로 이관)
-- =====================================================
ALTER TABLE teachers DROP COLUMN IF EXISTS homeroom_grade;
ALTER TABLE teachers DROP COLUMN IF EXISTS homeroom_class;

COMMENT ON TABLE teachers IS '교사 상세 정보. 담임 정보는 classes.homeroom_teacher_id로 관리';

-- =====================================================
-- 2. students 수정 — grade/class_num/student_num 제거 (enrollments로 이관)
-- =====================================================
DROP INDEX IF EXISTS idx_students_grade_class_num;
ALTER TABLE students DROP COLUMN IF EXISTS grade;
ALTER TABLE students DROP COLUMN IF EXISTS class_num;
ALTER TABLE students DROP COLUMN IF EXISTS student_num;

COMMENT ON TABLE students IS '학생 상세 정보. 소속 반/번호는 student_enrollments로 관리';

-- =====================================================
-- 3. classes (학년도별 반)
-- =====================================================
CREATE TABLE classes (
    id                  BIGSERIAL       PRIMARY KEY,
    academic_year       INTEGER         NOT NULL,
    grade               INTEGER         NOT NULL CHECK (grade BETWEEN 1 AND 3),
    class_num           INTEGER         NOT NULL,
    homeroom_teacher_id BIGINT          REFERENCES teachers(id) ON DELETE SET NULL,
    UNIQUE (academic_year, grade, class_num)
);

CREATE INDEX idx_classes_year ON classes(academic_year);
CREATE INDEX idx_classes_homeroom ON classes(homeroom_teacher_id);

COMMENT ON TABLE classes IS '학년도별 반. 학년도가 바뀌면 새 레코드 생성 (이전 데이터 보존)';
COMMENT ON COLUMN classes.homeroom_teacher_id IS '담임 교사 — NULL이면 미배정';

-- =====================================================
-- 4. student_enrollments (학생 반 배정 이력)
-- =====================================================
CREATE TABLE student_enrollments (
    id              BIGSERIAL       PRIMARY KEY,
    student_id      BIGINT          NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_id        BIGINT          NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    academic_year   INTEGER         NOT NULL,
    student_num     INTEGER         NOT NULL,
    UNIQUE (student_id, academic_year),
    UNIQUE (class_id, student_num)
);

CREATE INDEX idx_enrollments_student ON student_enrollments(student_id);
CREATE INDEX idx_enrollments_class ON student_enrollments(class_id);

COMMENT ON TABLE student_enrollments IS '학생의 학년도별 반 배정. 학년도마다 1개 레코드';

-- =====================================================
-- 5. teacher_assignments (교사 담당 과목 배정)
-- =====================================================
CREATE TABLE teacher_assignments (
    id              BIGSERIAL       PRIMARY KEY,
    teacher_id      BIGINT          NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    class_id        BIGINT          NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    subject_id      BIGINT          NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    academic_year   INTEGER         NOT NULL,
    UNIQUE (teacher_id, class_id, subject_id, academic_year)
);

CREATE INDEX idx_ta_teacher ON teacher_assignments(teacher_id, academic_year);
CREATE INDEX idx_ta_class_subject ON teacher_assignments(class_id, subject_id, academic_year);

COMMENT ON TABLE teacher_assignments IS '교사-반-과목 배정. 성적/세특 수정 권한 기준 테이블';

-- =====================================================
-- 6. student_records 수정 — 세특(SPECIAL) 구분 + 공개 여부 + 검토 상태
-- =====================================================
-- 6-1. 기존 카테고리 CHECK 제약 제거 후 재정의
ALTER TABLE student_records DROP CONSTRAINT IF EXISTS student_records_category_check;
ALTER TABLE student_records ADD CONSTRAINT student_records_category_check
    CHECK (category IN ('ATTENDANCE', 'GENERAL_OPINION', 'AWARD', 'VOLUNTEER', 'SPECIAL_NOTE', 'OTHER'));

-- 6-2. 세특용 과목 FK (BASIC이면 NULL)
ALTER TABLE student_records ADD COLUMN subject_id BIGINT REFERENCES subjects(id) ON DELETE SET NULL;

-- 6-3. 학생/학부모 공개 여부 (담임만 설정 가능)
ALTER TABLE student_records ADD COLUMN is_visible_to_student BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE student_records ADD COLUMN is_visible_to_parent  BOOLEAN NOT NULL DEFAULT FALSE;

-- 6-4. 담임 검토 상태
ALTER TABLE student_records ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
    CHECK (review_status IN ('DRAFT', 'REVIEWED', 'APPROVED'));

CREATE INDEX idx_student_records_subject ON student_records(student_id, subject_id) WHERE subject_id IS NOT NULL;

COMMENT ON COLUMN student_records.category IS 'BASIC: 기본학생부(담임), SPECIAL: 세특(교과교사)';
COMMENT ON COLUMN student_records.subject_id IS '세특(SPECIAL) 전용 과목 FK';
COMMENT ON COLUMN student_records.review_status IS '담임 검토 상태: DRAFT→REVIEWED→APPROVED';

-- =====================================================
-- 7. counselings 수정 — is_shared 제거 (전체 교사 공유 확정)
-- =====================================================
ALTER TABLE counselings DROP COLUMN IF EXISTS is_shared;
DROP INDEX IF EXISTS idx_counselings_shared;

COMMENT ON TABLE counselings IS '교사-학생 상담 내역. 전체 교사 공유 (학생/학부모 접근 없음)';

-- =====================================================
-- 8. audit_logs (변경 이력)
-- =====================================================
CREATE TABLE audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    table_name      VARCHAR(50)     NOT NULL,
    record_id       BIGINT          NOT NULL,
    field_name      VARCHAR(100)    NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_by      BIGINT          NOT NULL REFERENCES users(id),
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_table_record ON audit_logs(table_name, record_id);
CREATE INDEX idx_audit_logs_changed_by ON audit_logs(changed_by);
CREATE INDEX idx_audit_logs_changed_at ON audit_logs(changed_at DESC);

COMMENT ON TABLE audit_logs IS '교육 데이터 변경 이력. 삭제 불가, 수정만 허용 원칙과 함께 적용';
COMMENT ON COLUMN audit_logs.table_name IS '수정된 테이블명 (scores, feedbacks, counselings, student_records)';
