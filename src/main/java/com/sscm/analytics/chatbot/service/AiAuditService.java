package com.sscm.analytics.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 요청 감사(Audit) 로그 서비스.
 *
 * 모든 AI 챗봇 요청/응답을 ai_request_logs 테이블에 기록한다.
 *
 * 왜 감사 로그가 필요한가?
 * 1. 학생 개인정보 접근 추적 — 누가 어떤 학생 데이터를 조회했는지 기록
 * 2. AI 사용 패턴 분석 — 어떤 질문이 많은지, 평균 응답 시간은 얼마인지
 * 3. 비용 관리 — API 호출 횟수와 패턴을 모니터링
 * 4. 문제 디버깅 — 느린 응답이나 오류 발생 시 원인 추적
 *
 * @Async로 비동기 처리하여 사용자 응답 지연을 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * AI 요청을 감사 로그에 기록한다.
     *
     * @param userId             요청한 사용자 ID
     * @param schoolId           학교 ID (멀티테넌트)
     * @param role               사용자 역할 (ROLE_TEACHER, ROLE_STUDENT, ROLE_PARENT)
     * @param question           사용자 질문 원문
     * @param intentType         의도 분류 (현재 미사용, 향후 확장)
     * @param usedTools          사용된 도구 이름 목록
     * @param accessedStudentIds 접근한 학생 ID 목록
     * @param responseSummary    응답 요약 (최대 500자)
     * @param latencyMs          응답 시간 (밀리초)
     */
    public void logRequest(Long userId, Long schoolId, String role, String question,
                           String intentType, List<String> usedTools,
                           List<Long> accessedStudentIds, String responseSummary,
                           long latencyMs) {
        if (schoolId == null) {
            log.warn("AI 감사 로그 스킵: schoolId가 null. userId={}, role={}", userId, role);
            return;
        }
        try {
            String toolsArray = usedTools != null
                    ? "{" + String.join(",", usedTools) + "}"
                    : null;

            String studentIdsArray = accessedStudentIds != null
                    ? "{" + accessedStudentIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")) + "}"
                    : null;

            String truncatedSummary = responseSummary != null && responseSummary.length() > 500
                    ? responseSummary.substring(0, 497) + "..."
                    : responseSummary;

            jdbcTemplate.update(
                    "INSERT INTO ai_request_logs " +
                    "(user_id, school_id, role, question, intent_type, used_tools, response_summary, latency_ms) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    userId, schoolId, role, question, intentType,
                    toolsArray, truncatedSummary, latencyMs);

            log.info("AI 감사 로그 기록: userId={}, schoolId={}, latency={}ms", userId, schoolId, latencyMs);
        } catch (Exception e) {
            log.error("AI 감사 로그 기록 실패: {}", e.getMessage());
        }
    }
}
