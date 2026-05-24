package com.sscm.analytics.event;

import com.sscm.analytics.event.payload.FeedbackEventPayload;
import lombok.Getter;

@Getter
public class FeedbackChangedEvent {
    private final String action;
    private final FeedbackEventPayload payload;

    public FeedbackChangedEvent(String action, FeedbackEventPayload payload) {
        this.action = action;
        this.payload = payload;
    }
}
