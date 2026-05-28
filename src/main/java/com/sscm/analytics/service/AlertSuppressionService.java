package com.sscm.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 위험 알림 억제(suppression) 서비스.
 *
 * 왜 알림 억제가 필요한가?
 * - 교사가 이미 인지하고 대응 중인 학생에 대해 반복 알림은 방해가 된다
 * - 예: "김철수 성적 하락" 알림을 본 교사가 "이미 알고 있다, 그만 알려줘"라고 설정
 * - suppressed_until이 NULL이면 영구 억제, 날짜가 있으면 해당 시점까지만 억제
 *
 * ON CONFLICT DO NOTHING으로 중복 억제 시도를 안전하게 무시한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertSuppressionService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 해당 교사-학생-규칙 조합에 대한 알림이 억제 상태인지 확인한다.
     */
    public boolean isSuppressed(Long teacherId, Long studentId, String ruleType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_suppressions " +
                "WHERE teacher_id = ? AND student_id = ? AND rule_type = ? " +
                "AND (suppressed_until IS NULL OR suppressed_until > NOW())",
                Integer.class, teacherId, studentId, ruleType);
        return count != null && count > 0;
    }

    /**
     * 알림을 영구 억제한다.
     */
    public void suppress(Long teacherId, Long studentId, String ruleType) {
        jdbcTemplate.update(
                "INSERT INTO alert_suppressions (teacher_id, student_id, rule_type) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (teacher_id, student_id, rule_type) DO NOTHING",
                teacherId, studentId, ruleType);
        log.info("알림 억제 설정: teacherId={}, studentId={}, ruleType={}", teacherId, studentId, ruleType);
    }

    /**
     * 알림 억제를 해제한다.
     */
    public void unsuppress(Long teacherId, Long studentId, String ruleType) {
        jdbcTemplate.update(
                "DELETE FROM alert_suppressions " +
                "WHERE teacher_id = ? AND student_id = ? AND rule_type = ?",
                teacherId, studentId, ruleType);
        log.info("알림 억제 해제: teacherId={}, studentId={}, ruleType={}", teacherId, studentId, ruleType);
    }
}
