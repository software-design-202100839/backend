package com.sscm.analytics.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequest {
    @NotBlank(message = "질문을 입력해주세요")
    private String question;

    /** 대화 세션 ID. 첫 요청 시 null이면 서버가 생성하여 응답에 포함. */
    private String sessionId;
}
