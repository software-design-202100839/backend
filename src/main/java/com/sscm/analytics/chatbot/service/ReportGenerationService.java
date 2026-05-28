package com.sscm.analytics.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.analytics.dto.StudentDashboardDto;
import com.sscm.analytics.dto.StudentScoreSummaryDto;
import com.sscm.analytics.service.AnalyticsDashboardService;
import com.sscm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 학생 종합 의견서 생성 서비스.
 *
 * Hybrid AI 아키텍처의 핵심: 3가지 데이터 소스를 결합하여 근거 기반 보고서를 생성한다.
 *
 * 1. Function Calling 데이터 — AnalyticsDashboardService로 성적/출결/피드백/상담 수치 조회
 * 2. RAG 데이터 — EmbeddingService로 관련 피드백/상담 원문 검색 (의미 기반)
 * 3. LLM 생성 — 위 데이터를 컨텍스트로 Claude에게 초안 작성 요청
 *
 * 생성된 보고서는 ai_generated_reports 테이블에 저장되며,
 * 교사가 Human-in-the-Loop으로 수정할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final AnalyticsDashboardService dashboardService;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 학생의 학기말 종합 의견 초안을 생성한다.
     *
     * @param studentId 학생 ID
     * @param year      학년도
     * @param semester  학기
     * @param schoolId  학교 ID (멀티테넌트)
     * @param userId    요청한 교사의 user ID
     * @return 초안 텍스트 + 참조 출처 목록
     */
    public ReportResult generateReport(Long studentId, int year, int semester,
                                       Long schoolId, Long userId) {
        log.info("보고서 생성 시작: studentId={}, year={}, semester={}", studentId, year, semester);

        // 1. 구조화된 데이터 수집 (Function Calling 레이어)
        StudentScoreSummaryDto scoreSummary = getScoreSummarySafely(studentId, year, semester);
        StudentDashboardDto dashboard = getDashboardSafely(studentId, year, semester);

        // 2. RAG - 의미 기반으로 관련 피드백/상담 원문 검색
        List<Map<String, Object>> feedbackResults = embeddingService.searchFeedback(
                "학생 학기 종합 평가", schoolId, List.of(studentId), year, semester, 5);
        List<Map<String, Object>> counselingResults = embeddingService.searchCounseling(
                "학생 학기 종합 평가", schoolId, List.of(studentId), year, semester, 3);

        // 3. LLM 컨텍스트 구성
        String context = buildContext(scoreSummary, dashboard, feedbackResults, counselingResults);

        log.info("보고서 LLM 컨텍스트: length={}", context.length());

        // 4. LLM으로 초안 생성
        String prompt = """
                다음 학생 데이터를 바탕으로 학기말 종합 의견 초안을 작성하세요.
                - 성적, 출결, 피드백, 상담 내용을 종합하여 2~3문단으로 작성
                - 학생의 강점과 개선점을 균형있게 서술
                - 구체적 수치와 사례를 포함
                - 한국어로 작성

                """ + context;

        String draft = ChatClient.create(chatModel)
                .prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder().maxTokens(4096).build())
                .call()
                .content();

        log.info("보고서 초안 생성됨: length={}, preview={}",
                draft != null ? draft.length() : 0,
                draft != null && draft.length() > 100 ? draft.substring(0, 100) + "..." : draft);

        // 5. 참조 출처 목록 구성
        List<Map<String, Object>> references = buildReferences(feedbackResults, counselingResults);

        // 6. DB에 보고서 저장 → 생성된 ID 반환
        Long reportId = saveReport(studentId, schoolId, year, semester, draft, references, userId);

        log.info("보고서 생성 완료: studentId={}, reportId={}", studentId, reportId);
        return new ReportResult(reportId, draft, references);
    }

    // ── 내부 메서드 ─────────────────────────────────────────────

    private StudentScoreSummaryDto getScoreSummarySafely(Long studentId, int year, int semester) {
        try {
            return dashboardService.getScoreSummary(studentId, year, semester);
        } catch (BusinessException e) {
            log.debug("성적 요약 데이터 없음: studentId={}", studentId);
            return null;
        }
    }

    private StudentDashboardDto getDashboardSafely(Long studentId, int year, int semester) {
        try {
            return dashboardService.getStudentDashboard(studentId, year, semester);
        } catch (BusinessException e) {
            log.debug("대시보드 데이터 없음: studentId={}", studentId);
            return null;
        }
    }

    private String buildContext(StudentScoreSummaryDto scoreSummary,
                                StudentDashboardDto dashboard,
                                List<Map<String, Object>> feedbackResults,
                                List<Map<String, Object>> counselingResults) {
        StringBuilder context = new StringBuilder();
        context.append("## 학생 정보\n");

        if (scoreSummary != null) {
            context.append("- 이름: ").append(scoreSummary.getStudentName()).append("\n");
            context.append("- 수강 과목 수: ").append(scoreSummary.getSubjectCount()).append("\n");
            context.append("- 평균 점수: ").append(scoreSummary.getAverageScore()).append("\n");
            context.append("- 최고 점수: ").append(scoreSummary.getHighestScore()).append("\n");
            context.append("- 최저 점수: ").append(scoreSummary.getLowestScore()).append("\n");
            context.append("- 평균 등급: ").append(scoreSummary.getAverageGrade()).append("\n");
        }

        if (dashboard != null) {
            context.append("- 위험도: ").append(dashboard.getRiskLevel()).append("\n");
            context.append("- 성적 추이: ").append(dashboard.getScoreTrend()).append("\n");
            context.append("- 출결 횟수: ").append(dashboard.getAttendanceCount()).append("\n");
            context.append("- 수상 횟수: ").append(dashboard.getAwardCount()).append("\n");
            context.append("- 총 피드백 수: ").append(dashboard.getTotalFeedbackCount()).append("\n");
            context.append("- 총 상담 수: ").append(dashboard.getTotalCounselCount()).append("\n");
        }

        if (scoreSummary == null && dashboard == null) {
            context.append("- 해당 학기 데이터가 없습니다.\n");
        }

        context.append("\n## 관련 피드백\n");
        if (feedbackResults.isEmpty()) {
            context.append("- 관련 피드백이 없습니다.\n");
        } else {
            for (var fb : feedbackResults) {
                context.append("- ").append(fb.get("content_preview")).append("\n");
            }
        }

        context.append("\n## 관련 상담\n");
        if (counselingResults.isEmpty()) {
            context.append("- 관련 상담 기록이 없습니다.\n");
        } else {
            for (var cs : counselingResults) {
                context.append("- ").append(cs.get("content_preview")).append("\n");
            }
        }

        return context.toString();
    }

    private List<Map<String, Object>> buildReferences(List<Map<String, Object>> feedbackResults,
                                                       List<Map<String, Object>> counselingResults) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (var fb : feedbackResults) {
            references.add(Map.of(
                    "type", "feedback",
                    "id", fb.get("feedback_id"),
                    "preview", fb.get("content_preview")
            ));
        }
        for (var cs : counselingResults) {
            references.add(Map.of(
                    "type", "counseling",
                    "id", cs.get("counseling_id"),
                    "preview", cs.get("content_preview")
            ));
        }
        return references;
    }

    private Long saveReport(Long studentId, Long schoolId, int year, int semester,
                            String draft, List<Map<String, Object>> references, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String refsJson;
        try {
            refsJson = objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            log.error("참조 JSON 직렬화 실패: {}", e.getMessage());
            refsJson = null;
        }

        final String json = refsJson;
        if (json != null) {
            jdbcTemplate.update(conn -> {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ai_generated_reports (student_id, school_id, academic_year, semester, draft_text, reference_ids, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
                        new String[]{"id"});
                ps.setLong(1, studentId);
                ps.setLong(2, schoolId);
                ps.setInt(3, year);
                ps.setInt(4, semester);
                ps.setString(5, draft);
                ps.setString(6, json);
                ps.setLong(7, userId);
                return ps;
            }, keyHolder);
        } else {
            jdbcTemplate.update(conn -> {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ai_generated_reports (student_id, school_id, academic_year, semester, draft_text, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                        new String[]{"id"});
                ps.setLong(1, studentId);
                ps.setLong(2, schoolId);
                ps.setInt(3, year);
                ps.setInt(4, semester);
                ps.setString(5, draft);
                ps.setLong(6, userId);
                return ps;
            }, keyHolder);
        }

        Number generatedId = keyHolder.getKey();
        return generatedId != null ? generatedId.longValue() : null;
    }

    // ── 결과 DTO ─────────────────────────────────────────────

    public record ReportResult(Long reportId, String draft, List<Map<String, Object>> references) {}
}
