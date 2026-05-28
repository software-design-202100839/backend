package com.sscm.analytics.chatbot.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.analytics.chatbot.service.ReportEditService;
import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.ParentStudent;
import com.sscm.auth.entity.Student;
import com.sscm.auth.repository.ParentRepository;
import com.sscm.auth.repository.ParentStudentRepository;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.common.response.ApiResponse;
import com.sscm.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * AI 생성 보고서 Human-in-the-Loop 컨트롤러.
 *
 * 교사가 AI 초안을 수정하여 최종 의견서로 확정하는 엔드포인트를 제공한다.
 * 보고서 조회 시 역할별 접근 제어:
 * - TEACHER/ADMIN: 본인 학교(schoolId) 보고서만 조회
 * - STUDENT: 본인 보고서만 조회
 * - PARENT: 자녀 보고서만 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics/reports")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReportController {

    private final ReportEditService reportEditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;

    /**
     * AI 생성 보고서를 조회한다.
     * 역할별 접근 제어로 권한 없는 보고서는 404를 반환한다.
     */
    @GetMapping("/{reportId}")
    public ApiResponse<ReportDetailResponse> getReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        // 1. 보고서 조회
        Map<String, Object> report;
        try {
            report = jdbcTemplate.queryForMap(
                    "SELECT id, student_id, school_id, academic_year, semester, " +
                    "draft_text, reference_ids, created_by, created_at " +
                    "FROM ai_generated_reports WHERE id = ?",
                    reportId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
        }

        Long reportSchoolId = ((Number) report.get("school_id")).longValue();
        Long reportStudentId = ((Number) report.get("student_id")).longValue();

        // 2. 역할별 접근 제어
        verifyAccess(role, userId, reportSchoolId, reportStudentId);

        // 3. references JSON 파싱
        List<Map<String, Object>> references = parseReferences(report.get("reference_ids"));

        // 4. 교사 수정 이력 조회 (최신 1건)
        ReportEditInfo editInfo = getLatestEdit(reportId);

        return ApiResponse.success(new ReportDetailResponse(
                ((Number) report.get("id")).longValue(),
                reportStudentId,
                (Integer) report.get("academic_year"),
                (Integer) report.get("semester"),
                (String) report.get("draft_text"),
                references,
                report.get("created_at").toString(),
                editInfo
        ));
    }

    /**
     * AI 생성 보고서를 교사가 수정한 최종본을 저장한다.
     */
    @PostMapping("/{reportId}/edit")
    public ApiResponse<Void> editReport(
            @PathVariable Long reportId,
            @RequestBody ReportEditRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        reportEditService.saveEdit(reportId, request.finalText(), userId);
        return ApiResponse.success("보고서 수정이 저장되었습니다.");
    }

    // ── 접근 제어 ─────────────────────────────────────────────

    private void verifyAccess(String role, Long userId, Long reportSchoolId, Long reportStudentId) {
        switch (role) {
            case "ROLE_TEACHER", "ROLE_ADMIN" -> {
                // 교사/관리자: 본인 학교 보고서만
                Long currentSchoolId = TenantContext.getSchoolId();
                if (currentSchoolId != null && !currentSchoolId.equals(reportSchoolId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
                }
            }
            case "ROLE_STUDENT" -> {
                // 학생: 본인 보고서만
                Long studentId = studentRepository.findByUser_Id(userId)
                        .map(Student::getId)
                        .orElse(null);
                if (studentId == null || !studentId.equals(reportStudentId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
                }
            }
            case "ROLE_PARENT" -> {
                // 학부모: 자녀 보고서만
                Parent parent = parentRepository.findByUser_Id(userId).orElse(null);
                if (parent == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
                }
                List<Long> childIds = parentStudentRepository.findByParent(parent).stream()
                        .map(ps -> ps.getStudent().getId())
                        .toList();
                if (!childIds.contains(reportStudentId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseReferences(Object refsObj) {
        if (refsObj == null) {
            return List.of();
        }
        try {
            String json = refsObj.toString();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("참조 JSON 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private ReportEditInfo getLatestEdit(Long reportId) {
        try {
            Map<String, Object> edit = jdbcTemplate.queryForMap(
                    "SELECT final_text, edit_distance, edited_by, edited_at " +
                    "FROM teacher_report_edits WHERE report_id = ? ORDER BY edited_at DESC LIMIT 1",
                    reportId);
            return new ReportEditInfo(
                    (String) edit.get("final_text"),
                    (Integer) edit.get("edit_distance"),
                    ((Number) edit.get("edited_by")).longValue(),
                    edit.get("edited_at").toString()
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // ── DTO ─────────────────────────────────────────────

    record ReportEditRequest(String finalText) {}

    record ReportDetailResponse(
            Long id,
            Long studentId,
            Integer academicYear,
            Integer semester,
            String draftText,
            List<Map<String, Object>> references,
            String createdAt,
            ReportEditInfo edit
    ) {}

    record ReportEditInfo(
            String finalText,
            Integer editDistance,
            Long editedBy,
            String editedAt
    ) {}
}
