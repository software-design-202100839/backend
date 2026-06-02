package com.sscm.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * 3개 학교 × 1,000명 규모 대규모 시드 데이터 생성.
 * 부하 테스트 및 멀티테넌시 검증용.
 *
 * 엔드포인트: POST /api/v1/dev/seed/large-scale?reset=true&key=...
 *
 * 생성 규모:
 *   School 3개, User ~6,150명, Score 90,000건,
 *   Feedback 18,000건, Counseling 9,000건, StudentRecord ~18,000건
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LargeScaleSeedService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate txTemplate;

    // ── 설정 상수 ────────────────────────────────────────────

    private static final int STUDENTS_PER_SCHOOL = 1000;
    private static final int TEACHERS_PER_SCHOOL = 50;
    private static final int CLASSES_PER_GRADE = 10;
    private static final int SCORE_SUBJECTS = 5;
    private static final int[] YEARS = {2024, 2025, 2026};
    private static final int BATCH_SIZE = 1000;

    private static final String[][] SCHOOL_DEFS = {
            {"HANBIT", "한빛중학교"},
            {"SAEBYEOL", "새별중학교"},
            {"PUREUN", "푸른중학교"}
    };

    private static final String[] DEPARTMENTS = {
            "국어", "수학", "영어", "과학", "사회", "역사", "도덕", "체육", "음악", "미술"
    };

    // ── 메인 진입점 ──────────────────────────────────────────

    public Map<String, Object> seedCore(boolean reset) {
        long start = System.currentTimeMillis();

        if (reset) {
            // 별도 트랜잭션으로 truncate — 이후 seed 실패해도 rollback되지 않음
            txTemplate.executeWithoutResult(status -> resetAllData());
        } else {
            Long existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schools WHERE code IN ('HANBIT','SAEBYEOL','PUREUN')", Long.class);
            if (existing != null && existing > 0) {
                throw new RuntimeException("시드 데이터가 이미 존재합니다. reset=true로 실행하세요.");
            }
        }

        String defaultPw = passwordEncoder.encode("password1234");
        String adminPw = passwordEncoder.encode("admin1234");
        String teacherPw = passwordEncoder.encode("teacher1234");
        String studentPw = passwordEncoder.encode("student1234");
        String parentPw = passwordEncoder.encode("parent1234");
        Random rng = new Random(42L);

        // V1 마이그레이션 과목 (첫 5개: 국어,수학,영어,사회,과학)
        List<Long> subjectIds = jdbcTemplate.queryForList(
                "SELECT id FROM subjects ORDER BY id LIMIT ?", Long.class, SCORE_SUBJECTS);
        if (subjectIds.size() < SCORE_SUBJECTS) {
            throw new RuntimeException("과목 데이터 부족. Flyway V1 마이그레이션 확인 필요.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (int si = 0; si < SCHOOL_DEFS.length; si++) {
            final String code = SCHOOL_DEFS[si][0];
            final String fname = SCHOOL_DEFS[si][1];
            final int schoolIdx = si;
            final int classNumOffset = si * 100; // 학교별 class_num 충돌 방지 (UNIQUE 제약)

            Map<String, Object> sr = txTemplate.execute(status ->
                    seedOneSchool(code, fname, schoolIdx, classNumOffset, subjectIds,
                            adminPw, teacherPw, studentPw, parentPw, defaultPw, rng));
            result.put(code, sr);
            log.info("[LARGE SEED] {} 완료: {}", fname, sr);
        }

        long elapsed = System.currentTimeMillis() - start;
        result.put("totalElapsedMs", elapsed);
        log.info("[LARGE SEED] === 전체 완료: {}ms ===", elapsed);
        return result;
    }

    // ── 데이터 초기화 ────────────────────────────────────────

    // Security: seed key로 보호됨 (DevSeedController.validateSeedKey).
    // 프로덕션 비즈니스 로직이 아닌 발표 검증/부하 테스트 데이터 초기화 전용.
    @SuppressWarnings("java:S2077") // SQL injection — 테이블명은 하드코딩된 허용 목록이므로 안전
    private void resetAllData() {
        log.info("[LARGE SEED] 전체 데이터 초기화 시작...");
        // 허용된 테이블명만 사용 (외부 입력 없음)
        String[] tables = {
                "teacher_report_edits", "ai_generated_reports", "ai_request_logs",
                "risk_alert_history", "alert_suppressions",
                "feedback_embeddings", "counseling_embeddings",
                "audit_logs", "notifications",
                "student_records", "counselings", "feedbacks", "scores",
                "teacher_assignments", "student_enrollments", "classes",
                "parent_student", "parents", "students", "teachers",
                "refresh_tokens", "token_blacklist", "invite_tokens",
                "users", "schools"
        };
        for (String table : tables) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE"); // NOSONAR — 허용 목록 기반
        }
        log.info("[LARGE SEED] 전체 데이터 초기화 완료");
    }

    // ── 학교별 시드 ──────────────────────────────────────────

    private Map<String, Object> seedOneSchool(
            String code, String name, int schoolIndex, int classNumOffset,
            List<Long> subjectIds,
            String adminPw, String teacherPw, String studentPw, String parentPw, String defaultPw,
            Random rng) {

        Map<String, Object> counts = new LinkedHashMap<>();

        // 1. School
        Long schoolId = allocateId("schools_id_seq");
        jdbcTemplate.update(
                "INSERT INTO schools (id, name, code) VALUES (?, ?, ?)",
                schoolId, name, code);
        counts.put("schoolId", schoolId);

        // 2. Admin
        createSingleUser(
                schoolIndex == 0 ? "admin@sscm.dev" : "admin-" + code + "@seed.sscm.dev",
                adminPw, "관리자", String.format("010-9%d00-0001", schoolIndex),
                "ADMIN", schoolId);
        counts.put("admins", 1);

        // 3. Teachers (50명) — teacherIds=teachers.id, teacherUserIds=users.id
        List<Long> teacherUserIds = allocateIds("users_id_seq", TEACHERS_PER_SCHOOL);
        List<Long> teacherIds = createTeachers(code, schoolIndex, schoolId, teacherPw, defaultPw, teacherUserIds);
        counts.put("teachers", teacherIds.size());

        // 4. Students (1000명)
        List<Long> studentIds = createStudents(code, schoolIndex, schoolId, studentPw, defaultPw);
        counts.put("students", studentIds.size());

        // 5. Parents (1000명) + ParentStudent
        int parentCount = createParentsAndLink(code, schoolIndex, schoolId, parentPw, defaultPw, studentIds);
        counts.put("parents", parentCount);

        // 6. ClassRooms (3년 × 10반 = 30)
        Map<String, Long> classRoomMap = createClassRooms(schoolId, classNumOffset, teacherIds);
        counts.put("classRooms", classRoomMap.size());

        // 7. StudentEnrollments
        int enrollCount = createEnrollments(studentIds, classRoomMap, classNumOffset);
        counts.put("enrollments", enrollCount);

        // 8. TeacherAssignments
        int assignCount = createTeacherAssignments(teacherIds, classRoomMap, subjectIds, classNumOffset);
        counts.put("assignments", assignCount);

        // 9. Scores (30,000건) — created_by/updated_by는 users.id여야 함
        int scoreCount = batchInsertScores(studentIds, subjectIds, teacherIds, teacherUserIds, rng);
        counts.put("scores", scoreCount);

        // 10. Feedbacks (6,000건)
        int feedbackCount = batchInsertFeedbacks(studentIds, teacherIds, teacherUserIds, rng);
        counts.put("feedbacks", feedbackCount);

        // 11. Counselings (3,000건)
        int counselCount = batchInsertCounselings(studentIds, teacherIds, teacherUserIds, rng);
        counts.put("counselings", counselCount);

        // 12. StudentRecords (6,000건)
        int recordCount = batchInsertStudentRecords(studentIds, subjectIds, teacherUserIds.get(0), rng);
        counts.put("studentRecords", recordCount);

        return counts;
    }

    // ── 사용자 생성 ──────────────────────────────────────────

    private List<Long> createTeachers(String code, int schoolIndex, Long schoolId,
                                       String teacherPw, String defaultPw,
                                       List<Long> userIds) {
        int count = TEACHERS_PER_SCHOOL;
        List<Long> teacherIds = allocateIds("teachers_id_seq", count);

        List<Object[]> userRows = new ArrayList<>(count);
        List<Object[]> teacherRows = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String email, pw, tName;
            if (i == 0 && schoolIndex == 0) {
                email = "teacher@sscm.dev"; pw = teacherPw; tName = "테스트교사";
            } else if (i == 0) {
                email = "teacher-" + code + "@seed.sscm.dev"; pw = teacherPw; tName = code + "교사";
            } else {
                email = String.format("t%03d@%s.seed.sscm.dev", i + 1, code);
                pw = defaultPw;
                tName = String.format("%s교사%d", code, i + 1);
            }
            String phone = String.format("010-1%d%02d-%04d", schoolIndex, 0, i + 1);

            userRows.add(new Object[]{userIds.get(i), email, pw, tName, phone, "TEACHER", schoolId});
            teacherRows.add(new Object[]{teacherIds.get(i), userIds.get(i), DEPARTMENTS[i % DEPARTMENTS.length]});
        }

        batchInsert("INSERT INTO users (id,email,password_hash,name,phone,role,school_id,is_active,is_activated,created_at,updated_at) " +
                "VALUES (?,?,?,?,?,?,?,true,true,NOW(),NOW())", userRows);
        batchInsert("INSERT INTO teachers (id,user_id,department) VALUES (?,?,?)", teacherRows);

        return teacherIds;
    }

    private List<Long> createStudents(String code, int schoolIndex, Long schoolId,
                                       String studentPw, String defaultPw) {
        int count = STUDENTS_PER_SCHOOL;
        List<Long> userIds = allocateIds("users_id_seq", count);
        List<Long> studentIds = allocateIds("students_id_seq", count);

        List<Object[]> userRows = new ArrayList<>(count);
        List<Object[]> studentRows = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String email, pw, sName;
            if (i == 0 && schoolIndex == 0) {
                email = "student@sscm.dev"; pw = studentPw; sName = "테스트학생";
            } else {
                email = String.format("s%04d@%s.seed.sscm.dev", i + 1, code);
                pw = defaultPw;
                sName = String.format("학생%d", i + 1);
            }
            String phone = String.format("010-2%d%02d-%04d", schoolIndex, 0, i + 1);

            userRows.add(new Object[]{userIds.get(i), email, pw, sName, phone, "STUDENT", schoolId});
            studentRows.add(new Object[]{studentIds.get(i), userIds.get(i), 2024}); // admission_year
        }

        batchInsert("INSERT INTO users (id,email,password_hash,name,phone,role,school_id,is_active,is_activated,created_at,updated_at) " +
                "VALUES (?,?,?,?,?,?,?,true,true,NOW(),NOW())", userRows);
        batchInsert("INSERT INTO students (id,user_id,admission_year) VALUES (?,?,?)", studentRows);

        return studentIds;
    }

    private int createParentsAndLink(String code, int schoolIndex, Long schoolId,
                                      String parentPw, String defaultPw, List<Long> studentIds) {
        int count = studentIds.size(); // 1:1
        List<Long> userIds = allocateIds("users_id_seq", count);
        List<Long> parentIds = allocateIds("parents_id_seq", count);

        List<Object[]> userRows = new ArrayList<>(count);
        List<Object[]> parentRows = new ArrayList<>(count);
        List<Object[]> linkRows = new ArrayList<>(count);

        String[] relationships = {"FATHER", "MOTHER", "GUARDIAN"};

        for (int i = 0; i < count; i++) {
            String email, pw, pName;
            if (i == 0 && schoolIndex == 0) {
                email = "parent@sscm.dev"; pw = parentPw; pName = "테스트학부모";
            } else {
                email = String.format("p%04d@%s.seed.sscm.dev", i + 1, code);
                pw = defaultPw;
                pName = String.format("학부모%d", i + 1);
            }
            String phone = String.format("010-3%d%02d-%04d", schoolIndex, 0, i + 1);

            userRows.add(new Object[]{userIds.get(i), email, pw, pName, phone, "PARENT", schoolId});
            parentRows.add(new Object[]{parentIds.get(i), userIds.get(i)});
            linkRows.add(new Object[]{parentIds.get(i), studentIds.get(i), relationships[i % 3]});
        }

        batchInsert("INSERT INTO users (id,email,password_hash,name,phone,role,school_id,is_active,is_activated,created_at,updated_at) " +
                "VALUES (?,?,?,?,?,?,?,true,true,NOW(),NOW())", userRows);
        batchInsert("INSERT INTO parents (id,user_id) VALUES (?,?)", parentRows);
        batchInsert("INSERT INTO parent_student (parent_id,student_id,relationship) VALUES (?,?,?)", linkRows);

        return count;
    }

    // ── 학사 구조 ────────────────────────────────────────────

    /** 3년 × 10반 = 30 ClassRooms. key: "year-grade" → classRoomId */
    private Map<String, Long> createClassRooms(Long schoolId, int classNumOffset, List<Long> teacherIds) {
        int total = YEARS.length * CLASSES_PER_GRADE; // 30
        List<Long> crIds = allocateIds("classes_id_seq", total);
        List<Object[]> rows = new ArrayList<>(total);
        Map<String, Long> map = new LinkedHashMap<>();

        int idx = 0;
        for (int yi = 0; yi < YEARS.length; yi++) {
            int year = YEARS[yi];
            int grade = yi + 1; // 2024→1학년, 2025→2학년, 2026→3학년
            for (int c = 1; c <= CLASSES_PER_GRADE; c++) {
                Long crId = crIds.get(idx);
                int classNum = classNumOffset + c; // 학교별 충돌 방지
                Long homeroomTeacherId = teacherIds.get(idx % teacherIds.size());

                rows.add(new Object[]{crId, year, grade, classNum, homeroomTeacherId, schoolId});
                map.put(year + "-" + c, crId); // c는 학교 내 인덱스 (1~10)
                idx++;
            }
        }

        batchInsert("INSERT INTO classes (id,academic_year,grade,class_num,homeroom_teacher_id,school_id) VALUES (?,?,?,?,?,?)", rows);
        return map;
    }

    private int createEnrollments(List<Long> studentIds, Map<String, Long> classRoomMap, int classNumOffset) {
        List<Object[]> rows = new ArrayList<>();

        for (int yi = 0; yi < YEARS.length; yi++) {
            int year = YEARS[yi];
            for (int i = 0; i < studentIds.size(); i++) {
                int classIdx = (i / (STUDENTS_PER_SCHOOL / CLASSES_PER_GRADE)) + 1; // 1~10
                if (classIdx > CLASSES_PER_GRADE) classIdx = CLASSES_PER_GRADE;
                Long crId = classRoomMap.get(year + "-" + classIdx);
                int studentNum = (i % (STUDENTS_PER_SCHOOL / CLASSES_PER_GRADE)) + 1;

                rows.add(new Object[]{studentIds.get(i), crId, year, studentNum});
            }
        }

        batchInsert("INSERT INTO student_enrollments (student_id,class_id,academic_year,student_num) VALUES (?,?,?,?)", rows);
        return rows.size();
    }

    private int createTeacherAssignments(List<Long> teacherIds, Map<String, Long> classRoomMap,
                                          List<Long> subjectIds, int classNumOffset) {
        List<Object[]> rows = new ArrayList<>();

        for (int yi = 0; yi < YEARS.length; yi++) {
            int year = YEARS[yi];
            for (int c = 1; c <= CLASSES_PER_GRADE; c++) {
                Long crId = classRoomMap.get(year + "-" + c);
                for (int si = 0; si < subjectIds.size(); si++) {
                    int teacherIdx = (c * subjectIds.size() + si) % teacherIds.size();
                    rows.add(new Object[]{teacherIds.get(teacherIdx), crId, subjectIds.get(si), year});
                }
            }
        }

        batchInsert("INSERT INTO teacher_assignments (teacher_id,class_id,subject_id,academic_year) VALUES (?,?,?,?)", rows);
        return rows.size();
    }

    // ── 대량 데이터 (JDBC Batch) ─────────────────────────────

    private int batchInsertScores(List<Long> studentIds, List<Long> subjectIds,
                                   List<Long> teacherIds, List<Long> teacherUserIds, Random rng) {
        String sql = "INSERT INTO scores (student_id,subject_id,teacher_id,year,semester,score,grade_letter," +
                "created_at,updated_at,created_by,updated_by,version) " +
                "VALUES (?,?,?,?,?,?,?,NOW(),NOW(),?,?,0)";

        List<Object[]> rows = new ArrayList<>(STUDENTS_PER_SCHOOL * SCORE_SUBJECTS * YEARS.length * 2);

        for (int i = 0; i < studentIds.size(); i++) {
            Long studentId = studentIds.get(i);
            int group = i % 4; // 0=향상, 1=하락, 2=상위, 3=하위
            int tIdx = i % teacherIds.size();
            Long teacherId = teacherIds.get(tIdx);
            Long teacherUserId = teacherUserIds.get(tIdx); // scores.created_by FK → users.id

            for (Long subjectId : subjectIds) {
                int subjectOffset = rng.nextInt(10) - 5;
                int semIdx = 0;
                for (int year : YEARS) {
                    for (int sem = 1; sem <= 2; sem++) {
                        double base = calcBaseScore(group, semIdx, rng);
                        double raw = Math.max(0, Math.min(100, base + subjectOffset + rng.nextGaussian() * 8));
                        BigDecimal scoreVal = BigDecimal.valueOf(Math.round(raw));
                        String grade = gradeLetter(scoreVal.intValue());

                        rows.add(new Object[]{studentId, subjectId, teacherId, year, sem,
                                scoreVal, grade, teacherUserId, teacherUserId});
                        semIdx++;
                    }
                }
            }
        }

        batchInsert(sql, rows);
        return rows.size();
    }

    private int batchInsertFeedbacks(List<Long> studentIds, List<Long> teacherIds,
                                      List<Long> teacherUserIds, Random rng) {
        String sql = "INSERT INTO feedbacks (student_id,teacher_id,year,semester,category,content," +
                "is_visible_to_student,is_visible_to_parent,created_at,updated_at,version) " +
                "VALUES (?,?,?,?,?,?,?,?,NOW(),NOW(),0)";

        String[] categories = {"ACADEMIC", "BEHAVIOR", "ATTENDANCE", "ATTITUDE", "GENERAL"};
        List<Object[]> rows = new ArrayList<>();

        for (int i = 0; i < studentIds.size(); i++) {
            Long studentId = studentIds.get(i);
            Long teacherId = teacherIds.get(i % teacherIds.size());

            for (int year : YEARS) {
                for (int sem = 1; sem <= 2; sem++) {
                    String cat = categories[rng.nextInt(categories.length)];
                    String content = pickFeedback(cat, rng);
                    boolean visStu = rng.nextBoolean();
                    boolean visPar = rng.nextInt(3) == 0;

                    rows.add(new Object[]{studentId, teacherId, year, sem, cat, content, visStu, visPar});
                }
            }
        }

        batchInsert(sql, rows);
        return rows.size();
    }

    private int batchInsertCounselings(List<Long> studentIds, List<Long> teacherIds,
                                        List<Long> teacherUserIds, Random rng) {
        String sql = "INSERT INTO counselings (student_id,teacher_id,counsel_date,category,content," +
                "next_plan,next_counsel_date,created_at,updated_at,version) " +
                "VALUES (?,?,?,?,?,?,?,NOW(),NOW(),0)";

        String[] categories = {"ACADEMIC", "CAREER", "BEHAVIOR", "PERSONAL", "OTHER"};
        List<Object[]> rows = new ArrayList<>();

        for (int i = 0; i < studentIds.size(); i++) {
            Long studentId = studentIds.get(i);
            Long teacherId = teacherIds.get(i % teacherIds.size());

            for (int year : YEARS) {
                int month = 3 + rng.nextInt(8); // 3~10월
                int day = 1 + rng.nextInt(28);
                LocalDate counselDate = LocalDate.of(year, month, day);
                String cat = categories[rng.nextInt(categories.length)];
                String[] pair = pickCounseling(cat, rng);
                String nextPlan = (year == 2026) ? pair[1] : null;
                LocalDate nextDate = (year == 2026) ? counselDate.plusWeeks(2) : null;

                rows.add(new Object[]{studentId, teacherId, Date.valueOf(counselDate), cat, pair[0],
                        nextPlan, nextDate != null ? Date.valueOf(nextDate) : null});
            }
        }

        batchInsert(sql, rows);
        return rows.size();
    }

    private int batchInsertStudentRecords(List<Long> studentIds, List<Long> subjectIds,
                                          Long createdByUserId, Random rng) {
        String sql = "INSERT INTO student_records (student_id,year,semester,category,content," +
                "is_visible_to_student,is_visible_to_parent,review_status," +
                "created_at,updated_at,created_by,updated_by,version) " +
                "VALUES (?,?,?,?,?::jsonb,?,?,'DRAFT',NOW(),NOW(),?,?,0)";

        List<Object[]> rows = new ArrayList<>();

        for (Long studentId : studentIds) {
            for (int year : YEARS) {
                for (int sem = 1; sem <= 2; sem++) {
                    // 출결 기록
                    String attJson = String.format(
                            "{\"description\":\"%s\",\"absenceDays\":%d,\"lateDays\":%d}",
                            pickAttendance(rng), rng.nextInt(5), rng.nextInt(8));
                    rows.add(new Object[]{studentId, year, sem, "ATTENDANCE", attJson, true, false, createdByUserId, createdByUserId});

                    // 종합의견 (50% 확률)
                    if (rng.nextBoolean()) {
                        String opJson = String.format("{\"description\":\"%s\"}", pickGeneralOpinion(rng));
                        rows.add(new Object[]{studentId, year, sem, "GENERAL_OPINION", opJson, true, false, createdByUserId, createdByUserId});
                    }
                }
            }
        }

        batchInsert(sql, rows);
        return rows.size();
    }

    // ── 유틸리티 ─────────────────────────────────────────────

    @SuppressWarnings("java:S2077") // seqName은 내부 하드코딩된 시퀀스명만 전달됨
    private Long allocateId(String seqName) {
        return jdbcTemplate.queryForObject("SELECT nextval('" + seqName + "')", Long.class);
    }

    @SuppressWarnings("java:S2077")
    private List<Long> allocateIds(String seqName, int count) {
        if (count == 0) return List.of();
        return jdbcTemplate.queryForList(
                "SELECT nextval('" + seqName + "') FROM generate_series(1," + count + ")", Long.class);
    }

    private void createSingleUser(String email, String pwHash, String name, String phone,
                                   String role, Long schoolId) {
        jdbcTemplate.update(
                "INSERT INTO users (email,password_hash,name,phone,role,school_id,is_active,is_activated,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,?,true,true,NOW(),NOW())",
                email, pwHash, name, phone, role, schoolId);
    }

    private void batchInsert(String sql, List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            jdbcTemplate.batchUpdate(sql, rows.subList(i, end));
        }
    }

    // ── 성적 트렌드 계산 ─────────────────────────────────────

    private double calcBaseScore(int group, int semIdx, Random rng) {
        return switch (group) {
            case 0 -> 52 + semIdx * 4.5 + rng.nextInt(5);   // 향상
            case 1 -> 92 - semIdx * 4.0 - rng.nextInt(5);   // 하락
            case 2 -> 88 + rng.nextInt(8) - 3;               // 상위 고정
            case 3 -> 45 + rng.nextInt(10);                   // 하위 고정
            default -> 70;
        };
    }

    private String gradeLetter(int score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 85) return "B+";
        if (score >= 80) return "B";
        if (score >= 75) return "C+";
        if (score >= 70) return "C";
        if (score >= 65) return "D+";
        if (score >= 60) return "D";
        return "F";
    }

    // ── 콘텐츠 풀 ───────────────────────────────────────────

    private static final String[][] FEEDBACK_POOL = {
            {"ACADEMIC", "수학 문제 풀이 능력이 향상되고 있습니다"},
            {"ACADEMIC", "영어 독해에 더 집중이 필요합니다"},
            {"ACADEMIC", "국어 작문 실력이 눈에 띄게 좋아졌습니다"},
            {"ACADEMIC", "과제 제출이 꾸준하고 성실합니다"},
            {"ACADEMIC", "시험 준비를 더 체계적으로 할 필요가 있습니다"},
            {"ACADEMIC", "수업 중 발표를 더 적극적으로 하면 좋겠습니다"},
            {"BEHAVIOR", "수업 태도가 매우 모범적입니다"},
            {"BEHAVIOR", "친구들과의 협동심이 돋보입니다"},
            {"BEHAVIOR", "때때로 수업 중 떠드는 모습이 보입니다"},
            {"BEHAVIOR", "봉사 활동에 적극적으로 참여합니다"},
            {"ATTENDANCE", "지각이 잦아 주의가 필요합니다"},
            {"ATTENDANCE", "출결 관리가 완벽합니다"},
            {"ATTENDANCE", "최근 결석이 늘어 관심이 필요합니다"},
            {"ATTITUDE", "적극적으로 질문합니다"},
            {"ATTITUDE", "학습에 대한 의지가 강합니다"},
            {"ATTITUDE", "자기주도적 학습 습관이 잘 형성되어 있습니다"},
            {"GENERAL", "리더십이 돋보이는 학생입니다"},
            {"GENERAL", "전반적으로 밝고 긍정적인 학생입니다"},
            {"GENERAL", "창의적인 아이디어를 많이 제시합니다"},
    };

    private String pickFeedback(String category, Random rng) {
        List<String[]> pool = new ArrayList<>();
        for (String[] fb : FEEDBACK_POOL) {
            if (fb[0].equals(category)) pool.add(fb);
        }
        if (pool.isEmpty()) return "피드백 내용";
        return pool.get(rng.nextInt(pool.size()))[1];
    }

    private static final String[][][] COUNSEL_POOL = {
            {{"ACADEMIC"}, {"중간고사 성적 하락에 대한 상담. 수학 보충 수업 권유"}, {"수학 보충 수업 참여 확인"}},
            {{"ACADEMIC"}, {"영어 성적 향상을 위한 학습 계획 수립"}, {"영어 학습 계획 이행 여부 확인"}},
            {{"ACADEMIC"}, {"기말고사 대비 학습 전략 상담"}, {"시험 후 결과 분석 면담"}},
            {{"CAREER"}, {"의사가 되고 싶다고 함. 이과 진학 안내"}, {"이과 관련 체험활동 안내"}},
            {{"CAREER"}, {"IT 분야 관심. 코딩 교육 프로그램 안내"}, {"코딩 교육 참여 확인"}},
            {{"BEHAVIOR"}, {"친구 관계에서 갈등이 있음. 중재 필요"}, {"갈등 해소 여부 확인"}},
            {{"BEHAVIOR"}, {"수업 중 집중도 저하. 원인 파악 상담"}, {"집중도 개선 확인"}},
            {{"PERSONAL"}, {"가정 환경 변화로 힘들어하고 있음"}, {"심리 상태 확인 면담"}},
            {{"PERSONAL"}, {"자존감이 낮아 자신감 회복 상담"}, {"자존감 향상 프로그램 참여 확인"}},
            {{"OTHER"}, {"학급 임원 활동에 대한 격려"}, {"임원 활동 수행 점검"}},
    };

    private String[] pickCounseling(String category, Random rng) {
        List<String[][]> pool = new ArrayList<>();
        for (String[][] entry : COUNSEL_POOL) {
            if (entry[0][0].equals(category)) pool.add(entry);
        }
        if (pool.isEmpty()) return new String[]{"상담 내용", "후속 계획"};
        String[][] chosen = pool.get(rng.nextInt(pool.size()));
        return new String[]{chosen[1][0], chosen[2][0]};
    }

    private static final String[] ATTENDANCE_POOL = {
            "출결 상태 양호", "무단 지각 2회", "병결 3일",
            "개근", "체험학습 2일", "병결 후 보충학습 완료"
    };

    private String pickAttendance(Random rng) {
        return ATTENDANCE_POOL[rng.nextInt(ATTENDANCE_POOL.length)];
    }

    private static final String[] OPINION_POOL = {
            "성실하고 책임감 있는 학생입니다",
            "학업에 대한 열정이 돋보이며 꾸준히 성장하고 있습니다",
            "밝고 긍정적인 성격으로 학급 분위기에 기여합니다",
            "학습 습관 형성에 더 노력이 필요한 학생입니다",
            "집중력 향상을 위한 지속적인 관심이 필요합니다",
            "자신감을 키워주면 큰 성장이 기대됩니다"
    };

    private String pickGeneralOpinion(Random rng) {
        return OPINION_POOL[rng.nextInt(OPINION_POOL.length)];
    }
}
