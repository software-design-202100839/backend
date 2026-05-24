package com.sscm.analytics.event;

import com.sscm.analytics.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Spring 내부 이벤트를 Kafka로 전송하는 다리 (Bridge).
 *
 * 흐름:
 * 1. 도메인 서비스가 Spring ApplicationEvent를 발행 (예: ScoreChangedEvent)
 * 2. 이 클래스의 @EventListener가 이벤트를 수신
 * 3. AnalyticsEvent 봉투로 감싸서 KafkaTemplate으로 Kafka 토픽에 전송
 *
 * @Async: 비동기 실행 → Kafka 전송이 느리거나 실패해도 도메인 서비스에 영향 없음
 *
 * Kafka 메시지의 Key = studentId (문자열)
 * → 같은 학생의 이벤트는 같은 파티션에 들어가서 순서 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsEventBridge {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async
    @EventListener
    public void onScoreChanged(ScoreChangedEvent event) {
        String key = String.valueOf(event.getPayload().getStudentId());
        AnalyticsEvent<?> analyticsEvent = AnalyticsEvent.of(
                "SCORE_" + event.getAction(), event.getPayload());

        kafkaTemplate.send(KafkaConfig.TOPIC_SCORES, key, analyticsEvent);
        log.debug("Kafka 전송: topic={}, key={}, type={}",
                KafkaConfig.TOPIC_SCORES, key, analyticsEvent.getEventType());
    }

    @Async
    @EventListener
    public void onFeedbackChanged(FeedbackChangedEvent event) {
        String key = String.valueOf(event.getPayload().getStudentId());
        AnalyticsEvent<?> analyticsEvent = AnalyticsEvent.of(
                "FEEDBACK_" + event.getAction(), event.getPayload());

        kafkaTemplate.send(KafkaConfig.TOPIC_FEEDBACKS, key, analyticsEvent);
        log.debug("Kafka 전송: topic={}, key={}, type={}",
                KafkaConfig.TOPIC_FEEDBACKS, key, analyticsEvent.getEventType());
    }

    @Async
    @EventListener
    public void onRecordChanged(RecordChangedEvent event) {
        String key = String.valueOf(event.getPayload().getStudentId());
        AnalyticsEvent<?> analyticsEvent = AnalyticsEvent.of(
                "RECORD_" + event.getAction(), event.getPayload());

        kafkaTemplate.send(KafkaConfig.TOPIC_RECORDS, key, analyticsEvent);
        log.debug("Kafka 전송: topic={}, key={}, type={}",
                KafkaConfig.TOPIC_RECORDS, key, analyticsEvent.getEventType());
    }

    @Async
    @EventListener
    public void onCounselingChanged(CounselingChangedEvent event) {
        String key = String.valueOf(event.getPayload().getStudentId());
        AnalyticsEvent<?> analyticsEvent = AnalyticsEvent.of(
                "COUNSELING_" + event.getAction(), event.getPayload());

        kafkaTemplate.send(KafkaConfig.TOPIC_COUNSELINGS, key, analyticsEvent);
        log.debug("Kafka 전송: topic={}, key={}, type={}",
                KafkaConfig.TOPIC_COUNSELINGS, key, analyticsEvent.getEventType());
    }
}
