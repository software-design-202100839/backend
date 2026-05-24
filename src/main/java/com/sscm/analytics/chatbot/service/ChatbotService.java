package com.sscm.analytics.chatbot.service;

import com.sscm.analytics.chatbot.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * AI 챗봇 서비스.
 *
 * 흐름:
 * 1. 사용자가 자연어 질문을 보냄
 * 2. 시스템 프롬프트 + 사용자 질문을 Claude에게 전달
 * 3. Claude가 필요한 Tool(함수)을 선택하여 호출 요청
 * 4. Spring AI가 Tool을 실행하고 결과를 Claude에게 반환
 * 5. Claude가 Tool 결과를 바탕으로 자연어 응답 생성
 *
 * tools() 설정:
 * - AnalyticsTools에서 @Bean으로 등록한 Function들의 이름을 지정
 * - Claude는 이 함수들 중에서 질문에 맞는 것을 선택하여 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatModel chatModel;

    private static final String SYSTEM_PROMPT = """
            당신은 SSCM(학생 성적 및 상담 관리 시스템)의 교육 분석 AI 어시스턴트입니다.

            역할:
            - 교사가 학생의 학습 현황에 대해 질문하면, 제공된 도구를 사용하여 데이터를 조회하고 분석합니다.
            - 데이터에 기반한 객관적인 답변을 제공합니다.
            - 학생의 강점, 약점, 개선 방향을 제안할 수 있습니다.

            규칙:
            - 반드시 도구를 사용하여 실제 데이터를 조회한 후 답변하세요.
            - 데이터가 없는 경우 "해당 데이터가 없습니다"라고 답변하세요.
            - 추측이나 가정으로 답변하지 마세요.
            - 한국어로 답변하세요.
            - 답변은 간결하면서도 유용한 인사이트를 포함하세요.

            현재 시스템의 데이터:
            - 성적: 과목별 점수, 등급, 석차
            - 출결/기록: 출결, 수상, 봉사, 세특, 종합의견
            - 피드백: 학업, 행동, 출결, 태도, 일반 카테고리
            - 상담: 학업, 진로, 행동, 개인, 기타 카테고리
            """;

    public ChatResponse chat(String question) {
        log.info("AI 챗봇 질문: {}", question);

        try {
            String answer = ChatClient.create(chatModel)
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(question)
                    .functions(
                            "getStudentDashboard",
                            "getStudentScoreSummary",
                            "getStudentScoreTrend",
                            "getStudentFeedbackSummary",
                            "getStudentCounselingSummary",
                            "getAllSubjectStatistics"
                    )
                    .call()
                    .content();

            log.info("AI 챗봇 응답 완료");
            return new ChatResponse(answer);
        } catch (Exception e) {
            log.error("AI 챗봇 에러: {}", e.getMessage(), e);
            return new ChatResponse("AI 서비스 오류: " + e.getMessage());
        }
    }
}
