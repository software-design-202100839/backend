package com.sscm.analytics.chatbot.controller;

import com.sscm.analytics.chatbot.dto.ChatRequest;
import com.sscm.analytics.chatbot.dto.ChatResponse;
import com.sscm.analytics.chatbot.service.ChatbotService;
import com.sscm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AI 챗봇 API.
 *
 * 교사와 관리자만 사용 가능.
 * 학생/학부모는 프롬프트 조작으로 다른 학생 데이터를 유출할 수 있어서 차단.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatbotService.chat(request.getQuestion()));
    }
}
