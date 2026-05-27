package com.sscm.analytics.chatbot.controller;

import com.sscm.analytics.chatbot.dto.ChatRequest;
import com.sscm.analytics.chatbot.dto.ChatResponse;
import com.sscm.analytics.chatbot.service.ChatbotService;
import com.sscm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * AI 챗봇 API.
 *
 * 모든 인증된 사용자가 사용 가능.
 * 역할(TEACHER/STUDENT/PARENT)에 따라 시스템 프롬프트와 사용 가능 도구가 달라진다.
 * - TEACHER/ADMIN: 전체 도구, 모든 학생 데이터 접근 가능
 * - STUDENT: 본인 데이터만 조회 가능
 * - PARENT: 자녀 데이터만 조회 가능
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                          Authentication authentication) {
        Long userId = Long.valueOf(authentication.getPrincipal().toString());
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        return ApiResponse.success(
                chatbotService.chat(request.getQuestion(), request.getSessionId(), userId, role));
    }
}
