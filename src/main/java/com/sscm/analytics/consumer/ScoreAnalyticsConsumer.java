package com.sscm.analytics.consumer;

import com.sscm.analytics.config.KafkaConfig;
import com.sscm.analytics.event.AnalyticsEvent;
import com.sscm.analytics.event.payload.ScoreEventPayload;
import com.sscm.analytics.repository.AnalyticsJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/**
 * 성적 이벤트 Kafka Consumer.
 *
 * sscm.scores 토픽에서 메시지가 오면 자동으로 consume() 메서드가 호출된다.
 *
 * 하는 일:
 * 1. 이벤트에서 studentId, subjectId, year, semester 추출
 * 2. 해당 학생의 성적 요약 재집계 (student_score_summary)
 * 3. 해당 과목의 통계 재집계 (subject_statistics)
 * 4. 해당 학생의 종합 대시보드 갱신 (student_learning_dashboard)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreAnalyticsConsumer {

    private final AnalyticsJdbcRepository analyticsRepo;

    @KafkaListener(topics = KafkaConfig.TOPIC_SCORES, groupId = "sscm-analytics")
    public void consume(AnalyticsEvent<LinkedHashMap<String, Object>> event) {
        try {
            log.info("성적 이벤트 수신: type={}", event.getEventType());

            var payload = event.getPayload();
            Long studentId = toLong(payload.get("studentId"));
            Long subjectId = toLong(payload.get("subjectId"));
            Long schoolId = toLong(payload.get("schoolId"));
            Integer year = (Integer) payload.get("year");
            Integer semester = (Integer) payload.get("semester");

            // 학생 성적 요약 재집계
            analyticsRepo.upsertStudentScoreSummary(studentId, year, semester, schoolId);
            // 과목 통계 재집계
            analyticsRepo.upsertSubjectStatistics(subjectId, year, semester, schoolId);
            // 종합 대시보드 갱신
            analyticsRepo.upsertStudentDashboard(studentId, year, semester, schoolId);

            log.info("성적 분석 완료: studentId={}, subjectId={}", studentId, subjectId);
        } catch (Exception e) {
            log.error("성적 이벤트 처리 실패: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }
}
