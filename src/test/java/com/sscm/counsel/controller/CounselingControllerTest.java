package com.sscm.counsel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.config.SecurityConfig;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.counsel.dto.CounselingResponse;
import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.service.CounselingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CounselingController.class)
@Import(SecurityConfig.class)
@DisplayName("CounselingController 통합 테스트")
class CounselingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CounselingService counselingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static RequestPostProcessor student() {
        return authentication(new UsernamePasswordAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    private CounselingResponse sampleResponse() {
        return CounselingResponse.builder()
                .id(1L)
                .studentId(10L).studentName("이학생")
                .teacherId(1L).teacherName("김선생")
                .counselDate(LocalDate.of(2026, 4, 21))
                .category(CounselCategory.ACADEMIC)
                .content("학업 상담 내용")
                .nextPlan("다음 상담 계획")
                .nextCounselDate(LocalDate.of(2026, 4, 28))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String createRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "studentId", 10,
                "counselDate", "2026-04-21",
                "category", "ACADEMIC",
                "content", "학업 상담 내용"));
    }

    private String updateRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "counselDate", "2026-04-21",
                "category", "ACADEMIC",
                "content", "수정된 상담 내용"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/counselings
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/counselings — 상담 내역 등록")
    class CreateCounseling {

        @Test
        @DisplayName("교사 권한으로 상담 등록 성공 → 201")
        void teacherCanCreate() throws Exception {
            given(counselingService.createCounseling(any(), eq(1L))).willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/counselings")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.category").value("ACADEMIC"));
        }

        @Test
        @DisplayName("어드민 권한으로 상담 등록 시도 → 403")
        void adminCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/counselings")
                            .with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("학생 권한으로 상담 등록 시도 → 403")
        void studentCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/counselings")
                            .with(student()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticatedUser() throws Exception {
            mockMvc.perform(post("/api/v1/counselings")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("studentId 누락 → 400 유효성 실패")
        void missingStudentId() throws Exception {
            mockMvc.perform(post("/api/v1/counselings")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "counselDate", "2026-04-21",
                                    "category", "ACADEMIC",
                                    "content", "내용"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("content 누락 → 400 유효성 실패")
        void missingContent() throws Exception {
            mockMvc.perform(post("/api/v1/counselings")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 10,
                                    "counselDate", "2026-04-21",
                                    "category", "ACADEMIC"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void studentNotFound() throws Exception {
            given(counselingService.createCounseling(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(post("/api/v1/counselings")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("교사 프로필이 없는 유저 → 404")
        void teacherProfileNotFound() throws Exception {
            given(counselingService.createCounseling(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.TEACHER_NOT_FOUND));

            mockMvc.perform(post("/api/v1/counselings")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson()))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/counselings/{counselingId}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/counselings/{counselingId} — 상담 내역 수정")
    class UpdateCounseling {

        @Test
        @DisplayName("교사(작성자)가 상담 수정 성공 → 200")
        void teacherCanUpdate() throws Exception {
            given(counselingService.updateCounseling(eq(1L), any(), eq(1L))).willReturn(sampleResponse());

            mockMvc.perform(put("/api/v1/counselings/1")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequestJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("어드민 권한으로 상담 수정 시도 → 403")
        void adminCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/counselings/1")
                            .with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(put("/api/v1/counselings/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequestJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 상담 수정 → 404")
        void counselingNotFound() throws Exception {
            given(counselingService.updateCounseling(eq(999L), any(), any()))
                    .willThrow(new BusinessException(ErrorCode.COUNSELING_NOT_FOUND));

            mockMvc.perform(put("/api/v1/counselings/999")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequestJson()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("다른 교사가 작성한 상담 수정 시도 → 403")
        void accessDeniedForOtherTeacher() throws Exception {
            given(counselingService.updateCounseling(eq(1L), any(), any()))
                    .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

            mockMvc.perform(put("/api/v1/counselings/1")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("content 누락 → 400 유효성 실패")
        void missingContent() throws Exception {
            mockMvc.perform(put("/api/v1/counselings/1")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "counselDate", "2026-04-21",
                                    "category", "ACADEMIC"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/counselings/{counselingId}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/counselings/{counselingId} — 상담 내역 단건 조회")
    class GetCounseling {

        @Test
        @DisplayName("교사가 상담 단건 조회 성공 → 200")
        void teacherCanGet() throws Exception {
            given(counselingService.getCounseling(1L)).willReturn(sampleResponse());

            mockMvc.perform(get("/api/v1/counselings/1").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.studentName").value("이학생"))
                    .andExpect(jsonPath("$.data.teacherName").value("김선생"));
        }

        @Test
        @DisplayName("어드민이 상담 단건 조회 성공 → 200")
        void adminCanGet() throws Exception {
            given(counselingService.getCounseling(1L)).willReturn(sampleResponse());

            mockMvc.perform(get("/api/v1/counselings/1").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("학생 권한으로 단건 조회 시도 → 403")
        void studentCannotGet() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/1").with(student()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 상담 → 404")
        void notFound() throws Exception {
            given(counselingService.getCounseling(999L))
                    .willThrow(new BusinessException(ErrorCode.COUNSELING_NOT_FOUND));

            mockMvc.perform(get("/api/v1/counselings/999").with(teacher()))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/counselings/students/{studentId}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/counselings/students/{studentId} — 학생별 상담 조회")
    class GetCounselingsByStudent {

        @Test
        @DisplayName("교사가 카테고리 없이 학생별 상담 조회 → 200")
        void teacherGetWithoutCategory() throws Exception {
            given(counselingService.getCounselingsByStudent(10L, null))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/counselings/students/10").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].studentId").value(10));
        }

        @Test
        @DisplayName("어드민이 카테고리 필터로 학생별 상담 조회 → 200")
        void adminGetWithCategory() throws Exception {
            given(counselingService.getCounselingsByStudent(10L, CounselCategory.ACADEMIC))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/counselings/students/10")
                            .with(admin())
                            .param("category", "ACADEMIC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].category").value("ACADEMIC"));
        }

        @Test
        @DisplayName("학생 권한으로 조회 시도 → 403")
        void studentCannotGet() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/students/10").with(student()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/students/10"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void studentNotFound() throws Exception {
            given(counselingService.getCounselingsByStudent(eq(999L), any()))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/counselings/students/999").with(teacher()))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/counselings/my
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/counselings/my — 내 상담 내역 조회")
    class GetMyCounselings {

        @Test
        @DisplayName("교사가 자신의 상담 내역 조회 → 200")
        void teacherGetMyCounselings() throws Exception {
            given(counselingService.getMyCounselings(1L))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/counselings/my").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].teacherName").value("김선생"));
        }

        @Test
        @DisplayName("어드민 권한으로 내 상담 조회 시도 → 403")
        void adminCannotGetMy() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/my").with(admin()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("학생 권한으로 내 상담 조회 시도 → 403")
        void studentCannotGetMy() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/my").with(student()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/my"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("교사 프로필 없음 → 404")
        void teacherNotFound() throws Exception {
            given(counselingService.getMyCounselings(1L))
                    .willThrow(new BusinessException(ErrorCode.TEACHER_NOT_FOUND));

            mockMvc.perform(get("/api/v1/counselings/my").with(teacher()))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/counselings/students/{studentId}/search
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/counselings/students/{studentId}/search — 기간 검색")
    class SearchCounselings {

        @Test
        @DisplayName("교사가 기간으로 상담 내역 검색 → 200")
        void teacherCanSearch() throws Exception {
            given(counselingService.searchCounselings(10L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/counselings/students/10/search")
                            .with(teacher())
                            .param("startDate", "2026-04-01")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("어드민이 기간으로 상담 내역 검색 → 200")
        void adminCanSearch() throws Exception {
            given(counselingService.searchCounselings(10L,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/counselings/students/10/search")
                            .with(admin())
                            .param("startDate", "2026-04-01")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("학생 권한으로 검색 시도 → 403")
        void studentCannotSearch() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/students/10/search")
                            .with(student())
                            .param("startDate", "2026-04-01")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/students/10/search")
                            .param("startDate", "2026-04-01")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 학생으로 검색 → 404")
        void studentNotFound() throws Exception {
            given(counselingService.searchCounselings(eq(999L), any(), any()))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/counselings/students/999/search")
                            .with(teacher())
                            .param("startDate", "2026-04-01")
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("startDate 누락 → 400")
        void missingStartDate() throws Exception {
            mockMvc.perform(get("/api/v1/counselings/students/10/search")
                            .with(teacher())
                            .param("endDate", "2026-04-30"))
                    .andExpect(status().isBadRequest());
        }
    }
}
