package com.sscm.counsel.service;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.counsel.dto.CounselingRequest;
import com.sscm.counsel.dto.CounselingResponse;
import com.sscm.counsel.dto.CounselingUpdateRequest;
import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.entity.Counseling;
import com.sscm.counsel.repository.CounselingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingService {

    private final CounselingRepository counselingRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;

    @Transactional
    public CounselingResponse createCounseling(CounselingRequest request, Long currentUserId) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        Teacher teacher = findTeacherByUserId(currentUserId);

        Counseling counseling = Counseling.builder()
                .student(student)
                .teacher(teacher)
                .counselDate(request.getCounselDate())
                .category(request.getCategory())
                .content(request.getContent())
                .nextPlan(request.getNextPlan())
                .nextCounselDate(request.getNextCounselDate())
                .build();

        Counseling saved = counselingRepository.save(counseling);
        return CounselingResponse.from(saved);
    }

    @Transactional
    public CounselingResponse updateCounseling(Long counselingId, CounselingUpdateRequest request,
                                                Long currentUserId) {
        Counseling counseling = counselingRepository.findByIdWithDetails(counselingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_NOT_FOUND));

        Teacher teacher = findTeacherByUserId(currentUserId);
        if (!counseling.getTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        counseling.update(
                request.getCounselDate(),
                request.getCategory(),
                request.getContent(),
                request.getNextPlan(),
                request.getNextCounselDate()
        );

        return CounselingResponse.from(counseling);
    }

    public CounselingResponse getCounseling(Long counselingId) {
        Counseling counseling = counselingRepository.findByIdWithDetails(counselingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_NOT_FOUND));
        return CounselingResponse.from(counseling);
    }

    public List<CounselingResponse> getCounselingsByStudent(Long studentId, CounselCategory category) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        List<Counseling> counselings;
        if (category != null) {
            counselings = counselingRepository.findByStudentIdAndCategoryWithDetails(studentId, category);
        } else {
            counselings = counselingRepository.findByStudentIdWithDetails(studentId);
        }

        return counselings.stream().map(CounselingResponse::from).toList();
    }

    public List<CounselingResponse> getMyCounselings(Long currentUserId) {
        Teacher teacher = findTeacherByUserId(currentUserId);
        return counselingRepository.findByTeacherIdWithDetails(teacher.getId())
                .stream().map(CounselingResponse::from).toList();
    }

    public List<CounselingResponse> searchCounselings(Long studentId,
                                                       LocalDate startDate, LocalDate endDate) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        return counselingRepository.findByStudentIdAndDateRangeWithDetails(studentId, startDate, endDate)
                .stream().map(CounselingResponse::from).toList();
    }

    private Teacher findTeacherByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
        return teacherRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
    }
}
