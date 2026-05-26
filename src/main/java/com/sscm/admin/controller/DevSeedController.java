package com.sscm.admin.controller;

import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.StudentEnrollment;
import com.sscm.common.entity.TeacherAssignment;
import com.sscm.common.repository.ClassRoomRepository;
import com.sscm.common.repository.StudentEnrollmentRepository;
import com.sscm.common.repository.TeacherAssignmentRepository;
import com.sscm.common.response.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
@Tag(name = "Dev", description = "개발 환경 전용 (프로덕션 비활성화)")
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@Profile("dev")
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

    @Value("${dev.seed.admin.password}")
    private String adminPassword;

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

    // ── ADMIN 단독 시드 (기존 호환) ───────────────────────────

    @Operation(summary = "[DEV] ADMIN 계정 생성/초기화")
    @PostMapping("/seed/admin")
    public ResponseEntity<ApiResponse<SeedResult>> seedAdmin() {
        User admin = upsertUser(ADMIN_EMAIL, ADMIN_PHONE, "관리자", adminPassword, Role.ADMIN);
        log.info("[DEV SEED] ADMIN: {}", ADMIN_EMAIL);
        return ResponseEntity.ok(ApiResponse.success(
                new SeedResult("ADMIN", ADMIN_EMAIL, adminPassword, admin.getId())));
    }

    // ── 전체 역할 시드 ────────────────────────────────────────

    @Operation(summary = "[DEV] 전체 역할별 계정 초기화",
               description = "ADMIN/TEACHER/STUDENT/PARENT 계정을 초기화하고 반·수강 관계를 구성합니다.")
    @PostMapping("/seed/all")
    public ResponseEntity<ApiResponse<SeedAllResult>> seedAll() {
        List<SeedResult> results = new ArrayList<>();

        // 1. ADMIN
        User adminUser = upsertUser(ADMIN_EMAIL, ADMIN_PHONE, "관리자", adminPassword, Role.ADMIN);
        results.add(new SeedResult("ADMIN", ADMIN_EMAIL, adminPassword, adminUser.getId()));

        // 2. TEACHER
        User teacherUser = upsertUser(TEACHER_EMAIL, TEACHER_PHONE, "테스트교사", TEACHER_PASSWORD, Role.TEACHER);
        Teacher teacher = teacherRepository.findByUser(teacherUser)
                .orElseGet(() -> teacherRepository.save(
                        Teacher.builder().user(teacherUser).department("테스트학과").build()));
        results.add(new SeedResult("TEACHER", TEACHER_EMAIL, TEACHER_PASSWORD, teacherUser.getId()));

        // 3. STUDENT
        User studentUser = upsertUser(STUDENT_EMAIL, STUDENT_PHONE, "테스트학생", STUDENT_PASSWORD, Role.STUDENT);
        Student student = studentRepository.findByUser(studentUser)
                .orElseGet(() -> studentRepository.save(
                        Student.builder().user(studentUser).admissionYear(2026).build()));
        results.add(new SeedResult("STUDENT", STUDENT_EMAIL, STUDENT_PASSWORD, studentUser.getId()));

        // 4. PARENT
        User parentUser = upsertUser(PARENT_EMAIL, PARENT_PHONE, "테스트학부모", PARENT_PASSWORD, Role.PARENT);
        Parent parent = parentRepository.findByUser(parentUser)
                .orElseGet(() -> parentRepository.save(
                        Parent.builder().user(parentUser).build()));
        results.add(new SeedResult("PARENT", PARENT_EMAIL, PARENT_PASSWORD, parentUser.getId()));

        // 5. 반 구성 (2026년 1학년 1반)
        ClassRoom classRoom = classRoomRepository
                .findByAcademicYearAndGradeAndClassNum(2026, 1, 1)
                .orElseGet(() -> classRoomRepository.save(
                        ClassRoom.builder().academicYear(2026).grade(1).classNum(1).build()));

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
    public ApiResponse<Void> seedBulk() {
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

    // ── 내부 헬퍼 ─────────────────────────────────────────────

    private User upsertUser(String email, String phone, String name, String password, Role role) {
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
