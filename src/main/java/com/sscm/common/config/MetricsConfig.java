package com.sscm.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 비즈니스 메트릭 정의.
 *
 * Micrometer Counter/Gauge를 빈으로 등록하여
 * 서비스 계층에서 주입받아 사용.
 *
 * Prometheus가 /actuator/prometheus에서 자동 수집 → Grafana에서 시각화.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter scoreCreateCounter(MeterRegistry registry) {
        return Counter.builder("sscm.score.created")
                .description("성적 등록 건수")
                .tag("domain", "grade")
                .register(registry);
    }

    @Bean
    public Counter feedbackCreateCounter(MeterRegistry registry) {
        return Counter.builder("sscm.feedback.created")
                .description("피드백 등록 건수")
                .tag("domain", "feedback")
                .register(registry);
    }

    @Bean
    public Counter counselingCreateCounter(MeterRegistry registry) {
        return Counter.builder("sscm.counseling.created")
                .description("상담 등록 건수")
                .tag("domain", "counseling")
                .register(registry);
    }

    @Bean
    public Counter loginSuccessCounter(MeterRegistry registry) {
        return Counter.builder("sscm.auth.login")
                .description("로그인 시도")
                .tag("result", "success")
                .register(registry);
    }

    @Bean
    public Counter loginFailureCounter(MeterRegistry registry) {
        return Counter.builder("sscm.auth.login")
                .description("로그인 시도")
                .tag("result", "failure")
                .register(registry);
    }

    @Bean
    public Counter kafkaEventCounter(MeterRegistry registry) {
        return Counter.builder("sscm.kafka.event.published")
                .description("Kafka 이벤트 발행 건수")
                .tag("domain", "analytics")
                .register(registry);
    }

    @Bean
    public Counter redisCacheHitCounter(MeterRegistry registry) {
        return Counter.builder("sscm.redis.cache.hit")
                .description("Redis 캐시 히트 건수")
                .tag("domain", "auth")
                .register(registry);
    }

    @Bean
    public Counter redisCacheMissCounter(MeterRegistry registry) {
        return Counter.builder("sscm.redis.cache.miss")
                .description("Redis 캐시 미스 건수")
                .tag("domain", "auth")
                .register(registry);
    }
}
