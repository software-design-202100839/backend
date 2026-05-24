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
 * 상담 이벤트 Kafka Consumer.
 *
 * 하는 일:
 * 1. 해당 학생의 상담 카테고리별 건수 재집계 (student_counseling_summary)
 * 2. 해당 학생의 종합 대시보드 갱신 (student_learning_dashboard)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CounselingAnalyticsConsumer {

    private final AnalyticsJdbcRepository analyticsRepo;

    @KafkaListener(topics = KafkaConfig.TOPIC_COUNSELINGS, groupId = "sscm-analytics")
    public void consume(AnalyticsEvent<LinkedHashMap<String, Object>> event) {
        try {
            log.info("상담 이벤트 수신: type={}", event.getEventType());

            var payload = event.getPayload();
            Long studentId = toLong(payload.get("studentId"));
            // 상담은 year/semester가 payload에 없으므로 counselDate에서 추출
            String counselDateStr = (String) payload.get("counselDate");
            var counselDate = java.time.LocalDate.parse(counselDateStr);
            int year = counselDate.getYear();
            int semester = counselDate.getMonthValue() <= 8 ? 1 : 2;

            analyticsRepo.upsertStudentCounselingSummary(studentId, year, semester);
            analyticsRepo.upsertStudentDashboard(studentId, year, semester);

            log.info("상담 분석 완료: studentId={}", studentId);
        } catch (Exception e) {
            log.error("상담 이벤트 처리 실패: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }
}
