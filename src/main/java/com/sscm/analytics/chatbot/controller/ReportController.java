package com.sscm.analytics.chatbot.controller;

import com.sscm.analytics.chatbot.service.ReportEditService;
import com.sscm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * AI 생성 보고서 Human-in-the-Loop 컨트롤러.
 *
 * 교사가 AI 초안을 수정하여 최종 의견서로 확정하는 엔드포인트를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/analytics/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportEditService reportEditService;

    /**
     * AI 생성 보고서를 교사가 수정한 최종본을 저장한다.
     *
     * @param reportId 보고서 ID
     * @param request  수정된 텍스트
     */
    @PostMapping("/{reportId}/edit")
    public ApiResponse<Void> editReport(
            @PathVariable Long reportId,
            @RequestBody ReportEditRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        reportEditService.saveEdit(reportId, request.finalText(), userId);
        return ApiResponse.success("보고서 수정이 저장되었습니다.");
    }

    record ReportEditRequest(String finalText) {}
}
