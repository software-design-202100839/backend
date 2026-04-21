package com.sscm.counsel.controller;

import com.sscm.common.response.ApiResponse;
import com.sscm.counsel.dto.CounselingRequest;
import com.sscm.counsel.dto.CounselingResponse;
import com.sscm.counsel.dto.CounselingUpdateRequest;
import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.service.CounselingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Counseling", description = "상담 내역 관리 API")
@RestController
@RequestMapping("/api/v1/counselings")
@RequiredArgsConstructor
public class CounselingController {

    private final CounselingService counselingService;

    @Operation(summary = "상담 내역 등록")
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<CounselingResponse>> createCounseling(
            @Valid @RequestBody CounselingRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CounselingResponse response = counselingService.createCounseling(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "상담 내역 수정 — 작성자만 가능")
    @PutMapping("/{counselingId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<CounselingResponse>> updateCounseling(
            @PathVariable Long counselingId,
            @Valid @RequestBody CounselingUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CounselingResponse response = counselingService.updateCounseling(counselingId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "상담 내역 단건 조회")
    @GetMapping("/{counselingId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CounselingResponse>> getCounseling(
            @PathVariable Long counselingId) {
        CounselingResponse response = counselingService.getCounseling(counselingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "학생별 상담 내역 조회")
    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> getCounselingsByStudent(
            @PathVariable Long studentId,
            @RequestParam(required = false) CounselCategory category) {
        List<CounselingResponse> responses = counselingService.getCounselingsByStudent(studentId, category);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "내 상담 내역 조회")
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> getMyCounselings(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<CounselingResponse> responses = counselingService.getMyCounselings(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "상담 내역 기간 검색")
    @GetMapping("/students/{studentId}/search")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> searchCounselings(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<CounselingResponse> responses = counselingService.searchCounselings(studentId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
