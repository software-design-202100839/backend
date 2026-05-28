package com.sscm.analytics.chatbot.dto;

import lombok.Getter;

@Getter
public class ChatResponse {
    private final String answer;
    private final String sessionId;
    private final Long reportId;

    public ChatResponse(String answer, String sessionId, Long reportId) {
        this.answer = answer;
        this.sessionId = sessionId;
        this.reportId = reportId;
    }

    public ChatResponse(String answer, String sessionId) {
        this(answer, sessionId, null);
    }

    public ChatResponse(String answer) {
        this(answer, null, null);
    }
}
