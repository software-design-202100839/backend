package com.sscm.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sscm.analytics.chatbot.controller.ChatbotController;
import com.sscm.analytics.chatbot.dto.ChatResponse;
import com.sscm.analytics.chatbot.service.ChatbotService;
import com.sscm.auth.service.JwtTokenProvider;
import com.sscm.auth.service.TokenBlacklistService;
import com.sscm.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatbotController.class)
@Import(SecurityConfig.class)
@DisplayName("ChatbotController 테스트")
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatbotService chatbotService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static RequestPostProcessor teacher() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    private static RequestPostProcessor student() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "10", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    private static RequestPostProcessor parent() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "20", null, List.of(new SimpleGrantedAuthority("ROLE_PARENT"))));
    }

    @Test
    @DisplayName("교사가 채팅 요청 성공")
    void teacher_chatSuccess() throws Exception {
        ChatResponse response = new ChatResponse("김철수 학생은 평균 85점입니다.", "session-123");
        given(chatbotService.chat(eq("김철수 학생 성적 알려줘"), isNull(), eq(1L), eq("ROLE_TEACHER")))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/analytics/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "김철수 학생 성적 알려줘")))
                        .with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("김철수 학생은 평균 85점입니다."))
                .andExpect(jsonPath("$.data.sessionId").value("session-123"));
    }

    @Test
    @DisplayName("학생이 채팅 요청 성공")
    void student_chatSuccess() throws Exception {
        ChatResponse response = new ChatResponse("당신의 평균 점수는 90점입니다.", "session-456");
        given(chatbotService.chat(eq("내 성적 알려줘"), isNull(), eq(10L), eq("ROLE_STUDENT")))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/analytics/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "내 성적 알려줘")))
                        .with(student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("당신의 평균 점수는 90점입니다."));
    }

    @Test
    @DisplayName("학부모가 채팅 요청 성공")
    void parent_chatSuccess() throws Exception {
        ChatResponse response = new ChatResponse("자녀의 성적 추이가 상승 중입니다.", "session-789");
        given(chatbotService.chat(eq("우리 아이 성적 어때요?"), isNull(), eq(20L), eq("ROLE_PARENT")))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/analytics/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "우리 아이 성적 어때요?")))
                        .with(parent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("자녀의 성적 추이가 상승 중입니다."));
    }

    @Test
    @DisplayName("질문이 비어있으면 400")
    void emptyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "")))
                        .with(teacher()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없으면 401")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "테스트"))))
                .andExpect(status().isUnauthorized());
    }
}
