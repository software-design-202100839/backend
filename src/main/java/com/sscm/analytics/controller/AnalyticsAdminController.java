package com.sscm.analytics.controller;

import com.sscm.analytics.service.AnalyticsDataLoader;
import com.sscm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석 관리자 API.
 * ADMIN만 접근 가능.
 */
@RestController
@RequestMapping("/api/v1/analytics/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsAdminController {

    private final AnalyticsDataLoader dataLoader;

    /**
     * 기존 운영 데이터를 분석 DB에 일괄 적재 (backfill).
     * OLAP 도입 초기 또는 분석 DB 초기화 후 사용.
     */
    @PostMapping("/backfill")
    public ApiResponse<Void> backfill() {
        dataLoader.backfillAll();
        return ApiResponse.success("Backfill 완료");
    }
}
