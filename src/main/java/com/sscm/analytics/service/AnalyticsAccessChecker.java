package com.sscm.analytics.service;

import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.Student;
import com.sscm.auth.repository.ParentRepository;
import com.sscm.auth.repository.ParentStudentRepository;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 분석 API 접근 권한 검증.
 *
 * - TEACHER, ADMIN: 모든 학생 데이터 조회 가능
 * - STUDENT: 본인 데이터만 조회 가능
 * - PARENT: 자녀 데이터만 조회 가능
 */
@Service
@RequiredArgsConstructor
public class AnalyticsAccessChecker {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;

    /**
     * 현재 사용자가 해당 studentId의 데이터를 조회할 수 있는지 검증.
     * 권한이 없으면 BusinessException(ACCESS_DENIED) 발생.
     */
    public void checkAccess(Long studentId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        switch (role) {
            case "ROLE_TEACHER", "ROLE_ADMIN" -> {
                // 교사, 관리자: 모든 학생 조회 가능
            }
            case "ROLE_STUDENT" -> {
                // 학생: 본인만
                Student student = studentRepository.findByUser_Id(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
                if (!student.getId().equals(studentId)) {
                    throw new BusinessException(ErrorCode.ACCESS_DENIED);
                }
            }
            case "ROLE_PARENT" -> {
                // 학부모: 자녀만
                Parent parent = parentRepository.findByUser_Id(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
                if (!parentStudentRepository.existsByParentAndStudent(parent, student)) {
                    throw new BusinessException(ErrorCode.ACCESS_DENIED);
                }
            }
            default -> throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
