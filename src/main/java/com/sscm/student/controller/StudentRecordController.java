package com.sscm.student.controller;

import com.sscm.common.response.ApiResponse;
import com.sscm.student.dto.*;
import com.sscm.student.entity.RecordCategory;
import com.sscm.student.service.StudentRecordService;
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

@Tag(name = "Student Record", description = "학생부 관리 API")
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentRecordController {

    private final StudentRecordService studentRecordService;

    @Operation(summary = "학생 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentInfoResponse>>> getAllStudents() {
        return ResponseEntity.ok(ApiResponse.success(studentRecordService.getAllStudents()));
    }

    @Operation(summary = "학생 기본정보 조회")
    @GetMapping("/{studentId}")
    public ResponseEntity<ApiResponse<StudentInfoResponse>> getStudentInfo(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.success(studentRecordService.getStudentInfo(studentId)));
    }

    @Operation(summary = "학생부 항목 등록", description = "교사가 학생부 항목(출결, 특기사항 등)을 등록한다")
    @PostMapping("/records")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<StudentRecordResponse>> createRecord(
            @Valid @RequestBody StudentRecordRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        StudentRecordResponse response = studentRecordService.createRecord(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "학생부 항목 수정")
    @PutMapping("/records/{recordId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<StudentRecordResponse>> updateRecord(
            @PathVariable Long recordId,
            @Valid @RequestBody StudentRecordUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        StudentRecordResponse response = studentRecordService.updateRecord(recordId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "학생부 항목 삭제")
    @DeleteMapping("/records/{recordId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(@PathVariable Long recordId) {
        studentRecordService.deleteRecord(recordId);
        return ResponseEntity.ok(ApiResponse.success("학생부 항목이 삭제되었습니다"));
    }

    @Operation(summary = "학생부 항목 단건 조회")
    @GetMapping("/records/{recordId}")
    public ResponseEntity<ApiResponse<StudentRecordResponse>> getRecord(
            @PathVariable Long recordId) {
        return ResponseEntity.ok(ApiResponse.success(studentRecordService.getRecord(recordId)));
    }

    @Operation(summary = "학생별 학기 학생부 조회", description = "카테고리 필터 가능. 학생은 공개 항목만, 학부모는 학부모 공개 항목만 조회됨")
    @GetMapping("/{studentId}/records")
    public ResponseEntity<ApiResponse<List<StudentRecordResponse>>> getStudentRecords(
            @PathVariable Long studentId,
            @RequestParam Integer year,
            @RequestParam Integer semester,
            @RequestParam(required = false) RecordCategory category,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        List<StudentRecordResponse> records = studentRecordService.getStudentRecords(
                studentId, year, semester, category, callerId);
        return ResponseEntity.ok(ApiResponse.success(records));
    }
}
