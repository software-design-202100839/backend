package com.sscm.analytics.chatbot.service;

/**
 * generateStudentReport Tool 실행 후 생성된 reportId를
 * ChatbotService로 전달하기 위한 ThreadLocal 홀더.
 *
 * getAndClear()로 원자적으로 가져오고 제거하여 다음 요청에 영향을 주지 않음.
 */
public class ReportIdHolder {

    private static final ThreadLocal<Long> lastReportId = new ThreadLocal<>();

    public static void set(Long id) {
        lastReportId.set(id);
    }

    /** 값을 반환하고 즉시 제거. null이면 보고서 생성이 없었음. */
    public static Long getAndClear() {
        Long id = lastReportId.get();
        lastReportId.remove();
        return id;
    }

    public static void clear() {
        lastReportId.remove();
    }
}
