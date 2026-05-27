package com.sscm.analytics.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private String sessionId;

    /** 하위 호환: sessionId 없이 생성 (에러 응답 등) */
    public ChatResponse(String answer) {
        this.answer = answer;
        this.sessionId = null;
    }
}
