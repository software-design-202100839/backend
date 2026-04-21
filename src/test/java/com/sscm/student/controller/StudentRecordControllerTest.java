package com.sscm.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.student.dto.StudentInfoResponse;
import com.sscm.student.dto.StudentRecordResponse;
import com.sscm.student.entity.RecordCategory;
import com.sscm.student.service.StudentRecordService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentRecordController.class)
@Import(SecurityConfig.class)
@DisplayName("StudentRecordController 통합 테스트")
class StudentRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StudentRecordService studentRecordService;

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

    private StudentRecordResponse sampleRecordResponse() {
        return StudentRecordResponse.builder()
                .id(1L).studentId(1L).studentName("이학생")
                .year(2026).semester(1).category(RecordCategory.ATTENDANCE)
                .content(Map.of("결석", 2, "지각", 1))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private StudentInfoResponse sampleStudentInfo() {
        return StudentInfoResponse.builder()
                .id(1L).name("이학생").email("student@test.com")
                .phone("010-1234-5678").admissionYear(2025)
                .build();
    }

    private String recordRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "studentId", 1, "year", 2026, "semester", 1,
                "category", "ATTENDANCE", "content", Map.of("결석", 2, "지각", 1)));
    }

    @Nested
    @DisplayName("GET /api/v1/students — 학생 목록 조회")
    class GetAllStudents {

        @Test
        @DisplayName("인증된 사용자 학생 목록 조회 → 200")
        void success() throws Exception {
            given(studentRecordService.getAllStudents())
                    .willReturn(List.of(sampleStudentInfo()));

            mockMvc.perform(get("/api/v1/students").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("이학생"));
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/students"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/students/{id} — 학생 기본정보 조회")
    class GetStudentInfo {

        @Test
        @DisplayName("학생 기본정보 조회 성공 → 200")
        void success() throws Exception {
            given(studentRecordService.getStudentInfo(1L)).willReturn(sampleStudentInfo());

            mockMvc.perform(get("/api/v1/students/1").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("이학생"))
                    .andExpect(jsonPath("$.data.admissionYear").value(2025));
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void notFound() throws Exception {
            given(studentRecordService.getStudentInfo(999L))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/students/999").with(teacher()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/students/records — 학생부 등록")
    class CreateRecord {

        @Test
        @DisplayName("교사 권한으로 출결 등록 성공 → 201")
        void teacherCanCreate() throws Exception {
            given(studentRecordService.createRecord(any(), any())).willReturn(sampleRecordResponse());

            mockMvc.perform(post("/api/v1/students/records")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recordRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.category").value("ATTENDANCE"));
        }

        @Test
        @DisplayName("학생 권한으로 학생부 등록 시도 → 403")
        void studentCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/students/records")
                            .with(student()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recordRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("학부모 권한으로 학생부 등록 시도 → 403")
        void parentCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/students/records")
                            .with(parent()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recordRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증 사용자 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/students/records")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recordRequestJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("필수 필드 누락 → 400")
        void missingRequired() throws Exception {
            mockMvc.perform(post("/api/v1/students/records")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("studentId", 1))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("학기 3 입력 → 400")
        void invalidSemester() throws Exception {
            mockMvc.perform(post("/api/v1/students/records")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 1, "year", 2026, "semester", 3,
                                    "category", "ATTENDANCE", "content", Map.of("결석", 0)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void studentNotFound() throws Exception {
            given(studentRecordService.createRecord(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(post("/api/v1/students/records")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "studentId", 999, "year", 2026, "semester", 1,
                                    "category", "ATTENDANCE", "content", Map.of("결석", 0)))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/students/records/{id} — 학생부 수정")
    class UpdateRecord {

        @Test
        @DisplayName("교사 권한으로 수정 성공 → 200")
        void teacherCanUpdate() throws Exception {
            given(studentRecordService.updateRecord(eq(1L), any(), any()))
                    .willReturn(sampleRecordResponse());

            mockMvc.perform(put("/api/v1/students/records/1")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", Map.of("결석", 3)))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("학생 권한으로 수정 시도 → 403")
        void studentCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/students/records/1")
                            .with(student()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", Map.of("결석", 3)))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("존재하지 않는 레코드 수정 → 404")
        void notFound() throws Exception {
            given(studentRecordService.updateRecord(eq(999L), any(), any()))
                    .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(put("/api/v1/students/records/999")
                            .with(teacher()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "content", Map.of("결석", 0)))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/students/records/{id} — 학생부 삭제")
    class DeleteRecord {

        @Test
        @DisplayName("교사 권한으로 삭제 성공 → 200")
        void teacherCanDelete() throws Exception {
            doNothing().when(studentRecordService).deleteRecord(1L);

            mockMvc.perform(delete("/api/v1/students/records/1")
                            .with(teacher()).with(csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("학부모 권한으로 삭제 시도 → 403")
        void parentCannotDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/students/records/1")
                            .with(parent()).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("존재하지 않는 레코드 삭제 → 404")
        void notFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(studentRecordService).deleteRecord(999L);

            mockMvc.perform(delete("/api/v1/students/records/999")
                            .with(teacher()).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/students/records/{id} — 학생부 단건 조회")
    class GetRecord {

        @Test
        @DisplayName("조회 성공 → 200")
        void success() throws Exception {
            given(studentRecordService.getRecord(1L)).willReturn(sampleRecordResponse());

            mockMvc.perform(get("/api/v1/students/records/1").with(teacher()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.studentName").value("이학생"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/students/{id}/records — 학생별 학기 학생부")
    class GetStudentRecords {

        @Test
        @DisplayName("카테고리 필터 없이 전체 조회 → 200")
        void withoutCategory() throws Exception {
            given(studentRecordService.getStudentRecords(1L, 2026, 1, null))
                    .willReturn(List.of(sampleRecordResponse()));

            mockMvc.perform(get("/api/v1/students/1/records")
                            .with(teacher())
                            .param("year", "2026")
                            .param("semester", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("카테고리 필터 ATTENDANCE → 200")
        void withCategory() throws Exception {
            given(studentRecordService.getStudentRecords(1L, 2026, 1, RecordCategory.ATTENDANCE))
                    .willReturn(List.of(sampleRecordResponse()));

            mockMvc.perform(get("/api/v1/students/1/records")
                            .with(teacher())
                            .param("year", "2026")
                            .param("semester", "1")
                            .param("category", "ATTENDANCE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].category").value("ATTENDANCE"));
        }

        @Test
        @DisplayName("존재하지 않는 학생 → 404")
        void studentNotFound() throws Exception {
            given(studentRecordService.getStudentRecords(999L, 2026, 1, null))
                    .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/students/999/records")
                            .with(teacher())
                            .param("year", "2026")
                            .param("semester", "1"))
                    .andExpect(status().isNotFound());
        }
    }
}
