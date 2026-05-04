package com.sscm.admin.controller;

import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.StudentEnrollment;
import com.sscm.common.repository.ClassRoomRepository;
import com.sscm.common.repository.StudentEnrollmentRepository;
import com.sscm.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
