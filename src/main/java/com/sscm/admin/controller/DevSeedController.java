package com.sscm.admin.controller;

import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.analytics.event.*;
import com.sscm.analytics.event.payload.*;
import com.sscm.analytics.chatbot.service.EmbeddingService;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.School;
import com.sscm.common.entity.StudentEnrollment;
import com.sscm.common.entity.TeacherAssignment;
import com.sscm.common.repository.ClassRoomRepository;
import com.sscm.common.repository.SchoolRepository;
import com.sscm.common.repository.StudentEnrollmentRepository;
import com.sscm.common.repository.TeacherAssignmentRepository;
import com.sscm.common.response.ApiResponse;
import com.sscm.common.tenant.TenantContext;
import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.entity.Counseling;
import com.sscm.counsel.repository.CounselingRepository;
import com.sscm.feedback.entity.Feedback;
import com.sscm.feedback.entity.FeedbackCategory;
import com.sscm.feedback.repository.FeedbackRepository;
import com.sscm.grade.entity.Score;
import com.sscm.grade.entity.Subject;
import com.sscm.grade.repository.ScoreRepository;
import com.sscm.grade.repository.SubjectRepository;
import com.sscm.student.entity.RecordCategory;
import com.sscm.student.entity.StudentRecord;
import com.sscm.student.repository.StudentRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * dev 환경 전용 — 역할별 테스트 계정 초기화.
 * POST /api/v1/dev/seed/admin → ADMIN 계정만 생성
 * POST /api/v1/dev/seed/all  → 전체 역할별 계정 초기화 (기존 시드 계정 삭제 후 재생성)
 *
 * 시드 계정 목록:
 *   ADMIN   : admin@sscm.dev    / admin1234   (DEV_SEED_ADMIN_PASSWORD)
 *   TEACHER : teacher@sscm.dev  / teacher1234
 *   STUDENT : student@sscm.dev  / student1234
 *   PARENT  : parent@sscm.dev   / parent1234
 */
