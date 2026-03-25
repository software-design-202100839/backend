package com.sscm.notification.controller;

import com.sscm.common.response.ApiResponse;
import com.sscm.notification.dto.NotificationResponse;
import com.sscm.notification.dto.UnreadCountResponse;
import com.sscm.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notification", description = "알림 관리 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록 조회", description = "미읽은 알림 우선, 최신순 정렬")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<NotificationResponse> responses = notificationService.getMyNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "미읽은 알림만 조회")
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<NotificationResponse> responses = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "미읽은 알림 개수 조회")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UnreadCountResponse response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        NotificationResponse response = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "모든 알림 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("모든 알림을 읽음 처리했습니다"));
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success("알림이 삭제되었습니다"));
    }
}
