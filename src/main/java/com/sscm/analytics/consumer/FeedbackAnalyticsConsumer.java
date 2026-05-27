package com.sscm.analytics.consumer;

import com.sscm.analytics.config.KafkaConfig;
import com.sscm.analytics.event.AnalyticsEvent;
import com.sscm.analytics.repository.AnalyticsJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/**
 * 피드백 이벤트 Kafka Consumer.
 *
 * 하는 일:
 * 1. 해당 학생의 피드백 카테고리별 건수 재집계 (student_feedback_summary)
 * 2. 해당 학생의 종합 대시보드 갱신 (student_learning_dashboard)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackAnalyticsConsumer {

    private final AnalyticsJdbcRepository analyticsRepo;

    @KafkaListener(topics = KafkaConfig.TOPIC_FEEDBACKS, groupId = "sscm-analytics")
    public void consume(AnalyticsEvent<LinkedHashMap<String, Object>> event) {
        try {
            log.info("피드백 이벤트 수신: type={}", event.getEventType());

            var payload = event.getPayload();
            Long studentId = toLong(payload.get("studentId"));
            Long schoolId = toLong(payload.get("schoolId"));
            Integer year = (Integer) payload.get("year");
            Integer semester = (Integer) payload.get("semester");

            analyticsRepo.upsertStudentFeedbackSummary(studentId, year, semester, schoolId);
            analyticsRepo.upsertStudentDashboard(studentId, year, semester, schoolId);

            log.info("피드백 분석 완료: studentId={}", studentId);
        } catch (Exception e) {
            log.error("피드백 이벤트 처리 실패: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }
}