@Tag(name = "Dev", description = "시드 데이터 관리 (seed-key 인증 필요)")
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevSeedController {

    // ── 시드 계정 고정값 ──────────────────────────────────────
    private static final String ADMIN_EMAIL    = "admin@sscm.dev";
    private static final String TEACHER_EMAIL  = "teacher@sscm.dev";
    private static final String STUDENT_EMAIL  = "student@sscm.dev";
    private static final String PARENT_EMAIL   = "parent@sscm.dev";

    private static final String ADMIN_PHONE    = "010-0000-0001";
    private static final String TEACHER_PHONE  = "010-1111-0001";
    private static final String STUDENT_PHONE  = "010-2222-0001";
    private static final String PARENT_PHONE   = "010-3333-0001";

    private static final String TEACHER_PASSWORD = "teacher1234";
    private static final String STUDENT_PASSWORD = "student1234";
    private static final String PARENT_PASSWORD  = "parent1234";

    private static final String SCHOOL1_CODE = "HANBIT";
    private static final String SCHOOL1_NAME = "한빛중학교";
    private static final String SCHOOL2_CODE = "SAEBYEOL";
    private static final String SCHOOL2_NAME = "새별중학교";

    @Value("${dev.seed.admin.password}")
    private String adminPassword;

    @Value("${dev.seed.key:#{null}}")
    private String seedKey;

    private void validateSeedKey(String key) {
        if (seedKey != null && !seedKey.isBlank() && !seedKey.equals(key)) {
            throw new RuntimeException("Invalid seed key");
        }
    }

    private final UserRepository              userRepository;
    private final TeacherRepository           teacherRepository;
    private final StudentRepository           studentRepository;
    private final ParentRepository            parentRepository;
    private final ParentStudentRepository     parentStudentRepository;
    private final ClassRoomRepository         classRoomRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final PasswordEncoder             passwordEncoder;
    private final SubjectRepository           subjectRepository;
    private final ScoreRepository             scoreRepository;
    private final FeedbackRepository          feedbackRepository;
    private final CounselingRepository        counselingRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final ApplicationEventPublisher   eventPublisher;
    private final SchoolRepository            schoolRepository;
    private final StudentRecordRepository     studentRecordRepository;
    private final EmbeddingService             embeddingService;

    // ── ADMIN 단독 시드 (기존 호환) ───────────────────────────

    @Operation(summary = "[DEV] ADMIN 계정 생성/초기화")
    @PostMapping("/seed/admin")
    public ResponseEntity<ApiResponse<SeedResult>> seedAdmin(@RequestParam(required = false) String key) {
        validateSeedKey(key);
        School school = getOrCreateSchool("ADMIN", "시스템관리");
        User admin = upsertUser(ADMIN_EMAIL, ADMIN_PHONE, "관리자", adminPassword, Role.ADMIN, school);
        log.info("[DEV SEED] ADMIN: {}", ADMIN_EMAIL);
        return ResponseEntity.ok(ApiResponse.success(
                new SeedResult("ADMIN", ADMIN_EMAIL, adminPassword, admin.getId())));
    }

    // ── 전체 역할 시드 ────────────────────────────────────────

    @Operation(summary = "[DEV] 전체 역할별 계정 초기화",
               description = "ADMIN/TEACHER/STUDENT/PARENT 계정을 초기화하고 반·수강 관계를 구성합니다.")
    @PostMapping("/seed/all")
    public ResponseEntity<ApiResponse<SeedAllResult>> seedAll(@RequestParam(required = false) String key) {
        validateSeedKey(key);
        List<SeedResult> results = new ArrayList<>();
        School school = getOrCreateSchool(SCHOOL1_CODE, SCHOOL1_NAME);

        // 1. ADMIN
        User adminUser = upsertUser(ADMIN_EMAIL, ADMIN_PHONE, "관리자", adminPassword, Role.ADMIN, school);
        results.add(new SeedResult("ADMIN", ADMIN_EMAIL, adminPassword, adminUser.getId()));

        // 2. TEACHER
        User teacherUser = upsertUser(TEACHER_EMAIL, TEACHER_PHONE, "테스트교사", TEACHER_PASSWORD, Role.TEACHER, school);
        Teacher teacher = teacherRepository.findByUser(teacherUser)
                .orElseGet(() -> teacherRepository.save(
                        Teacher.builder().user(teacherUser).department("테스트학과").build()));
        results.add(new SeedResult("TEACHER", TEACHER_EMAIL, TEACHER_PASSWORD, teacherUser.getId()));

        // 3. STUDENT
        User studentUser = upsertUser(STUDENT_EMAIL, STUDENT_PHONE, "테스트학생", STUDENT_PASSWORD, Role.STUDENT, school);
        Student student = studentRepository.findByUser(studentUser)
                .orElseGet(() -> studentRepository.save(
                        Student.builder().user(studentUser).admissionYear(2026).build()));
        results.add(new SeedResult("STUDENT", STUDENT_EMAIL, STUDENT_PASSWORD, studentUser.getId()));

        // 4. PARENT
        User parentUser = upsertUser(PARENT_EMAIL, PARENT_PHONE, "테스트학부모", PARENT_PASSWORD, Role.PARENT, school);
        Parent parent = parentRepository.findByUser(parentUser)
                .orElseGet(() -> parentRepository.save(
                        Parent.builder().user(parentUser).build()));
        results.add(new SeedResult("PARENT", PARENT_EMAIL, PARENT_PASSWORD, parentUser.getId()));

        // 5. 반 구성 (2026년 1학년 1반)
        ClassRoom classRoom = classRoomRepository
                .findByAcademicYearAndGradeAndClassNum(2026, 1, 1)
                .orElseGet(() -> classRoomRepository.save(
                        ClassRoom.builder().academicYear(2026).grade(1).classNum(1).school(school).build()));

        // 담임 배정
        classRoom.assignHomeroom(teacher);
        classRoomRepository.save(classRoom);

        // 학생 배정 (이미 등록된 경우 스킵)
        if (!enrollmentRepository.existsByStudentAndAcademicYear(student, 2026)) {
            enrollmentRepository.save(StudentEnrollment.builder()
                    .student(student)
                    .classRoom(classRoom)
                    .academicYear(2026)
                    .studentNum(1)
                    .build());
        }

        // 학부모-학생 연결 (이미 연결된 경우 스킵)
        if (!parentStudentRepository.existsByParentAndStudent(parent, student)) {
            parentStudentRepository.save(ParentStudent.builder()
                    .parent(parent)
                    .student(student)
                    .relationship(ParentStudent.Relationship.MOTHER)
                    .build());
        }

        log.info("[DEV SEED] 전체 시드 완료: ADMIN/TEACHER/STUDENT/PARENT");
        return ResponseEntity.ok(ApiResponse.success(new SeedAllResult(results,
                "2026년 1학년 1반 구성 완료 — 담임: 테스트교사, 학생: 테스트학생, 학부모 연결 완료")));
    }

    // ── 대량 테스트 데이터 시드 ─────────────────────────────────

    @Operation(summary = "[DEV] 대량 테스트 데이터 생성",
               description = "학생 30명 + 과목 5개 + 성적/피드백/상담 데이터 생성. 부하 테스트용.")
    @PostMapping("/seed/bulk")
    public ApiResponse<Void> seedBulk(@RequestParam(required = false) String key) {
        validateSeedKey(key);
        // 1. 교사 확보 (기존 시드의 teacher 사용)
        User teacherUser = userRepository.findByEmail(TEACHER_EMAIL)
                .orElseThrow(() -> new RuntimeException("먼저 /seed/all 실행 필요"));
        Teacher teacher = teacherRepository.findByUser(teacherUser)
                .orElseThrow(() -> new RuntimeException("교사 엔티티 없음"));

        // 2. 반 확보
        ClassRoom classRoom = classRoomRepository
                .findByAcademicYearAndGradeAndClassNum(2026, 1, 1)
                .orElseThrow(() -> new RuntimeException("먼저 /seed/all 실행 필요"));

        // 3. 과목 5개 생성
        String[][] subjectData = {
                {"국어", "KOR101"}, {"수학", "MATH101"}, {"영어", "ENG101"},
                {"과학", "SCI101"}, {"사회", "SOC101"}
        };
        java.util.List<Subject> subjects = new java.util.ArrayList<>();
        for (String[] s : subjectData) {
            Subject subject = subjectRepository.findByCode(s[1])
                    .orElseGet(() -> subjectRepository.save(
                            Subject.builder().name(s[0]).code(s[1]).description(s[0] + " 과목").build()));
            subjects.add(subject);

            // 교사-과목 배정
            if (!assignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2026)) {
                assignmentRepository.save(TeacherAssignment.builder()
                        .teacher(teacher).classRoom(classRoom).subject(subject).academicYear(2026).build());
            }
        }
        log.info("[BULK SEED] 과목 {} 개 준비 완료", subjects.size());

        // 4. 학생 30명 생성
        School school = teacherUser.getSchool();
        java.util.List<Student> students = new java.util.ArrayList<>();
        String pwHash = passwordEncoder.encode("student1234");
        for (int i = 1; i <= 30; i++) {
            final int idx = i;
            String email = String.format("student%02d@sscm.dev", i);
            String phone = String.format("010-0000-%04d", i);
            // 이메일로 먼저 찾고, 없으면 전화번호로 찾아서 이메일 업데이트, 둘 다 없으면 새로 생성
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.findByPhone(phone)
                            .map(existing -> {
                                // 전화번호가 같은 기존 유저의 이메일/이름 업데이트
                                existing.resetPassword(pwHash);
                                return userRepository.save(existing);
                            })
                            .orElseGet(() -> userRepository.save(User.builder()
                                    .name("학생" + idx).email(email).phone(phone)
                                    .passwordHash(pwHash).role(Role.STUDENT)
                                    .school(school)
                                    .isActive(true).isActivated(true)
                                    .createdAt(java.time.LocalDateTime.now())
                                    .updatedAt(java.time.LocalDateTime.now())
                                    .build())));
            Student student = studentRepository.findByUser(user)
                    .orElseGet(() -> studentRepository.save(
                            Student.builder().user(user).admissionYear(2026).build()));
            students.add(student);

            // 반 배정 (student_num을 10+i로 시작하여 기존 시드와 충돌 방지)
            if (!enrollmentRepository.existsByStudentAndAcademicYear(student, 2026)) {
                enrollmentRepository.save(StudentEnrollment.builder()
                        .student(student).classRoom(classRoom).academicYear(2026).studentNum(10 + i).build());
            }
        }
        log.info("[BULK SEED] 학생 {} 명 준비 완료", students.size());

        // 5. 성적 데이터 (학생 30명 × 과목 5개 = 150건)
        java.util.Random random = new java.util.Random(42);
        int scoreCount = 0;
        for (Student student : students) {
            for (Subject subject : subjects) {
                if (scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(
                        student.getId(), subject.getId(), 2026, 1).isEmpty()) {
                    java.math.BigDecimal scoreVal = java.math.BigDecimal.valueOf(55 + random.nextInt(45));
                    Score score = scoreRepository.save(Score.builder()
                            .student(student).subject(subject).teacher(teacher)
                            .year(2026).semester(1).score(scoreVal)
                            .gradeLetter(Score.calculateGradeLetter(scoreVal))
                            .createdBy(teacherUser.getId()).updatedBy(teacherUser.getId())
                            .build());
                    // Kafka 이벤트 발행
                    eventPublisher.publishEvent(new com.sscm.analytics.event.ScoreChangedEvent("CREATED",
                            com.sscm.analytics.event.payload.ScoreEventPayload.builder()
                                    .scoreId(score.getId()).studentId(student.getId())
                                    .subjectId(subject.getId()).teacherId(teacher.getId())
                                    .year(2026).semester(1).score(scoreVal)
                                    .gradeLetter(score.getGradeLetter()).build()));
                    scoreCount++;
                }
            }
        }
        log.info("[BULK SEED] 성적 {} 건 생성", scoreCount);

        // 6. 피드백 데이터 (학생당 2건 = 60건)
        FeedbackCategory[] fbCategories = FeedbackCategory.values();
        int feedbackCount = 0;
        for (Student student : students) {
            for (int j = 0; j < 2; j++) {
                feedbackRepository.save(Feedback.builder()
                        .student(student).teacher(teacher).year(2026).semester(1)
                        .category(fbCategories[random.nextInt(fbCategories.length)])
                        .content("테스트 피드백 " + (j + 1))
                        .isVisibleToStudent(true).isVisibleToParent(false)
                        .build());
                feedbackCount++;
            }
        }
        log.info("[BULK SEED] 피드백 {} 건 생성", feedbackCount);

        // 7. 상담 데이터 (학생당 1건 = 30건)
        CounselCategory[] ccCategories = CounselCategory.values();
        int counselCount = 0;
        for (Student student : students) {
            counselingRepository.save(Counseling.builder()
                    .student(student).teacher(teacher)
                    .counselDate(java.time.LocalDate.of(2026, 3, 15 + random.nextInt(15)))
                    .category(ccCategories[random.nextInt(ccCategories.length)])
                    .content("테스트 상담 내용")
                    .nextPlan("후속 계획")
                    .build());
            counselCount++;
        }
        log.info("[BULK SEED] 상담 {} 건 생성", counselCount);

        String summary = String.format("학생 %d명, 과목 %d개, 성적 %d건, 피드백 %d건, 상담 %d건 생성 완료",
                students.size(), subjects.size(), scoreCount, feedbackCount, counselCount);
        return ApiResponse.success(summary);
    }

    // ── 대규모 트렌드 분석용 시드 ─────────────────────────────────

    @Operation(summary = "[DEV] 대규모 트렌드 분석용 데이터 생성",
               description = "학생 30명 × 3학년도 × 2학기 — 성적/피드백/상담/학생부 데이터 생성. 분석 트렌드 시각화용.")
    @PostMapping("/seed/large")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seedLarge(@RequestParam(required = false) String key) {
        validateSeedKey(key);
        // 0. 전제: /seed/all 호출 후 교사 존재해야 함
        User teacherUser = userRepository.findByEmail(TEACHER_EMAIL)
                .orElseThrow(() -> new RuntimeException("먼저 /seed/all 실행 필요"));
        Teacher teacher = teacherRepository.findByUser(teacherUser)
                .orElseThrow(() -> new RuntimeException("교사 엔티티 없음"));
        School school = teacherUser.getSchool();

        // TenantContext 설정 (JWT 없는 dev 환경이므로 수동 세팅)
        TenantContext.setSchoolId(school.getId());
        try {
            return ResponseEntity.ok(ApiResponse.success(doSeedLarge(teacherUser, teacher, school)));
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> doSeedLarge(User teacherUser, Teacher teacher, School school) {
        Random rng = new Random(12345L); // 고정 시드 → 재현 가능
        Long schoolId = school.getId();
        Long teacherId = teacher.getId();

        // ── 1. 과목 5개 ──────────────────────────────────────────
        String[][] subjectData = {
                {"국어", "KOR101"}, {"수학", "MATH101"}, {"영어", "ENG101"},
                {"과학", "SCI101"}, {"사회", "SOC101"}
        };
        List<Subject> subjects = new ArrayList<>();
        for (String[] s : subjectData) {
            subjects.add(subjectRepository.findByCode(s[1])
                    .orElseGet(() -> subjectRepository.save(
                            Subject.builder().name(s[0]).code(s[1]).description(s[0] + " 과목").build())));
        }

        // ── 2. 학년도별 반 (2024-1학년, 2025-2학년, 2026-3학년) ──
        int[] years = {2024, 2025, 2026};
        int[] grades = {1, 2, 3};
        Map<Integer, ClassRoom> classRoomByYear = new LinkedHashMap<>();
        for (int i = 0; i < years.length; i++) {
            int yr = years[i];
            int gr = grades[i];
            ClassRoom cr = classRoomRepository
                    .findByAcademicYearAndGradeAndClassNum(yr, gr, 1)
                    .orElseGet(() -> classRoomRepository.save(
                            ClassRoom.builder().academicYear(yr).grade(gr).classNum(1).school(school).build()));
            cr.assignHomeroom(teacher);
            classRoomRepository.save(cr);
            classRoomByYear.put(yr, cr);

            // 교사-과목 배정
            for (Subject subject : subjects) {
                if (!assignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                        teacher, cr, subject, yr)) {
                    assignmentRepository.save(TeacherAssignment.builder()
                            .teacher(teacher).classRoom(cr).subject(subject).academicYear(yr).build());
                }
            }
        }
        log.info("[LARGE SEED] 반 {} 개 준비 완료", classRoomByYear.size());

        // ── 3. 학생 30명 (재사용 or 생성) ────────────────────────
        String pwHash = passwordEncoder.encode("student1234");
        List<Student> students = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            final int idx = i;
            String email = String.format("student%02d@sscm.dev", i);
            String phone = String.format("010-0000-%04d", i);
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.findByPhone(phone)
                            .map(existing -> { existing.resetPassword(pwHash); return userRepository.save(existing); })
                            .orElseGet(() -> userRepository.save(User.builder()
                                    .name("학생" + idx).email(email).phone(phone)
                                    .passwordHash(pwHash).role(Role.STUDENT).school(school)
                                    .isActive(true).isActivated(true)
                                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                                    .build())));
            Student student = studentRepository.findByUser(user)
                    .orElseGet(() -> studentRepository.save(
                            Student.builder().user(user).admissionYear(2024).build()));
            students.add(student);

            // 3개 학년도 모두 등록
            for (int yi = 0; yi < years.length; yi++) {
                int yr = years[yi];
                ClassRoom cr = classRoomByYear.get(yr);
                if (!enrollmentRepository.existsByStudentAndAcademicYear(student, yr)) {
                    enrollmentRepository.save(StudentEnrollment.builder()
                            .student(student).classRoom(cr).academicYear(yr).studentNum(10 + i).build());
                }
            }
        }
        log.info("[LARGE SEED] 학생 {} 명 준비 완료", students.size());

        // ── 4. 학생별 성적 트렌드 프로파일 ───────────────────────
        // 30명을 4그룹으로 분배: 향상 / 하락 / 상위고정 / 하위고정
        // 향상: 기준 50~60에서 시작, 학기마다 +3~5
        // 하락: 기준 85~95에서 시작, 학기마다 -3~5
        // 상위고정: 85~95 범위에서 변동
        // 하위고정: 40~55 범위에서 변동
        int scoreCount = 0;
        int feedbackCount = 0;
        int counselCount = 0;
        int recordCount = 0;

        for (int si = 0; si < students.size(); si++) {
            Student student = students.get(si);
            int group = si % 4; // 0=향상, 1=하락, 2=상위, 3=하위

            // ── 성적 (5과목 × 3년 × 2학기 = 30건/학생) ──────────
            for (Subject subject : subjects) {
                int subjectOffset = rng.nextInt(10) - 5; // 과목별 편차
                int semIdx = 0;
                for (int yr : years) {
                    for (int sem = 1; sem <= 2; sem++) {
                        double base = calcBaseScore(group, semIdx, rng);
                        double raw = base + subjectOffset + rng.nextGaussian() * 10;
                        raw = Math.max(40, Math.min(100, raw));
                        BigDecimal scoreVal = BigDecimal.valueOf(Math.round(raw));

                        if (scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(
                                student.getId(), subject.getId(), yr, sem).isEmpty()) {
                            Score score = scoreRepository.save(Score.builder()
                                    .student(student).subject(subject).teacher(teacher)
                                    .year(yr).semester(sem).score(scoreVal)
                                    .gradeLetter(Score.calculateGradeLetter(scoreVal))
                                    .createdBy(teacherUser.getId()).updatedBy(teacherUser.getId())
                                    .build());
                            eventPublisher.publishEvent(new ScoreChangedEvent("CREATED",
                                    ScoreEventPayload.builder()
                                            .scoreId(score.getId()).studentId(student.getId())
                                            .subjectId(subject.getId()).teacherId(teacherId)
                                            .schoolId(schoolId)
                                            .year(yr).semester(sem).score(scoreVal)
                                            .gradeLetter(score.getGradeLetter()).build()));
                            scoreCount++;
                        }
                        semIdx++;
                    }
                }
            }

            // ── 피드백 (학기당 2~4건 = 12~24건/학생) ─────────────
            for (int yr : years) {
                for (int sem = 1; sem <= 2; sem++) {
                    int fbCount = 2 + rng.nextInt(3); // 2~4
                    for (int f = 0; f < fbCount; f++) {
                        FeedbackCategory cat = FEEDBACK_CATEGORIES[rng.nextInt(FEEDBACK_CATEGORIES.length)];
                        String content = pickFeedbackContent(cat, rng);
                        boolean visStudent = rng.nextBoolean();
                        boolean visParent = rng.nextInt(3) == 0; // 1/3 확률

                        Feedback fb = feedbackRepository.save(Feedback.builder()
                                .student(student).teacher(teacher).year(yr).semester(sem)
                                .category(cat).content(content)
                                .isVisibleToStudent(visStudent).isVisibleToParent(visParent)
                                .build());
                        eventPublisher.publishEvent(new FeedbackChangedEvent("CREATED",
                                FeedbackEventPayload.builder()
                                        .feedbackId(fb.getId()).studentId(student.getId())
                                        .teacherId(teacherId).schoolId(schoolId)
                                        .year(yr).semester(sem).category(cat.name()).build()));
                        feedbackCount++;
                    }
                }
            }

            // ── 상담 (학기당 1~2건 = 6~12건/학생) ────────────────
            for (int yr : years) {
                for (int sem = 1; sem <= 2; sem++) {
                    int cCount = 1 + rng.nextInt(2); // 1~2
                    for (int c = 0; c < cCount; c++) {
                        CounselCategory cat = COUNSEL_CATEGORIES[rng.nextInt(COUNSEL_CATEGORIES.length)];
                        String[] pair = pickCounselingContent(cat, rng);
                        int month = sem == 1 ? 3 + rng.nextInt(4) : 9 + rng.nextInt(3); // 3~6 or 9~11
                        int day = 1 + rng.nextInt(28);
                        LocalDate counselDate = LocalDate.of(yr, month, day);

                        // 최근 상담(2026)에만 nextPlan/nextCounselDate 설정
                        String nextPlan = (yr == 2026) ? pair[1] : null;
                        LocalDate nextCounselDate = (yr == 2026)
                                ? counselDate.plusWeeks(2 + rng.nextInt(3)) : null;

                        Counseling co = counselingRepository.save(Counseling.builder()
                                .student(student).teacher(teacher)
                                .counselDate(counselDate).category(cat)
                                .content(pair[0]).nextPlan(nextPlan).nextCounselDate(nextCounselDate)
                                .build());
                        eventPublisher.publishEvent(new CounselingChangedEvent("CREATED",
                                CounselingEventPayload.builder()
                                        .counselingId(co.getId()).studentId(student.getId())
                                        .teacherId(teacherId).schoolId(schoolId)
                                        .counselDate(counselDate).category(cat.name()).build()));
                        counselCount++;
                    }
                }
            }

            // ── 학생부 기록 (학기당 다양한 카테고리) ─────────────
            for (int yr : years) {
                for (int sem = 1; sem <= 2; sem++) {
                    // 출결 기록
                    recordCount += createRecord(student, yr, sem, RecordCategory.ATTENDANCE,
                            Map.of("description", pickAttendanceContent(rng),
                                    "absenceDays", rng.nextInt(5),
                                    "lateDays", rng.nextInt(8)),
                            schoolId, teacherUser.getId());

                    // 수상 기록 (50% 확률)
                    if (rng.nextBoolean()) {
                        recordCount += createRecord(student, yr, sem, RecordCategory.AWARD,
                                Map.of("description", pickAwardContent(rng),
                                        "awardDate", LocalDate.of(yr, sem == 1 ? 6 : 12, 1 + rng.nextInt(20)).toString()),
                                schoolId, teacherUser.getId());
                    }

                    // 종합의견 (매 학기)
                    recordCount += createRecord(student, yr, sem, RecordCategory.GENERAL_OPINION,
                            Map.of("description", pickGeneralOpinionContent(group, rng)),
                            schoolId, teacherUser.getId());

                    // 세부능력 특기사항 (2~3 과목)
                    int specCount = 2 + rng.nextInt(2);
                    for (int sp = 0; sp < specCount && sp < subjects.size(); sp++) {
                        recordCount += createRecord(student, yr, sem, RecordCategory.SPECIAL_NOTE,
                                Map.of("description", pickSpecialNoteContent(subjects.get(sp).getName(), rng),
                                        "subjectName", subjects.get(sp).getName()),
                                schoolId, teacherUser.getId());
                    }
                }
            }
        }

        log.info("[LARGE SEED] 완료 — 성적 {}건, 피드백 {}건, 상담 {}건, 학생부 {}건",
                scoreCount, feedbackCount, counselCount, recordCount);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("students", students.size());
        summary.put("subjects", subjects.size());
        summary.put("years", "2024, 2025, 2026 (1→3학년 진급)");
        summary.put("scores", scoreCount);
        summary.put("feedbacks", feedbackCount);
        summary.put("counselings", counselCount);
        summary.put("studentRecords", recordCount);
        summary.put("message", String.format(
                "학생 %d명 × 3학년도 × 2학기 대규모 트렌드 데이터 생성 완료. Kafka 이벤트 발행됨.",
                students.size()));
        return summary;
    }

    // ── 성적 트렌드 기준점 계산 ──────────────────────────────────

    /** 학생 그룹별 학기 인덱스(0~5)에 따른 기준 점수 */
    private double calcBaseScore(int group, int semIdx, Random rng) {
        return switch (group) {
            case 0 -> 52 + semIdx * 4.5 + rng.nextInt(5);  // 향상: 52→79 부근
            case 1 -> 92 - semIdx * 4.0 - rng.nextInt(5);  // 하락: 92→68 부근
            case 2 -> 88 + rng.nextInt(8) - 3;              // 상위 고정: 85~95
            case 3 -> 45 + rng.nextInt(10);                  // 하위 고정: 45~55
            default -> 70;
        };
    }

    // ── 피드백 콘텐츠 풀 ─────────────────────────────────────────

    private static final FeedbackCategory[] FEEDBACK_CATEGORIES = FeedbackCategory.values();
    private static final CounselCategory[] COUNSEL_CATEGORIES = CounselCategory.values();

    private static final Map<FeedbackCategory, String[]> FEEDBACK_POOL = Map.of(
            FeedbackCategory.ACADEMIC, new String[]{
                    "수학 문제 풀이 능력이 향상되고 있습니다",
                    "영어 독해에 더 집중이 필요합니다",
                    "국어 작문 실력이 눈에 띄게 좋아졌습니다",
                    "과학 실험 보고서 작성이 꼼꼼합니다",
                    "사회 과목에서 비판적 사고가 돋보입니다",
                    "수업 중 발표를 더 적극적으로 하면 좋겠습니다",
                    "과제 제출이 꾸준하고 성실합니다",
                    "시험 준비를 더 체계적으로 할 필요가 있습니다"
            },
            FeedbackCategory.BEHAVIOR, new String[]{
                    "수업 태도가 매우 모범적입니다",
                    "친구들과의 협동심이 돋보입니다",
                    "교실 정리정돈에 솔선수범합니다",
                    "규칙을 잘 준수하며 생활합니다",
                    "다른 학생들에게 좋은 영향을 줍니다",
                    "때때로 수업 중 떠드는 모습이 보입니다",
                    "봉사 활동에 적극적으로 참여합니다",
                    "학교 행사에 열정적으로 참여합니다"
            },
            FeedbackCategory.ATTENDANCE, new String[]{
                    "지각이 잦아 주의가 필요합니다",
                    "개근상 후보입니다",
                    "출석이 양호합니다",
                    "최근 결석이 늘어 관심이 필요합니다",
                    "병결 후 보충 학습을 성실히 수행했습니다",
                    "조퇴가 잦아 건강 상태 확인이 필요합니다",
                    "출결 관리가 완벽합니다",
                    "지각 횟수가 줄어들고 있습니다"
            },
            FeedbackCategory.ATTITUDE, new String[]{
                    "자습 시간 활용이 우수합니다",
                    "적극적으로 질문합니다",
                    "학습에 대한 의지가 강합니다",
                    "노력하는 모습이 보기 좋습니다",
                    "꾸준히 성장하고 있습니다",
                    "자기주도적 학습 습관이 잘 형성되어 있습니다",
                    "수업에 집중하는 시간이 늘었습니다",
                    "도전적인 과제에도 포기하지 않는 자세가 좋습니다"
            },
            FeedbackCategory.GENERAL, new String[]{
                    "리더십이 돋보이는 학생입니다",
                    "진로 탐색에 관심이 많습니다",
                    "전반적으로 밝고 긍정적인 학생입니다",
                    "주변 친구들에게 인기가 많습니다",
                    "창의적인 아이디어를 많이 제시합니다",
                    "독서량이 많아 배경지식이 풍부합니다",
                    "예체능 분야에서 특별한 재능을 보입니다",
                    "다양한 교내 활동에 참여하고 있습니다"
            }
    );

    private String pickFeedbackContent(FeedbackCategory cat, Random rng) {
        String[] pool = FEEDBACK_POOL.get(cat);
        return pool[rng.nextInt(pool.length)];
    }

    // ── 상담 콘텐츠 풀 (content, nextPlan) ───────────────────────

    private static final Map<CounselCategory, String[][]> COUNSEL_POOL = Map.of(
            CounselCategory.ACADEMIC, new String[][]{
                    {"중간고사 성적 하락에 대한 상담. 수학 보충 수업 권유", "수학 보충 수업 참여 확인"},
                    {"영어 성적 향상을 위한 학습 계획 수립", "영어 학습 계획 이행 여부 확인"},
                    {"전체 성적 분석 및 취약 과목 파악", "취약 과목 보충 학습 계획 점검"},
                    {"기말고사 대비 학습 전략 상담", "시험 후 결과 분석 면담"}
            },
            CounselCategory.CAREER, new String[][]{
                    {"의사가 되고 싶다고 함. 이과 진학 안내", "이과 관련 체험활동 안내"},
                    {"IT 분야 관심. 코딩 교육 프로그램 안내", "코딩 교육 참여 확인"},
                    {"진로 희망이 불분명. 적성 검사 권유", "적성 검사 결과 상담"},
                    {"교사를 희망. 교육봉사 활동 권유", "교육봉사 활동 참여 확인"}
            },
            CounselCategory.BEHAVIOR, new String[][]{
                    {"친구 관계에서 갈등이 있음. 중재 필요", "갈등 해소 여부 확인"},
                    {"수업 중 집중도 저하. 원인 파악 상담", "집중도 개선 확인"},
                    {"학교폭력 예방 상담. 올바른 교우관계 지도", "교우 관계 변화 관찰"},
                    {"휴대폰 사용 관련 주의. 학습 방해 요소 제거 유도", "휴대폰 사용 패턴 확인"}
            },
            CounselCategory.PERSONAL, new String[][]{
                    {"가정 환경 변화로 힘들어하고 있음. 지속 관찰 필요", "심리 상태 확인 면담"},
                    {"자존감이 낮아 자신감 회복 상담", "자존감 향상 프로그램 참여 확인"},
                    {"스트레스 관리 방법 안내", "스트레스 수준 확인"},
                    {"교우 관계 고민 상담. 경청 및 조언", "교우 관계 개선 여부 확인"}
            },
            CounselCategory.OTHER, new String[][]{
                    {"학급 임원 활동에 대한 격려", "임원 활동 수행 점검"},
                    {"특기적성 활동 참여 상담", "활동 참여 현황 확인"},
                    {"학교 적응 전반에 대한 상담", "적응 상태 재확인"},
                    {"봉사 활동 참여 권유 및 안내", "봉사 활동 참여 확인"}
            }
    );

    private String[] pickCounselingContent(CounselCategory cat, Random rng) {
        String[][] pool = COUNSEL_POOL.get(cat);
        return pool[rng.nextInt(pool.length)];
    }

    // ── 학생부 콘텐츠 풀 ─────────────────────────────────────────

    private static final String[] ATTENDANCE_POOL = {
            "출결 상태 양호", "무단 지각 2회", "병결 3일 (감기)",
            "개근", "무단 결석 1일, 학부모 상담 완료",
            "체험학습 2일", "병결 후 보충학습 완료", "지각 개선 중"
    };
    private static final String[] AWARD_POOL = {
            "교내 과학 경시대회 은상", "학년 모범상", "영어 말하기 대회 장려상",
            "수학 올림피아드 동상", "독서 감상문 대회 최우수상", "봉사 활동 우수상",
            "체육대회 MVP", "교내 UCC 공모전 금상", "글짓기 대회 우수상"
    };
    private static final String[] GENERAL_OPINION_GOOD = {
            "성실하고 책임감 있는 학생입니다", "학업에 대한 열정이 돋보이며 꾸준히 성장하고 있습니다",
            "밝고 긍정적인 성격으로 학급 분위기에 기여합니다", "자기주도적 학습 능력이 우수합니다",
            "리더십과 협동심이 뛰어나 또래 관계가 원만합니다"
    };
    private static final String[] GENERAL_OPINION_NEEDS = {
            "학습 습관 형성에 더 노력이 필요한 학생입니다", "집중력 향상을 위한 지속적인 관심이 필요합니다",
            "기초 학력 보충이 필요하며 꾸준한 지도가 요구됩니다", "자신감을 키워주면 큰 성장이 기대됩니다",
            "출결 관리에 더 신경 쓸 필요가 있습니다"
    };
    private static final String[] SPECIAL_NOTE_POOL = {
            "%s 과목에서 깊이 있는 탐구 능력을 보여줌",
            "%s 수업에서 적극적인 참여와 질문으로 이해도가 높음",
            "%s 과목의 심화 문제에 도전하는 자세가 돋보임",
            "%s 수업 중 발표력이 우수하며 논리적으로 사고함",
            "%s 과목 관련 자기주도 프로젝트를 성실히 수행함",
            "%s 실험/실습에서 정확한 관찰력과 기록 능력을 보임"
    };

    private String pickAttendanceContent(Random rng) {
        return ATTENDANCE_POOL[rng.nextInt(ATTENDANCE_POOL.length)];
    }

    private String pickAwardContent(Random rng) {
        return AWARD_POOL[rng.nextInt(AWARD_POOL.length)];
    }

    private String pickGeneralOpinionContent(int group, Random rng) {
        // 상위/향상 그룹은 긍정적 의견, 하위 그룹은 개선 필요 의견
        if (group == 2 || group == 0) {
            return GENERAL_OPINION_GOOD[rng.nextInt(GENERAL_OPINION_GOOD.length)];
        } else if (group == 3) {
            return GENERAL_OPINION_NEEDS[rng.nextInt(GENERAL_OPINION_NEEDS.length)];
        } else {
            // 하락 그룹: 섞어서
            return rng.nextBoolean()
                    ? GENERAL_OPINION_GOOD[rng.nextInt(GENERAL_OPINION_GOOD.length)]
                    : GENERAL_OPINION_NEEDS[rng.nextInt(GENERAL_OPINION_NEEDS.length)];
        }
    }

    private String pickSpecialNoteContent(String subjectName, Random rng) {
        String template = SPECIAL_NOTE_POOL[rng.nextInt(SPECIAL_NOTE_POOL.length)];
        return String.format(template, subjectName);
    }

    /** StudentRecord 생성 + Kafka 이벤트 발행. 생성 건수(0 or 1) 반환. */
    private int createRecord(Student student, int year, int semester,
                             RecordCategory category, Map<String, Object> content,
                             Long schoolId, Long createdBy) {
        StudentRecord record = studentRecordRepository.save(StudentRecord.builder()
                .student(student).year(year).semester(semester)
                .category(category).content(content)
                .isVisibleToStudent(true).isVisibleToParent(false)
                .createdBy(createdBy).updatedBy(createdBy)
                .build());
        eventPublisher.publishEvent(new RecordChangedEvent("CREATED",
                RecordEventPayload.builder()
                        .recordId(record.getId()).studentId(student.getId())
                        .schoolId(schoolId).year(year).semester(semester)
                        .category(category.name()).build()));
        return 1;
    }

    // ── 새별중학교 시드 (멀티테넌시 데모용) ─────────────────────

    @Operation(summary = "[DEV] 새별중학교 시드 데이터 생성",
               description = "새별중학교(School 2) 교사/학생/반 구성. 멀티테넌시 격리 데모용.")
    @PostMapping("/seed/school2")
    public ResponseEntity<ApiResponse<SeedAllResult>> seedSchool2(@RequestParam(required = false) String key) {
        validateSeedKey(key);
        School school = getOrCreateSchool(SCHOOL2_CODE, SCHOOL2_NAME);
        List<SeedResult> results = new ArrayList<>();

        // 1. TEACHER for school 2
        User teacherUser2 = upsertUser("teacher2@sscm.dev", "010-1111-0002", "새별교사", TEACHER_PASSWORD, Role.TEACHER, school);
        Teacher teacher2 = teacherRepository.findByUser(teacherUser2)
                .orElseGet(() -> teacherRepository.save(
                        Teacher.builder().user(teacherUser2).department("새별학과").build()));
        results.add(new SeedResult("TEACHER", "teacher2@sscm.dev", TEACHER_PASSWORD, teacherUser2.getId()));

        // 2. STUDENT for school 2
        User studentUser2 = upsertUser("student-sb@sscm.dev", "010-2222-0002", "새별학생", STUDENT_PASSWORD, Role.STUDENT, school);
        Student student2 = studentRepository.findByUser(studentUser2)
                .orElseGet(() -> studentRepository.save(
                        Student.builder().user(studentUser2).admissionYear(2026).build()));
        results.add(new SeedResult("STUDENT", "student-sb@sscm.dev", STUDENT_PASSWORD, studentUser2.getId()));

        // 3. 반 구성 (2026년 1학년 2반 — school1의 1반과 충돌 방지)
        ClassRoom classRoom2 = classRoomRepository
                .findByAcademicYearAndGradeAndClassNum(2026, 1, 2)
                .orElseGet(() -> classRoomRepository.save(
                        ClassRoom.builder().academicYear(2026).grade(1).classNum(2).school(school).build()));

        // 담임 배정
        classRoom2.assignHomeroom(teacher2);
        classRoomRepository.save(classRoom2);

        // 학생 배정
        if (!enrollmentRepository.existsByStudentAndAcademicYear(student2, 2026)) {
            enrollmentRepository.save(StudentEnrollment.builder()
                    .student(student2).classRoom(classRoom2).academicYear(2026).studentNum(1).build());
        }

        log.info("[DEV SEED] 새별중학교 시드 완료");
        return ResponseEntity.ok(ApiResponse.success(new SeedAllResult(results,
                "새별중학교 — 교사: 새별교사, 학생: 새별학생, 1학년 2반 구성 완료")));
    }

    // ── 피드백/상담 임베딩 시드 ─────────────────────────────────

    @Operation(summary = "[DEV] 피드백/상담 임베딩 생성",
               description = "기존 피드백/상담 데이터의 텍스트를 임베딩하여 벡터 DB에 저장합니다.")
    @PostMapping("/seed/embeddings")
    public ApiResponse<Map<String, Object>> seedEmbeddings(@RequestParam(required = false) String key) {
        validateSeedKey(key);

        // Get teacher for school context
        User teacherUser = userRepository.findByEmail(TEACHER_EMAIL)
            .orElseThrow(() -> new RuntimeException("먼저 /seed/all 실행 필요"));
        Long schoolId = teacherUser.getSchool().getId();

        // Embed all feedbacks that don't have embeddings yet
        List<Feedback> feedbacks = feedbackRepository.findAll();
        int feedbackCount = 0;
        int feedbackErrors = 0;
        for (Feedback fb : feedbacks) {
            try {
                embeddingService.embedFeedback(
                    fb.getId(), fb.getStudent().getId(), schoolId,
                    fb.getYear(), fb.getSemester(),
                    fb.getCategory().name(), fb.getContent());
                feedbackCount++;
            } catch (Exception e) {
                feedbackErrors++;
                log.error("피드백 임베딩 실패: feedbackId={}, error={}", fb.getId(), e.getMessage());
            }
        }

        // Embed all counselings
        List<Counseling> counselings = counselingRepository.findAll();
        int counselingCount = 0;
        int counselingErrors = 0;
        for (Counseling cs : counselings) {
            try {
                embeddingService.embedCounseling(
                    cs.getId(), cs.getStudent().getId(), schoolId,
                    // counseling doesn't have year/semester directly, use counsel date
                    cs.getCounselDate().getYear(),
                    cs.getCounselDate().getMonthValue() <= 6 ? 1 : 2,
                    cs.getCategory().name(), cs.getContent());
                counselingCount++;
            } catch (Exception e) {
                counselingErrors++;
                log.error("상담 임베딩 실패: counselingId={}, error={}", cs.getId(), e.getMessage());
            }
        }

        log.info("[SEED] 임베딩 생성 완료: 피드백 {}건(에러 {}건), 상담 {}건(에러 {}건)",
            feedbackCount, feedbackErrors, counselingCount, counselingErrors);

        return ApiResponse.success(Map.of(
            "feedbackEmbeddings", feedbackCount,
            "feedbackErrors", feedbackErrors,
            "counselingEmbeddings", counselingCount,
            "counselingErrors", counselingErrors
        ));
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────

    private School getOrCreateSchool(String code, String name) {
        return schoolRepository.findByCode(code)
                .orElseGet(() -> schoolRepository.save(
                        School.builder().name(name).code(code).build()));
    }

    private User upsertUser(String email, String phone, String name, String password, Role role, School school) {
        String pwHash = passwordEncoder.encode(password);

        return userRepository.findByEmail(email).map(user -> {
            user.resetPassword(pwHash);
            log.info("[DEV SEED] 기존 계정 비밀번호 초기화: {}", email);
            return userRepository.save(user);
        }).orElseGet(() -> {
            // 전화번호 중복 시 기존 계정 삭제 후 재생성
            userRepository.findByPhone(phone).ifPresent(dup -> {
                log.info("[DEV SEED] 전화번호 중복 계정 제거: phone={}", phone);
                userRepository.delete(dup);
            });
            User user = User.builder()
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .passwordHash(pwHash)
                    .role(role)
                    .school(school)
                    .isActive(true)
                    .isActivated(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            log.info("[DEV SEED] 신규 계정 생성: {}", email);
            return userRepository.save(user);
        });
    }

    // ── 응답 DTO ──────────────────────────────────────────────

    public record SeedResult(String role, String email, String password, Long userId) {}

    public record SeedAllResult(List<SeedResult> accounts, String structure) {}
}
