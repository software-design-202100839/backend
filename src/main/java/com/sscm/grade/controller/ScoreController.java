package com.sscm.grade.controller;

import com.sscm.common.response.ApiResponse;
import com.sscm.grade.dto.*;
import com.sscm.grade.service.ScoreService;
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

@Tag(name = "Grade", description = "성적 관리 API")
@RestController
@RequestMapping("/api/v1/grades")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @Operation(summary = "성적 등록", description = "교사가 학생의 과목별 성적을 등록한다")
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<ScoreResponse>> createScore(
            @Valid @RequestBody ScoreRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ScoreResponse response = scoreService.createScore(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "성적 수정", description = "교사가 등록된 성적을 수정한다")
    @PutMapping("/{scoreId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<ScoreResponse>> updateScore(
            @PathVariable Long scoreId,
            @Valid @RequestBody ScoreUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ScoreResponse response = scoreService.updateScore(scoreId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "성적 삭제", description = "교사가 등록된 성적을 삭제한다")
    @DeleteMapping("/{scoreId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteScore(@PathVariable Long scoreId) {
        scoreService.deleteScore(scoreId);
        return ResponseEntity.ok(ApiResponse.success("성적이 삭제되었습니다"));
    }

    @Operation(summary = "성적 단건 조회")
    @GetMapping("/{scoreId}")
    public ResponseEntity<ApiResponse<ScoreResponse>> getScore(@PathVariable Long scoreId) {
        ScoreResponse response = scoreService.getScore(scoreId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "학생별 학기 성적 조회", description = "학생의 특정 학기 전 과목 성적 + 총점/평균 조회")
    @GetMapping("/students/{studentId}")
    public ResponseEntity<ApiResponse<StudentScoreSummary>> getStudentScores(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester) {
        StudentScoreSummary summary = scoreService.getStudentScores(studentId, year, semester);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "과목 목록 조회")
    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> getAllSubjects() {
        List<SubjectResponse> subjects = scoreService.getAllSubjects();
        return ResponseEntity.ok(ApiResponse.success(subjects));
    }
}
