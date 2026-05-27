package com.sscm.analytics.consumer;

import com.sscm.analytics.config.KafkaConfig;
import com.sscm.analytics.event.AnalyticsEvent;
import com.sscm.analytics.repository.AnalyticsJdbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=sscm-analytics-test",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.base-url=http://localhost:9999"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaConfig.TOPIC_SCORES, KafkaConfig.TOPIC_FEEDBACKS,
                KafkaConfig.TOPIC_RECORDS, KafkaConfig.TOPIC_COUNSELINGS},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"}
)
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Kafka Consumer 통합 테스트")
@org.junit.jupiter.api.Disabled("통합 테스트: 로컬 Docker 환경에서만 실행 (Kafka + Analytics DB 필요)")
class KafkaIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public AnalyticsJdbcRepository mockAnalyticsRepository() {
            return mock(AnalyticsJdbcRepository.class);
        }
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AnalyticsJdbcRepository analyticsRepo;

    @Test
    @DisplayName("Kafka에 성적 이벤트 발행 → Consumer가 수신하여 집계 메서드 호출")
    void scoreEvent_consumedAndProcessed() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentId", 5);
        payload.put("subjectId", 1);
        payload.put("year", 2026);
        payload.put("semester", 1);
        payload.put("schoolId", 1);

        AnalyticsEvent<LinkedHashMap<String, Object>> event =
                new AnalyticsEvent<>("SCORE_CREATED", LocalDateTime.now(), payload);

        kafkaTemplate.send(KafkaConfig.TOPIC_SCORES, "5", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(analyticsRepo, atLeastOnce()).upsertStudentScoreSummary(5L, 2026, 1, 1L);
            verify(analyticsRepo, atLeastOnce()).upsertSubjectStatistics(1L, 2026, 1, 1L);
            verify(analyticsRepo, atLeastOnce()).upsertStudentDashboard(5L, 2026, 1, 1L);
        });
    }
}
