package com.sscm.analytics.controller;

import com.sscm.analytics.service.AnalyticsDataLoader;
import com.sscm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 분석 관리자 API.
 * ADMIN만 접근 가능.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsAdminController {

    private final AnalyticsDataLoader dataLoader;
    private final KafkaListenerEndpointRegistry kafkaRegistry;

    // 전체 Analytics Consumer ID 목록
    private static final List<String> ANALYTICS_CONSUMERS = List.of(
            "score-analytics", "feedback-analytics", "counseling-analytics", "record-analytics",
            "risk-score", "risk-feedback"
    );

    /**
     * 기존 운영 데이터를 분석 DB에 일괄 적재 (backfill).
     * OLAP 도입 초기 또는 분석 DB 초기화 후 사용.
     */
    @PostMapping("/backfill")
    public ApiResponse<Void> backfill() {
        dataLoader.backfillAll();
        return ApiResponse.success("Backfill 완료");
    }

    /**
     * Kafka Consumer 일시 중단.
     * Kafka 장애 격리 테스트용: Consumer가 중단되어도 운영 API(성적 입력 등)는 정상 동작함을 검증.
     */
    @PostMapping("/consumers/pause")
    public ApiResponse<Map<String, String>> pauseConsumers() {
        Map<String, String> status = new LinkedHashMap<>();
        for (String id : ANALYTICS_CONSUMERS) {
            MessageListenerContainer container = kafkaRegistry.getListenerContainer(id);
            if (container != null && container.isRunning()) {
                container.pause();
                status.put(id, "paused");
                log.info("[KAFKA] Consumer 중단: {}", id);
            } else {
                status.put(id, container == null ? "not_found" : "already_paused");
            }
        }
        return ApiResponse.success(status);
    }

    /**
     * Kafka Consumer 재개.
     * 중단 기간 동안 Kafka에 쌓인 이벤트를 Consumer가 처리하여 분석 DB가 따라잡음을 검증.
     */
    @PostMapping("/consumers/resume")
    public ApiResponse<Map<String, String>> resumeConsumers() {
        Map<String, String> status = new LinkedHashMap<>();
        for (String id : ANALYTICS_CONSUMERS) {
            MessageListenerContainer container = kafkaRegistry.getListenerContainer(id);
            if (container != null && container.isPauseRequested()) {
                container.resume();
                status.put(id, "resumed");
                log.info("[KAFKA] Consumer 재개: {}", id);
            } else {
                status.put(id, container == null ? "not_found" : "already_running");
            }
        }
        return ApiResponse.success(status);
    }

    /**
     * Kafka Consumer 상태 조회.
     */
    @GetMapping("/consumers/status")
    public ApiResponse<Map<String, Object>> consumerStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        for (String id : ANALYTICS_CONSUMERS) {
            MessageListenerContainer container = kafkaRegistry.getListenerContainer(id);
            if (container != null) {
                status.put(id, Map.of(
                        "running", container.isRunning(),
                        "paused", container.isPauseRequested()
                ));
            } else {
                status.put(id, "not_found");
            }
        }
        return ApiResponse.success(status);
    }
}
