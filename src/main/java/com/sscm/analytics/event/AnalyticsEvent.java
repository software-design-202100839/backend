package com.sscm.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka로 전송되는 분석 이벤트의 봉투 (envelope).
 *
 * 모든 도메인 이벤트를 이 형태로 감싸서 Kafka에 보낸다.
 * Consumer는 eventType을 보고 어떤 처리를 할지 결정한다.
 *
 * @param <T> payload 타입 (ScoreEventPayload, FeedbackEventPayload 등)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent<T> {
    private String eventType;       // 예: "SCORE_CREATED", "SCORE_UPDATED", "SCORE_DELETED"
    private LocalDateTime timestamp;
    private T payload;

    public static <T> AnalyticsEvent<T> of(String eventType, T payload) {
        return new AnalyticsEvent<>(eventType, LocalDateTime.now(), payload);
    }
}
