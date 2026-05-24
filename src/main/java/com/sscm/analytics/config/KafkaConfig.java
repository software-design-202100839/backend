package com.sscm.analytics.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 자동 생성 설정.
 *
 * Spring Boot가 시작될 때 Kafka에 토픽이 없으면 자동으로 생성한다.
 * 각 도메인(성적, 피드백, 학생부, 상담)별로 토픽 1개씩 = 총 4개.
 *
 * - partitions(3): 병렬 처리 통로 3개. 지금은 Consumer 1개지만 확장 여지.
 * - replicas(1): 복제본 1개 (로컬 단일 브로커이므로 1. 운영에서는 3 권장)
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_SCORES = "sscm.scores";
    public static final String TOPIC_FEEDBACKS = "sscm.feedbacks";
    public static final String TOPIC_RECORDS = "sscm.records";
    public static final String TOPIC_COUNSELINGS = "sscm.counselings";

    @Bean
    public NewTopic scoresTopic() {
        return TopicBuilder.name(TOPIC_SCORES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic feedbacksTopic() {
        return TopicBuilder.name(TOPIC_FEEDBACKS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic recordsTopic() {
        return TopicBuilder.name(TOPIC_RECORDS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic counselingsTopic() {
        return TopicBuilder.name(TOPIC_COUNSELINGS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
