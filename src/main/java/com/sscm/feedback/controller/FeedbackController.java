package com.sscm.feedback.controller;

import com.sscm.common.response.ApiResponse;
import com.sscm.feedback.dto.FeedbackRequest;
import com.sscm.feedback.dto.FeedbackResponse;
import com.sscm.feedback.dto.FeedbackUpdateRequest;
import com.sscm.feedback.entity.FeedbackCategory;
import com.sscm.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Feedback", description = "피드백 관리 API")
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "피드백 작성", description = "교사가 학생에게 피드백을 작성한다")
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> createFeedback(
            @Valid @RequestBody FeedbackRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        FeedbackResponse response = feedbackService.createFeedback(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "피드백 수정", description = "작성한 교사만 피드백을 수정할 수 있다")
    @PutMapping("/{feedbackId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> updateFeedback(
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        FeedbackResponse response = feedbackService.updateFeedback(feedbackId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "피드백 삭제", description = "작성한 교사만 피드백을 삭제할 수 있다")
    @DeleteMapping("/{feedbackId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteFeedback(
            @PathVariable Long feedbackId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        feedbackService.deleteFeedback(feedbackId, userId);
        return ResponseEntity.ok(ApiResponse.success("피드백이 삭제되었습니다"));
    }

    @Operation(summary = "피드백 단건 조회")
    @GetMapping("/{feedbackId}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getFeedback(
            @PathVariable Long feedbackId) {
        FeedbackResponse response = feedbackService.getFeedback(feedbackId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "학생별 피드백 목록 조회", description = "교사가 학생의 전체 피드백을 조회한다. 카테고리 필터 가능")
    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getFeedbacksByStudent(
            @PathVariable Long studentId,
            @RequestParam(required = false) FeedbackCategory category) {
        List<FeedbackResponse> responses = feedbackService.getFeedbacksByStudent(studentId, category);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "학생 본인 피드백 조회", description = "학생이 자신에게 공개된 피드백을 조회한다")
    @GetMapping("/students/{studentId}/visible")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getVisibleFeedbacksForStudent(
            @PathVariable Long studentId) {
        List<FeedbackResponse> responses = feedbackService.getVisibleFeedbacksForStudent(studentId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "학부모 피드백 조회", description = "학부모가 자녀에게 공개된 피드백을 조회한다")
    @GetMapping("/students/{studentId}/parent")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getVisibleFeedbacksForParent(
            @PathVariable Long studentId) {
        List<FeedbackResponse> responses = feedbackService.getVisibleFeedbacksForParent(studentId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
