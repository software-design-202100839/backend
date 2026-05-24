package com.sscm.analytics.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequest {
    @NotBlank(message = "질문을 입력해주세요")
    private String question;
}
