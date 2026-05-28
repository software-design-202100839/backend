package com.sscm.analytics.consumer;

import com.sscm.analytics.config.KafkaConfig;
import com.sscm.analytics.event.AnalyticsEvent;
import com.sscm.analytics.service.AlertSuppressionService;
import com.sscm.notification.entity.NotificationReferenceType;
import com.sscm.notification.entity.NotificationType;
import com.sscm.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 위험 학생 실시간 감지 Kafka Consumer.
 *
 * 기존 analytics consumer(group: sscm-analytics)와 별도 그룹(sscm-risk-detection)으로
 * 동일한 토픽을 구독한다.
 *
 * 왜 별도 그룹인가?
 * - Kafka의 consumer group은 독립적으로 offset을 관리
 * - sscm-analytics 그룹은 집계 업데이트 담당
 * - sscm-risk-detection 그룹은 위험 규칙 검사 담당
 * - 서로 영향을 주지 않고 독립적으로 처리할 수 있다
 *
 * 현재 감지 규칙:
 * 1. SCORE_DROP — 이전 학기 대비 평균 10점 이상 하락
 * 2. NEGATIVE_FEEDBACK — 행동/태도 피드백 누적 3건 이상
 *
 * 알림 중복 방지:
 * - risk_alert_history 테이블로 7일 쿨다운 적용
 * - alert_suppressions 테이블로 교사의 수동 억제 확인
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskDetectionConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final AlertSuppressionService suppressionService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int SCORE_DROP_THRESHOLD = 10;
    private static final int NEGATIVE_FEEDBACK_THRESHOLD = 3;
    private static final int COOLDOWN_DAYS = 7;

    // ── 성적 이벤트 → 성적 하락 감지 ──────────────────────────

    @KafkaListener(topics = KafkaConfig.TOPIC_SCORES, groupId = "sscm-risk-detection")
    public void onScoreEvent(AnalyticsEvent<LinkedHashMap<String, Object>> event) {
        try {
            var payload = event.getPayload();
            Long studentId = toLong(payload.get("studentId"));
            Long schoolId = toLong(payload.get("schoolId"));
            Integer year = (Integer) payload.get("year");
            Integer semester = (Integer) payload.get("semester");

            if (studentId == null || schoolId == null || year == null || semester == null) {
                return;
            }

            checkScoreDrop(studentId, schoolId, year, semester);
        } catch (Exception e) {
            log.error("위험 감지 실패 (성적): {}", e.getMessage());
        }
    }

    // ── 피드백 이벤트 → 부정적 피드백 누적 감지 ──────────────────

    @KafkaListener(topics = KafkaConfig.TOPIC_FEEDBACKS, groupId = "sscm-risk-detection")
    public void onFeedbackEvent(AnalyticsEvent<LinkedHashMap<String, Object>> event) {
        try {
            var payload = event.getPayload();
            Long studentId = toLong(payload.get("studentId"));
            Long schoolId = toLong(payload.get("schoolId"));
            Integer year = (Integer) payload.get("year");
            Integer semester = (Integer) payload.get("semester");

            if (studentId == null || schoolId == null || year == null || semester == null) {
                return;
            }

            checkNegativeFeedback(studentId, schoolId, year, semester);
        } catch (Exception e) {
            log.error("위험 감지 실패 (피드백): {}", e.getMessage());
        }
    }

    // ── 성적 하락 검사 ──────────────────────────────────────────

    private void checkScoreDrop(Long studentId, Long schoolId, int year, int semester) {
        // 현재 학기 평균 점수 조회 (운영 DB의 scores 테이블에서 직접 계산)
        BigDecimal currentAvg = queryAvgScore(studentId, year, semester);
        if (currentAvg == null) return;

        // 이전 학기 평균 점수 조회
        int prevYear = semester == 1 ? year - 1 : year;
        int prevSemester = semester == 1 ? 2 : 1;
        BigDecimal prevAvg = queryAvgScore(studentId, prevYear, prevSemester);
        if (prevAvg == null) return;  // 이전 학기 데이터가 없으면 비교 불가

        // 하락폭 계산
        double drop = prevAvg.doubleValue() - currentAvg.doubleValue();
        if (drop < SCORE_DROP_THRESHOLD) return;

        log.info("성적 하락 감지: studentId={}, 이전={}점 → 현재={}점 (−{}점)",
                studentId, prevAvg, currentAvg, String.format("%.1f", drop));

        // 쿨다운 확인 (7일 이내 동일 알림 발송 이력이 있으면 스킵)
        if (isInCooldown(studentId, "SCORE_DROP")) {
            log.debug("쿨다운 중: studentId={}, ruleType=SCORE_DROP", studentId);
            return;
        }

        // 해당 학교의 교사 목록에게 알림 발송
        sendAlertToTeachers(studentId, schoolId, "SCORE_DROP",
                String.format("학생(ID:%d)의 평균 성적이 이전 학기 대비 %.1f점 하락했습니다. (%.1f → %.1f)",
                        studentId, drop, prevAvg.doubleValue(), currentAvg.doubleValue()));
    }

    // ── 부정적 피드백 누적 검사 ────────────────────────────────

    private void checkNegativeFeedback(Long studentId, Long schoolId, int year, int semester) {
        // 행동(BEHAVIOR) + 태도(ATTITUDE) 카테고리 피드백 건수 조회
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedbacks " +
                "WHERE student_id = ? AND year = ? AND semester = ? " +
                "AND category IN ('BEHAVIOR', 'ATTITUDE')",
                Integer.class, studentId, year, semester);

        if (count == null || count < NEGATIVE_FEEDBACK_THRESHOLD) return;

        log.info("부정적 피드백 누적 감지: studentId={}, count={}", studentId, count);

        if (isInCooldown(studentId, "NEGATIVE_FEEDBACK")) {
            log.debug("쿨다운 중: studentId={}, ruleType=NEGATIVE_FEEDBACK", studentId);
            return;
        }

        sendAlertToTeachers(studentId, schoolId, "NEGATIVE_FEEDBACK",
                String.format("학생(ID:%d)에게 행동/태도 관련 피드백이 %d건 누적되었습니다. 상담이 필요할 수 있습니다.",
                        studentId, count));
    }

    // ── 공통 헬퍼 메서드 ─────────────────────────────────────────

    private BigDecimal queryAvgScore(Long studentId, int year, int semester) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT AVG(score) FROM scores WHERE student_id = ? AND year = ? AND semester = ?",
                    BigDecimal.class, studentId, year, semester);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isInCooldown(Long studentId, String ruleType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_alert_history " +
                "WHERE student_id = ? AND rule_type = ? " +
                "AND alerted_at > NOW() - INTERVAL '" + COOLDOWN_DAYS + " days'",
                Integer.class, studentId, ruleType);
        return count != null && count > 0;
    }

    private void sendAlertToTeachers(Long studentId, Long schoolId,
                                     String ruleType, String message) {
        // 해당 학교 교사들의 user_id 조회
        List<Long> teacherUserIds = jdbcTemplate.queryForList(
                "SELECT u.id FROM users u " +
                "JOIN teachers t ON t.user_id = u.id " +
                "WHERE u.school_id = ?",
                Long.class, schoolId);

        if (teacherUserIds.isEmpty()) {
            log.warn("알림을 보낼 교사가 없음: schoolId={}", schoolId);
            return;
        }

        // 억제된 교사 필터링
        List<Long> recipients = teacherUserIds.stream()
                .filter(teacherId -> !suppressionService.isSuppressed(teacherId, studentId, ruleType))
                .toList();

        if (recipients.isEmpty()) {
            log.debug("모든 교사가 알림을 억제함: studentId={}, ruleType={}", studentId, ruleType);
            return;
        }

        // Spring ApplicationEvent로 알림 발행 (기존 NotificationEventListener가 처리)
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientIds(recipients)
                .type(NotificationType.SYSTEM)
                .title("[위험 감지] " + ruleType)
                .message(message)
                .referenceType(NotificationReferenceType.SCORE)
                .referenceId(studentId)
                .build());

        // 알림 이력 기록 (쿨다운 용)
        jdbcTemplate.update(
                "INSERT INTO risk_alert_history (student_id, school_id, rule_type, detail) VALUES (?, ?, ?, ?)",
                studentId, schoolId, ruleType, message);

        log.info("위험 알림 발송 완료: studentId={}, ruleType={}, recipients={}", studentId, ruleType, recipients.size());
    }

    private Long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }
}
