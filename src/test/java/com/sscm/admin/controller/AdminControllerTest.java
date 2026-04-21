package com.sscm.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.admin.dto.*;
import com.sscm.admin.service.AdminService;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.auth.entity.ParentStudent;
import com.sscm.common.config.SecurityConfig;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@DisplayName("AdminController 통합 테스트")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    // ─── 인증 헬퍼 ────────────────────────────────────────────

    private static RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor student() {
        return authentication(new UsernamePasswordAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    // ─── 샘플 DTO ────────────────────────────────────────────

    private TeacherSummary sampleTeacher() {
        return TeacherSummary.builder()
                .id(1L).name("이교사").phone("010-1111-2222")
                .email(null).department("수학").isActive(true).isActivated(false)
                .build();
    }

    private StudentSummary sampleStudent() {
        return StudentSummary.builder()
                .id(1L).name("이학생").phone("010-3333-4444")
                .admissionYear(2025).isActivated(false).currentEnrollment(null)
                .build();
    }

    private ParentSummary sampleParent() {
        return ParentSummary.builder()
                .id(1L).name("이학부모").phone("010-5555-6666")
                .isActivated(false).children(List.of())
                .build();
    }

    private ClassSummary sampleClass() {
        return ClassSummary.builder()
                .id(1L).academicYear(2026).grade(1).classNum(1)
                .homeroomTeacher(null).studentCount(0)
                .build();
    }

    private AssignmentSummary sampleAssignment() {
        return AssignmentSummary.builder()
                .id(1L)
                .teacher(AssignmentSummary.TeacherInfo.builder().id(1L).name("이교사").department("수학").build())
                .classInfo(AssignmentSummary.ClassInfo.builder().id(1L).grade(1).classNum(1).build())
                .subject(AssignmentSummary.SubjectInfo.builder().id(1L).name("수학").code("MATH").build())
                .academicYear(2026)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 교사 관리
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/admin/teachers — 교사 목록 조회")
    class GetTeachers {

        @Test
        @DisplayName("ADMIN 권한으로 교사 목록 조회 → 200")
        void adminCanGetTeachers() throws Exception {
            Page<TeacherSummary> page = new PageImpl<>(
                    List.of(sampleTeacher()), PageRequest.of(0, 20), 1);
            given(adminService.getTeachers(any())).willReturn(page);

            mockMvc.perform(get("/api/v1/admin/teachers").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.content[0].name").value("이교사"));
        }

        @Test
        @DisplayName("TEACHER 권한으로 조회 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/teachers").with(teacher()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/teachers"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/teachers — 교사 등록")
    class RegisterTeacher {

        @Test
        @DisplayName("ADMIN 권한으로 교사 등록 성공 → 201")
        void adminCanRegister() throws Exception {
            given(adminService.registerTeacher(any())).willReturn(sampleTeacher());

            mockMvc.perform(post("/api/v1/admin/teachers")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이교사", "phone", "010-1111-2222", "department", "수학"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("이교사"))
                    .andExpect(jsonPath("$.data.department").value("수학"));
        }

        @Test
        @DisplayName("전화번호 중복 시 → 409")
        void phoneDuplicate() throws Exception {
            given(adminService.registerTeacher(any()))
                    .willThrow(new BusinessException(ErrorCode.PHONE_DUPLICATE));

            mockMvc.perform(post("/api/v1/admin/teachers")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이교사", "phone", "010-1111-2222"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("필수 필드 누락(name) → 400")
        void missingName() throws Exception {
            mockMvc.perform(post("/api/v1/admin/teachers")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("phone", "010-1111-2222"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("STUDENT 권한으로 등록 시도 → 403")
        void studentForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/teachers")
                            .with(student())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이교사", "phone", "010-1111-2222"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/teachers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이교사", "phone", "010-1111-2222"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 학생 관리
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/admin/students — 학생 목록 조회")
    class GetStudents {

        @Test
        @DisplayName("ADMIN 권한으로 학생 목록 조회 → 200")
        void adminCanGetStudents() throws Exception {
            Page<StudentSummary> page = new PageImpl<>(
                    List.of(sampleStudent()), PageRequest.of(0, 20), 1);
            given(adminService.getStudents(any())).willReturn(page);

            mockMvc.perform(get("/api/v1/admin/students").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].name").value("이학생"));
        }

        @Test
        @DisplayName("TEACHER 권한으로 조회 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/students").with(teacher()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/students"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/students — 학생 등록")
    class RegisterStudent {

        @Test
        @DisplayName("ADMIN 권한으로 학생 등록 성공 → 201")
        void adminCanRegister() throws Exception {
            given(adminService.registerStudent(any())).willReturn(sampleStudent());

            mockMvc.perform(post("/api/v1/admin/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학생", "phone", "010-3333-4444", "admissionYear", 2025))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("이학생"))
                    .andExpect(jsonPath("$.data.admissionYear").value(2025));
        }

        @Test
        @DisplayName("전화번호 중복 시 → 409")
        void phoneDuplicate() throws Exception {
            given(adminService.registerStudent(any()))
                    .willThrow(new BusinessException(ErrorCode.PHONE_DUPLICATE));

            mockMvc.perform(post("/api/v1/admin/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학생", "phone", "010-3333-4444", "admissionYear", 2025))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("필수 필드 누락(admissionYear) → 400")
        void missingAdmissionYear() throws Exception {
            mockMvc.perform(post("/api/v1/admin/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학생", "phone", "010-3333-4444"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TEACHER 권한으로 등록 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/students")
                            .with(teacher())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학생", "phone", "010-3333-4444", "admissionYear", 2025))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/students")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학생", "phone", "010-3333-4444", "admissionYear", 2025))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/students/{studentId}/parents — 학생-학부모 연결")
    class LinkParentChild {

        @Test
        @DisplayName("ADMIN 권한으로 연결 성공 → 200")
        void adminCanLink() throws Exception {
            doNothing().when(adminService).linkParentChild(anyLong(), any());

            mockMvc.perform(post("/api/v1/admin/students/1/parents")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "parentId", 1, "relationship", "MOTHER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void studentNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND))
                    .when(adminService).linkParentChild(eq(999L), any());

            mockMvc.perform(post("/api/v1/admin/students/999/parents")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "parentId", 1, "relationship", "MOTHER"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("존재하지 않는 학부모 → 404")
        void parentNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.PARENT_NOT_FOUND))
                    .when(adminService).linkParentChild(eq(1L), any());

            mockMvc.perform(post("/api/v1/admin/students/1/parents")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "parentId", 999, "relationship", "FATHER"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("이미 연결된 관계 → 409")
        void duplicateLink() throws Exception {
            doThrow(new BusinessException(ErrorCode.PARENT_CHILD_DUPLICATE))
                    .when(adminService).linkParentChild(eq(1L), any());

            mockMvc.perform(post("/api/v1/admin/students/1/parents")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "parentId", 1, "relationship", "MOTHER"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("STUDENT 권한으로 연결 시도 → 403")
        void studentForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/students/1/parents")
                            .with(student())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "parentId", 1, "relationship", "MOTHER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/students/1/parents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "parentId", 1, "relationship", "MOTHER"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 학부모 관리
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/admin/parents — 학부모 목록 조회")
    class GetParents {

        @Test
        @DisplayName("ADMIN 권한으로 학부모 목록 조회 → 200")
        void adminCanGetParents() throws Exception {
            Page<ParentSummary> page = new PageImpl<>(
                    List.of(sampleParent()), PageRequest.of(0, 20), 1);
            given(adminService.getParents(any())).willReturn(page);

            mockMvc.perform(get("/api/v1/admin/parents").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].name").value("이학부모"));
        }

        @Test
        @DisplayName("TEACHER 권한으로 조회 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/parents").with(teacher()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/parents"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/parents — 학부모 등록")
    class RegisterParent {

        @Test
        @DisplayName("ADMIN 권한으로 학부모 등록 성공 → 201")
        void adminCanRegister() throws Exception {
            given(adminService.registerParent(any())).willReturn(sampleParent());

            mockMvc.perform(post("/api/v1/admin/parents")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학부모", "phone", "010-5555-6666"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("이학부모"));
        }

        @Test
        @DisplayName("전화번호 중복 시 → 409")
        void phoneDuplicate() throws Exception {
            given(adminService.registerParent(any()))
                    .willThrow(new BusinessException(ErrorCode.PHONE_DUPLICATE));

            mockMvc.perform(post("/api/v1/admin/parents")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학부모", "phone", "010-5555-6666"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("TEACHER 권한으로 등록 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/parents")
                            .with(teacher())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학부모", "phone", "010-5555-6666"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/parents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "이학부모", "phone", "010-5555-6666"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 반 관리
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/admin/classes — 반 목록 조회")
    class GetClasses {

        @Test
        @DisplayName("ADMIN 권한으로 반 목록 조회 → 200")
        void adminCanGetClasses() throws Exception {
            given(adminService.getClasses(2026)).willReturn(List.of(sampleClass()));

            mockMvc.perform(get("/api/v1/admin/classes")
                            .with(admin())
                            .param("academicYear", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].grade").value(1))
                    .andExpect(jsonPath("$.data[0].classNum").value(1));
        }

        @Test
        @DisplayName("TEACHER 권한으로 조회 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/classes")
                            .with(teacher())
                            .param("academicYear", "2026"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/classes").param("academicYear", "2026"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/classes — 반 생성")
    class CreateClass {

        @Test
        @DisplayName("ADMIN 권한으로 반 생성 성공 → 201")
        void adminCanCreate() throws Exception {
            given(adminService.createClass(any())).willReturn(sampleClass());

            mockMvc.perform(post("/api/v1/admin/classes")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "academicYear", 2026, "grade", 1, "classNum", 1))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.academicYear").value(2026))
                    .andExpect(jsonPath("$.data.grade").value(1));
        }

        @Test
        @DisplayName("학년도-학년-반 중복 시 → 409")
        void classDuplicate() throws Exception {
            given(adminService.createClass(any()))
                    .willThrow(new BusinessException(ErrorCode.CLASS_DUPLICATE));

            mockMvc.perform(post("/api/v1/admin/classes")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "academicYear", 2026, "grade", 1, "classNum", 1))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("학년 범위 초과(4학년) → 400")
        void invalidGrade() throws Exception {
            mockMvc.perform(post("/api/v1/admin/classes")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "academicYear", 2026, "grade", 4, "classNum", 1))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("STUDENT 권한으로 생성 시도 → 403")
        void studentForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/classes")
                            .with(student())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "academicYear", 2026, "grade", 1, "classNum", 1))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/classes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "academicYear", 2026, "grade", 1, "classNum", 1))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/classes/{classId}/homeroom — 담임 배정")
    class AssignHomeroom {

        @Test
        @DisplayName("ADMIN 권한으로 담임 배정 성공 → 200")
        void adminCanAssign() throws Exception {
            doNothing().when(adminService).assignHomeroom(anyLong(), any());

            mockMvc.perform(put("/api/v1/admin/classes/1/homeroom")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("teacherId", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("존재하지 않는 반 → 404")
        void classNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.CLASS_NOT_FOUND))
                    .when(adminService).assignHomeroom(eq(999L), any());

            mockMvc.perform(put("/api/v1/admin/classes/999/homeroom")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("teacherId", 1))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("존재하지 않는 교사 → 404")
        void teacherNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.TEACHER_NOT_FOUND))
                    .when(adminService).assignHomeroom(eq(1L), any());

            mockMvc.perform(put("/api/v1/admin/classes/1/homeroom")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("teacherId", 999))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TEACHER 권한으로 담임 배정 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(put("/api/v1/admin/classes/1/homeroom")
                            .with(teacher())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("teacherId", 1))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(put("/api/v1/admin/classes/1/homeroom")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("teacherId", 1))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/classes/{classId}/students — 학생 반 배정")
    class EnrollStudent {

        @Test
        @DisplayName("ADMIN 권한으로 학생 반 배정 성공 → 200")
        void adminCanEnroll() throws Exception {
            doNothing().when(adminService).enrollStudent(anyLong(), any());

            mockMvc.perform(post("/api/v1/admin/classes/1/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "studentNum", 5))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("존재하지 않는 반 → 404")
        void classNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.CLASS_NOT_FOUND))
                    .when(adminService).enrollStudent(eq(999L), any());

            mockMvc.perform(post("/api/v1/admin/classes/999/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "studentNum", 5))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("동일 학년도 이미 등록된 학생 → 409")
        void enrollmentDuplicate() throws Exception {
            doThrow(new BusinessException(ErrorCode.ENROLLMENT_DUPLICATE))
                    .when(adminService).enrollStudent(eq(1L), any());

            mockMvc.perform(post("/api/v1/admin/classes/1/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "studentNum", 5))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("동일 번호 중복 → 409")
        void studentNumDuplicate() throws Exception {
            doThrow(new BusinessException(ErrorCode.STUDENT_NUM_DUPLICATE))
                    .when(adminService).enrollStudent(eq(1L), any());

            mockMvc.perform(post("/api/v1/admin/classes/1/students")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 2, "studentNum", 5))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("STUDENT 권한으로 배정 시도 → 403")
        void studentForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/classes/1/students")
                            .with(student())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "studentNum", 5))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/classes/1/students")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "studentNum", 5))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 과목 배정
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/admin/assignments — 과목 배정 목록 조회")
    class GetAssignments {

        @Test
        @DisplayName("ADMIN 권한으로 배정 목록 조회 → 200")
        void adminCanGetAssignments() throws Exception {
            given(adminService.getAssignments(2026)).willReturn(List.of(sampleAssignment()));

            mockMvc.perform(get("/api/v1/admin/assignments")
                            .with(admin())
                            .param("academicYear", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].academicYear").value(2026))
                    .andExpect(jsonPath("$.data[0].subject.code").value("MATH"));
        }

        @Test
        @DisplayName("TEACHER 권한으로 조회 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/assignments")
                            .with(teacher())
                            .param("academicYear", "2026"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/admin/assignments").param("academicYear", "2026"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/assignments — 과목 배정 등록")
    class CreateAssignment {

        @Test
        @DisplayName("ADMIN 권한으로 배정 등록 성공 → 201")
        void adminCanCreate() throws Exception {
            given(adminService.createAssignment(any())).willReturn(sampleAssignment());

            mockMvc.perform(post("/api/v1/admin/assignments")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "teacherId", 1, "classId", 1,
                                    "subjectId", 1, "academicYear", 2026))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.teacher.name").value("이교사"))
                    .andExpect(jsonPath("$.data.subject.code").value("MATH"));
        }

        @Test
        @DisplayName("교사를 찾을 수 없음 → 404")
        void teacherNotFound() throws Exception {
            given(adminService.createAssignment(any()))
                    .willThrow(new BusinessException(ErrorCode.TEACHER_NOT_FOUND));

            mockMvc.perform(post("/api/v1/admin/assignments")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "teacherId", 999, "classId", 1,
                                    "subjectId", 1, "academicYear", 2026))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("중복 배정 → 409")
        void assignmentDuplicate() throws Exception {
            given(adminService.createAssignment(any()))
                    .willThrow(new BusinessException(ErrorCode.ASSIGNMENT_DUPLICATE));

            mockMvc.perform(post("/api/v1/admin/assignments")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "teacherId", 1, "classId", 1,
                                    "subjectId", 1, "academicYear", 2026))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("TEACHER 권한으로 배정 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/assignments")
                            .with(teacher())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "teacherId", 1, "classId", 1,
                                    "subjectId", 1, "academicYear", 2026))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/admin/assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "teacherId", 1, "classId", 1,
                                    "subjectId", 1, "academicYear", 2026))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/assignments/{assignmentId} — 과목 배정 해제")
    class DeleteAssignment {

        @Test
        @DisplayName("ADMIN 권한으로 배정 해제 성공 → 200")
        void adminCanDelete() throws Exception {
            doNothing().when(adminService).deleteAssignment(1L);

            mockMvc.perform(delete("/api/v1/admin/assignments/1").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("존재하지 않는 배정 삭제 → 404")
        void assignmentNotFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND))
                    .when(adminService).deleteAssignment(999L);

            mockMvc.perform(delete("/api/v1/admin/assignments/999").with(admin()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TEACHER 권한으로 삭제 시도 → 403")
        void teacherForbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/assignments/1").with(teacher()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/assignments/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
