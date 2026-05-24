package com.sscm.analytics.event;

import com.sscm.analytics.event.payload.RecordEventPayload;
import lombok.Getter;

@Getter
public class RecordChangedEvent {
    private final String action;
    private final RecordEventPayload payload;

    public RecordChangedEvent(String action, RecordEventPayload payload) {
        this.action = action;
        this.payload = payload;
    }
}
