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

    @Operation(summary = "상담 내역 등록", description = "교사가 학생과의 상담 내역을 등록한다")
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<CounselingResponse>> createCounseling(
            @Valid @RequestBody CounselingRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CounselingResponse response = counselingService.createCounseling(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "상담 내역 수정", description = "작성한 교사만 상담 내역을 수정할 수 있다")
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

    @Operation(summary = "상담 내역 삭제", description = "작성한 교사만 상담 내역을 삭제할 수 있다")
    @DeleteMapping("/{counselingId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteCounseling(
            @PathVariable Long counselingId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        counselingService.deleteCounseling(counselingId, userId);
        return ResponseEntity.ok(ApiResponse.success("상담 내역이 삭제되었습니다"));
    }

    @Operation(summary = "상담 내역 단건 조회")
    @GetMapping("/{counselingId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<CounselingResponse>> getCounseling(
            @PathVariable Long counselingId) {
        CounselingResponse response = counselingService.getCounseling(counselingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "학생별 상담 내역 조회", description = "본인이 작성한 + 공유된 상담 내역을 조회한다. 카테고리 필터 가능")
    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> getCounselingsByStudent(
            @PathVariable Long studentId,
            @RequestParam(required = false) CounselCategory category) {
        List<CounselingResponse> responses = counselingService.getCounselingsByStudent(studentId, category);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "공유된 상담 내역 조회", description = "다른 교사가 공유한 상담 내역을 조회한다")
    @GetMapping("/students/{studentId}/shared")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> getSharedCounselings(
            @PathVariable Long studentId) {
        List<CounselingResponse> responses = counselingService.getSharedCounselingsByStudent(studentId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "내 상담 내역 조회", description = "로그인한 교사가 자신이 진행한 모든 상담을 조회한다")
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> getMyCounselings(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<CounselingResponse> responses = counselingService.getMyCounselings(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "상담 내역 검색", description = "학생별 기간 검색")
    @GetMapping("/students/{studentId}/search")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<CounselingResponse>>> searchCounselings(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<CounselingResponse> responses = counselingService.searchCounselings(studentId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
