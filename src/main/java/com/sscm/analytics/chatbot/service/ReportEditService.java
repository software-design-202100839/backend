package com.sscm.analytics.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 생성 보고서의 교사 수정(Human-in-the-Loop) 서비스.
 *
 * 왜 Human-in-the-Loop이 필요한가?
 * - AI가 생성한 초안은 "초안"일 뿐, 최종 의견서가 아니다
 * - 교사가 검토하고 수정해야 비로소 공식 문서가 된다
 * - 수정 거리(edit_distance)를 기록하여 AI 품질을 정량적으로 추적
 *   → 수정 거리가 작을수록 AI 초안 품질이 높다는 의미
 *   → 이 데이터를 모아 프롬프트 개선에 활용할 수 있다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportEditService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 교사가 AI 초안을 수정한 최종 텍스트를 저장한다.
     *
     * @param reportId 보고서 ID (ai_generated_reports.id)
     * @param finalText 교사가 수정한 최종 텍스트
     * @param editedBy  수정한 교사의 user ID
     */
    public void saveEdit(Long reportId, String finalText, Long editedBy) {
        // 원본 초안 텍스트 조회
        String draftText;
        try {
            draftText = jdbcTemplate.queryForObject(
                    "SELECT draft_text FROM ai_generated_reports WHERE id = ?",
                    String.class, reportId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("보고서를 찾을 수 없습니다: reportId=" + reportId);
        }

        // 수정 거리 계산 (글자 수 차이 기반 — 간단하지만 실용적)
        int editDistance = calculateEditDistance(draftText, finalText);

        jdbcTemplate.update(
                "INSERT INTO teacher_report_edits (report_id, final_text, edit_distance, edited_by) " +
                "VALUES (?, ?, ?, ?)",
                reportId, finalText, editDistance, editedBy);

        log.info("보고서 수정 저장: reportId={}, editDistance={}, editedBy={}", reportId, editDistance, editedBy);
    }

    /**
     * 간단한 수정 거리 계산.
     *
     * 전체 Levenshtein은 O(n*m) 메모리/시간이 필요하여 긴 텍스트에는 비효율적.
     * 여기서는 글자 수 차이를 기본으로 하되, 동일 길이라도 내용이 다르면
     * 문자 단위 불일치 수를 세어 반환한다.
     */
    private int calculateEditDistance(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null ? 0 : (a == null ? b.length() : a.length());
        }

        // 길이가 다르면 길이 차이를 기본 거리로
        if (a.length() != b.length()) {
            return Math.abs(a.length() - b.length());
        }

        // 같은 길이면 문자 단위 불일치 수 계산
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                diff++;
            }
        }
        return diff;
    }
}
