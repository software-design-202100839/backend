package com.sscm.grade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.grade.dto.ScoreResponse;
import com.sscm.grade.dto.StudentScoreSummary;
import com.sscm.grade.dto.SubjectResponse;
import com.sscm.grade.service.ScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.sscm.common.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreController.class)
@Import(SecurityConfig.class)
@DisplayName("ScoreController 통합 테스트")
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScoreService scoreService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor student() {
        return authentication(new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    private static RequestPostProcessor parent() {
        return authentication(new UsernamePasswordAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_PARENT"))));
    }

    private ScoreResponse sampleScoreResponse() {
        return ScoreResponse.builder()
                .id(1L).studentId(1L).studentName("이학생")
                .subjectId(1L).subjectName("수학").subjectCode("MATH")
                .year(2026).semester(1)
                .score(new BigDecimal("95.00")).gradeLetter("A+").rank(1)
                .build();
    }

    private String scoreRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "studentId", 1, "subjectId", 1,
                "year", 2026, "semester", 1, "score", 95.00));
    }

    @Nested
    @DisplayName("POST /api/v1/grades — 성적 등록")
    class CreateScore {

        @Test
        @DisplayName("교사 권한으로 성적 등록 성공 → 201")
        void teacherCanCreate() throws Exception {
            given(scoreService.createScore(any(), any())).willReturn(sampleScoreResponse());

            mockMvc.perform(post("/api/v1/grades")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scoreRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.gradeLetter").value("A+"));
        }

        @Test
        @DisplayName("학생 권한으로 성적 등록 시도 → 403")
        void studentCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(student()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scoreRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("학부모 권한으로 성적 등록 시도 → 403")
        void parentCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(parent()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scoreRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticatedUser() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scoreRequestJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("점수 -1 입력 → 400 유효성 실패")
        void negativeScore() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "subjectId", 1,
                                    "year", 2026, "semester", 1, "score", -1))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("점수 101 입력 → 400 유효성 실패")
        void overMaxScore() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "subjectId", 1,
                                    "year", 2026, "semester", 1, "score", 101))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("학기 3 입력 → 400 유효성 실패")
        void invalidSemester() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "subjectId", 1,
                                    "year", 2026, "semester", 3, "score", 90))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 필드 누락 → 400 유효성 실패")
        void missingRequiredField() throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("studentId", 1))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("동일 학생-과목-학기 중복 등록 → 409")
        void duplicateScore() throws Exception {
            given(scoreService.createScore(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.SCORE_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/grades")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scoreRequestJson()))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/grades/{id} — 성적 수정")
    class UpdateScore {

        @Test
        @DisplayName("교사 권한으로 성적 수정 성공 → 200")
        void teacherCanUpdate() throws Exception {
            given(scoreService.updateScore(eq(1L), any(), any())).willReturn(sampleScoreResponse());

            mockMvc.perform(put("/api/v1/grades/1")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("score", 88.00))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("학생 권한으로 성적 수정 시도 → 403")
        void studentCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/grades/1")
                            .with(student()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("score", 88.00))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("존재하지 않는 성적 수정 → 404")
        void scoreNotFound() throws Exception {
            given(scoreService.updateScore(eq(999L), any(), any()))
                    .willThrow(new BusinessException(ErrorCode.SCORE_NOT_FOUND));

            mockMvc.perform(put("/api/v1/grades/999")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("score", 88.00))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/grades/{id} — 성적 삭제")
    class DeleteScore {

        @Test
        @DisplayName("교사 권한으로 성적 삭제 성공 → 200")
        void teacherCanDelete() throws Exception {
            doNothing().when(scoreService).deleteScore(1L);

            mockMvc.perform(delete("/api/v1/grades/1")
                            .with(teacher()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("학생 권한으로 삭제 시도 → 403")
        void studentCannotDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/grades/1")
                            .with(student()).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/grades/{id} — 성적 단건 조회")
    class GetScore {

        @Test
        @DisplayName("인증된 사용자 조회 성공 → 200")
        void authenticatedUserCanGet() throws Exception {
            given(scoreService.getScore(1L)).willReturn(sampleScoreResponse());

            mockMvc.perform(get("/api/v1/grades/1").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.studentName").value("이학생"));
        }

        @Test
        @DisplayName("존재하지 않는 성적 → 404")
        void notFound() throws Exception {
            given(scoreService.getScore(999L))
                    .willThrow(new BusinessException(ErrorCode.SCORE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/grades/999").with(teacher()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/grades/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/grades/students/{id} — 학생별 학기 성적")
    class GetStudentScores {

        @Test
        @DisplayName("총점/평균 포함 성적 조회 → 200")
        void success() throws Exception {
            StudentScoreSummary summary = StudentScoreSummary.builder()
                    .studentId(1L).studentName("이학생")
                    .year(2026).semester(1)
                    .scores(List.of(sampleScoreResponse()))
                    .totalScore(new BigDecimal("95.00"))
                    .averageScore(new BigDecimal("95.00"))
                    .averageGradeLetter("A+")
                    .build();
            given(scoreService.getStudentScores(1L, 2026, 1)).willReturn(summary);

            mockMvc.perform(get("/api/v1/grades/students/1")
                            .with(teacher())
                            .param("year", "2026")
                            .param("semester", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalScore").value(95.00))
                    .andExpect(jsonPath("$.data.averageGradeLetter").value("A+"));
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void studentNotFound() throws Exception {
            given(scoreService.getStudentScores(999L, 2026, 1))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/grades/students/999")
                            .with(teacher())
                            .param("year", "2026")
                            .param("semester", "1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/grades/subjects — 과목 목록")
    class GetSubjects {

        @Test
        @DisplayName("전체 과목 목록 조회 → 200")
        void success() throws Exception {
            List<SubjectResponse> subjects = List.of(
                    SubjectResponse.builder().id(1L).name("수학").code("MATH").build(),
                    SubjectResponse.builder().id(2L).name("영어").code("ENG").build()
            );
            given(scoreService.getAllSubjects()).willReturn(subjects);

            mockMvc.perform(get("/api/v1/grades/subjects").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }
}
