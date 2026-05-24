package com.sscm.analytics.event;

import com.sscm.analytics.event.payload.ScoreEventPayload;
import lombok.Getter;

/**
 * 성적이 변경되었을 때 발행되는 Spring 내부 이벤트.
 *
 * ScoreService → (이 이벤트 발행) → AnalyticsEventBridge가 수신 → Kafka로 전송
 *
 * Spring ApplicationEvent를 직접 상속하지 않고 단순 객체로 만들어도
 * ApplicationEventPublisher.publishEvent()에 넘길 수 있다. (Spring 4.2+)
 */
@Getter
public class ScoreChangedEvent {
    private final String action;    // "CREATED", "UPDATED", "DELETED"
    private final ScoreEventPayload payload;

    public ScoreChangedEvent(String action, ScoreEventPayload payload) {
        this.action = action;
        this.payload = payload;
    }
}
