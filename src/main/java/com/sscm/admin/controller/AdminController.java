package com.sscm.admin.controller;

import com.sscm.admin.dto.*;
import com.sscm.admin.service.AdminService;
import com.sscm.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin", description = "관리자 전용 API")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ─── 교사 ────────────────────────────────────────────────

    @Operation(summary = "교사 목록 조회")
    @GetMapping("/teachers")
    public ResponseEntity<ApiResponse<Page<TeacherSummary>>> getTeachers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getTeachers(pageable)));
    }

    @Operation(summary = "교사 등록")
    @PostMapping("/teachers")
    public ResponseEntity<ApiResponse<TeacherSummary>> registerTeacher(
            @Valid @RequestBody RegisterTeacherRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminService.registerTeacher(req)));
    }

    // ─── 학생 ────────────────────────────────────────────────

    @Operation(summary = "학생 목록 조회")
    @GetMapping("/students")
    public ResponseEntity<ApiResponse<Page<StudentSummary>>> getStudents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getStudents(pageable)));
    }

    @Operation(summary = "학생 등록")
    @PostMapping("/students")
    public ResponseEntity<ApiResponse<StudentSummary>> registerStudent(
            @Valid @RequestBody RegisterStudentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminService.registerStudent(req)));
    }

    @Operation(summary = "학생-학부모 연결")
    @PostMapping("/students/{studentId}/parents")
    public ResponseEntity<ApiResponse<Void>> linkParentChild(
            @PathVariable Long studentId,
            @Valid @RequestBody LinkParentChildRequest req) {
        adminService.linkParentChild(studentId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─── 학부모 ───────────────────────────────────────────────

    @Operation(summary = "학부모 목록 조회")
    @GetMapping("/parents")
    public ResponseEntity<ApiResponse<Page<ParentSummary>>> getParents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getParents(pageable)));
    }

    @Operation(summary = "학부모 등록")
    @PostMapping("/parents")
    public ResponseEntity<ApiResponse<ParentSummary>> registerParent(
            @Valid @RequestBody RegisterParentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminService.registerParent(req)));
    }

    // ─── 반 ──────────────────────────────────────────────────

    @Operation(summary = "반 목록 조회")
    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<ClassSummary>>> getClasses(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int academicYear) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getClasses(academicYear)));
    }

    @Operation(summary = "반 생성")
    @PostMapping("/classes")
    public ResponseEntity<ApiResponse<ClassSummary>> createClass(
            @Valid @RequestBody CreateClassRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminService.createClass(req)));
    }

    @Operation(summary = "담임 배정")
    @PutMapping("/classes/{classId}/homeroom")
    public ResponseEntity<ApiResponse<Void>> assignHomeroom(
            @PathVariable Long classId,
            @Valid @RequestBody AssignHomeroomRequest req) {
        adminService.assignHomeroom(classId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "학생 반 배정")
    @PostMapping("/classes/{classId}/students")
    public ResponseEntity<ApiResponse<Void>> enrollStudent(
            @PathVariable Long classId,
            @Valid @RequestBody EnrollStudentRequest req) {
        adminService.enrollStudent(classId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─── 과목 배정 ────────────────────────────────────────────

    @Operation(summary = "과목 배정 목록 조회")
    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentSummary>>> getAssignments(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int academicYear) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAssignments(academicYear)));
    }

    @Operation(summary = "과목 배정 등록")
    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<AssignmentSummary>> createAssignment(
            @Valid @RequestBody CreateAssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminService.createAssignment(req)));
    }

    @Operation(summary = "과목 배정 해제")
    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(@PathVariable Long assignmentId) {
        adminService.deleteAssignment(assignmentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
