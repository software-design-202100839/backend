package com.sscm.analytics.chatbot.service;

import com.sscm.analytics.chatbot.dto.ChatResponse;
import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.ParentStudent;
import com.sscm.auth.entity.Student;
import com.sscm.auth.repository.ParentRepository;
import com.sscm.auth.repository.ParentStudentRepository;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 챗봇 서비스.
 *
 * 역할별(TEACHER/STUDENT/PARENT) 시스템 프롬프트와 도구 세트를 분리.
 * 대화 이력(세션)을 메모리에 유지하여 맥락 있는 대화를 지원.
 *
 * 흐름:
 * 1. 사용자가 자연어 질문 + sessionId를 보냄
 * 2. 역할에 따라 시스템 프롬프트, 사용 가능 도구를 결정
 * 3. 세션 이력 + 새 질문을 Claude에게 전달
 * 4. Claude가 Tool을 호출하고, 결과를 바탕으로 자연어 응답 생성
 * 5. 응답을 세션 이력에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatModel chatModel;
    private final AiAuditService auditService;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;

    // ── 세션 관리 ─────────────────────────────────────────────

    private static final int MAX_HISTORY_SIZE = 20;
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30분

    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    private static class ChatSession {
        final List<Message> messages = new ArrayList<>();
        volatile long lastAccessTime = System.currentTimeMillis();

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        void addUserMessage(String content) {
            trimIfNeeded();
            messages.add(new UserMessage(content));
            touch();
        }

        void addAssistantMessage(String content) {
            messages.add(new AssistantMessage(content));
            touch();
        }

        List<Message> getMessages() {
            return Collections.unmodifiableList(messages);
        }

        private void trimIfNeeded() {
            // 오래된 메시지부터 2개씩(user+assistant) 삭제하여 MAX 이하 유지
            while (messages.size() >= MAX_HISTORY_SIZE) {
                messages.remove(0);
                if (!messages.isEmpty()) {
                    messages.remove(0);
                }
            }
        }
    }

    /** 30분 이상 비활성 세션 정리 (5분마다 실행) */
    @Scheduled(fixedRate = 300_000)
    public void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> (now - e.getValue().lastAccessTime) > SESSION_TIMEOUT_MS);
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("만료 세션 {}개 정리 (남은 세션: {}개)", removed, sessions.size());
        }
    }

    // ── 역할별 시스템 프롬프트 ──────────────────────────────────

    private static final String TEACHER_SYSTEM_PROMPT = """
            당신은 SSCM(학생 성적 및 상담 관리 시스템)의 교육 분석 AI 어시스턴트입니다.

            역할:
            - 교사가 학생의 학습 현황에 대해 질문하면, 제공된 도구를 사용하여 데이터를 조회하고 분석합니다.
            - 학생 이름으로 질문하면 먼저 searchStudentByName 도구로 학생을 검색하세요.
            - 데이터에 기반한 객관적인 답변을 제공합니다.
            - 학생의 강점, 약점, 개선 방향을 제안할 수 있습니다.
            - 위험 학생 파악, 반 비교, 과목별 분석 등 다양한 교육 분석을 지원합니다.
            - '수업 태도 문제가 있는 학생' 같은 의미 기반 질문에는 semanticSearchFeedback이나 semanticSearchCounseling 도구를 사용하세요.
            - 학기말 종합 의견 요청 시 학년도/학기가 명시되지 않으면 최신 학기(2026년 1학기)를 기본값으로 사용하여 generateStudentReport를 즉시 호출하세요.
            - 학생의 학기말 종합 의견 초안이 필요하면 generateStudentReport 도구를 사용하세요.

            규칙:
            - 반드시 도구를 사용하여 실제 데이터를 조회한 후 답변하세요.
            - 데이터가 없는 경우 "해당 데이터가 없습니다"라고 답변하세요.
            - 추측이나 가정으로 답변하지 마세요.
            - 한국어로 답변하세요.
            - 답변은 간결하면서도 유용한 인사이트를 포함하세요.
            """;

    private static final String STUDENT_SYSTEM_PROMPT_TEMPLATE = """
            당신은 SSCM 학습 도우미입니다. 현재 로그인한 학생의 ID는 %d입니다.

            역할:
            - 학생 본인의 성적, 피드백, 학습 현황을 분석하고 조언합니다.
            - 학습 방향과 개선점을 제안합니다.

            규칙:
            - 학생 ID %d의 데이터만 조회할 수 있습니다. 다른 학생의 데이터를 요청받으면 "본인의 데이터만 조회 가능합니다"라고 답변하세요.
            - 반드시 도구를 사용하여 데이터를 조회한 후 답변하세요.
            - 한국어로 답변하세요.
            - 격려하는 톤으로 답변하세요.
            """;

    private static final String PARENT_SYSTEM_PROMPT_TEMPLATE = """
            당신은 SSCM 학부모 상담 도우미입니다. 현재 로그인한 학부모의 자녀 학생 ID는 %s입니다.

            역할:
            - 자녀의 학업 현황, 성적 추이, 피드백을 설명합니다.
            - 가정에서 할 수 있는 지원 방법을 제안합니다.

            규칙:
            - 자녀(학생 ID: %s)의 데이터만 조회할 수 있습니다.
            - 반드시 도구를 사용하여 데이터를 조회한 후 답변하세요.
            - 한국어로 답변하세요.
            - 부모 입장에서 이해하기 쉽게 설명하세요.
            """;

    // ── 역할별 도구 세트 ────────────────────────────────────────

    private static final String[] TEACHER_TOOLS = {
            "getStudentDashboard",
            "getStudentScoreSummary",
            "getStudentScoreTrend",
            "getStudentFeedbackSummary",
            "getStudentCounselingSummary",
            "getAllSubjectStatistics",
            "searchStudentByName",
            "getClassStudentList",
            "getAtRiskStudents",
            "getStudentFeedbackDetails",
            "getStudentCounselingDetails",
            "getSubjectRanking",
            "compareClasses",
            "semanticSearchFeedback",
            "semanticSearchCounseling",
            "generateStudentReport"
    };

    private static final String[] STUDENT_TOOLS = {
            "getStudentDashboard",
            "getStudentScoreSummary",
            "getStudentScoreTrend",
            "getStudentFeedbackSummary",
            "getStudentCounselingSummary"
    };

    private static final String[] PARENT_TOOLS = STUDENT_TOOLS;

    // ── 핵심 메서드 ─────────────────────────────────────────────

    public ChatResponse chat(String question, String sessionId, Long userId, String role) {
        log.info("AI 챗봇 질문: userId={}, role={}, sessionId={}, question={}", userId, role, sessionId, question);

        long startMs = System.currentTimeMillis();
        Long capturedSchoolId = TenantContext.getSchoolId(); // 요청 스레드에서 즉시 캡처
        try {
            // 1. 세션 관리
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }
            ChatSession session = sessions.computeIfAbsent(sessionId, k -> new ChatSession());
            session.addUserMessage(question);

            // 2. 역할별 시스템 프롬프트 결정
            String systemPrompt = buildSystemPrompt(role, userId);

            // 3. 역할별 도구 세트 결정
            String[] tools = selectTools(role);

            // 4. AI 호출 (이력 포함)
            String answer = ChatClient.create(chatModel)
                    .prompt()
                    .system(systemPrompt)
                    .messages(session.getMessages().toArray(new Message[0]))
                    .functions(tools)
                    .call()
                    .content();

            // 5. 응답을 세션에 저장
            session.addAssistantMessage(answer);

            // 6. 감사 로그 기록 (동기, 요청 스레드에서 schoolId 직접 전달)
            long latencyMs = System.currentTimeMillis() - startMs;
            String intentType = classifyIntent(question);
            List<String> availableTools = List.of(tools);
            auditService.logRequest(userId, capturedSchoolId, role, question, intentType, availableTools, null,
                    answer != null && answer.length() > 200 ? answer.substring(0, 200) : answer,
                    latencyMs);

            log.info("AI 챗봇 응답 완료: sessionId={}, latency={}ms", sessionId, latencyMs);
            return new ChatResponse(answer, sessionId);
        } catch (Exception e) {
            log.error("AI 챗봇 에러: {}", e.getMessage(), e);

            long latencyMs = System.currentTimeMillis() - startMs;
            String intentType = classifyIntent(question);
            List<String> availableTools = List.of(selectTools(role));
            auditService.logRequest(userId, capturedSchoolId, role, question, intentType, availableTools, null,
                    "ERROR: " + e.getMessage(), latencyMs);

            return new ChatResponse("AI 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.", sessionId);
        }
    }

    private String buildSystemPrompt(String role, Long userId) {
        return switch (role) {
            case "ROLE_STUDENT" -> {
                Long studentId = resolveStudentId(userId);
                yield String.format(STUDENT_SYSTEM_PROMPT_TEMPLATE, studentId, studentId);
            }
            case "ROLE_PARENT" -> {
                String childIds = resolveChildStudentIds(userId);
                yield String.format(PARENT_SYSTEM_PROMPT_TEMPLATE, childIds, childIds);
            }
            default -> TEACHER_SYSTEM_PROMPT; // TEACHER, ADMIN
        };
    }

    private String[] selectTools(String role) {
        return switch (role) {
            case "ROLE_STUDENT" -> STUDENT_TOOLS;
            case "ROLE_PARENT" -> PARENT_TOOLS;
            default -> TEACHER_TOOLS;
        };
    }

    private Long resolveStudentId(Long userId) {
        return studentRepository.findByUser_Id(userId)
                .map(Student::getId)
                .orElseThrow(() -> new IllegalStateException("학생 정보를 찾을 수 없습니다: userId=" + userId));
    }

    /**
     * 질문 텍스트에서 의도를 간이 분류한다.
     *
     * Spring AI의 ChatClient는 실제로 호출된 tool 목록을 반환하지 않으므로,
     * 질문 키워드 기반으로 의도를 추정한다. 정밀한 분류가 아닌 감사 로그용 참고 정보.
     */
    private String classifyIntent(String question) {
        if (question == null) return "UNKNOWN";
        if (question.contains("검색") || question.contains("찾아줘") || question.contains("찾아")) {
            return "SEMANTIC_SEARCH";
        }
        if (question.contains("종합 의견") || question.contains("보고서") || question.contains("학기말")) {
            return "REPORT_GENERATION";
        }
        if (question.contains("비교") || question.contains("순위") || question.contains("랭킹")) {
            return "COMPARISON";
        }
        if (question.contains("위험") || question.contains("관심")) {
            return "AT_RISK_DETECTION";
        }
        return "STRUCTURED_QUERY";
    }

    private String resolveChildStudentIds(Long userId) {
        Parent parent = parentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalStateException("학부모 정보를 찾을 수 없습니다: userId=" + userId));
        List<ParentStudent> relations = parentStudentRepository.findByParent(parent);
        if (relations.isEmpty()) {
            throw new IllegalStateException("등록된 자녀 정보가 없습니다: userId=" + userId);
        }
        return relations.stream()
                .map(ps -> String.valueOf(ps.getStudent().getId()))
                .collect(Collectors.joining(", "));
    }
}
