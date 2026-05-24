package com.sscm.analytics.event;

import com.sscm.analytics.event.payload.CounselingEventPayload;
import lombok.Getter;

@Getter
public class CounselingChangedEvent {
    private final String action;
    private final CounselingEventPayload payload;

    public CounselingChangedEvent(String action, CounselingEventPayload payload) {
        this.action = action;
        this.payload = payload;
    }
}
