package com.sscm.counsel.service;

import com.sscm.auth.entity.Role;
import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.analytics.event.CounselingChangedEvent;
import com.sscm.analytics.event.payload.CounselingEventPayload;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.counsel.dto.CounselingRequest;
import com.sscm.counsel.dto.CounselingResponse;
import com.sscm.counsel.dto.CounselingUpdateRequest;
import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.entity.Counseling;
import com.sscm.counsel.repository.CounselingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
        publishCounselingAnalyticsEvent("CREATED", saved);
        return CounselingResponse.from(saved);
    }

    @Transactional
    public CounselingResponse updateCounseling(Long counselingId, CounselingUpdateRequest request,
                                                Long currentUserId) {
        Counseling counseling = counselingRepository.findByIdWithDetails(counselingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_NOT_FOUND));

        checkWriteAccess(counseling, currentUserId);

        counseling.update(
                request.getCounselDate(),
                request.getCategory(),
                request.getContent(),
                request.getNextPlan(),
                request.getNextCounselDate()
        );

        publishCounselingAnalyticsEvent("UPDATED", counseling);
        return CounselingResponse.from(counseling);
    }

    @Transactional
    public void deleteCounseling(Long counselingId, Long currentUserId) {
        Counseling counseling = counselingRepository.findByIdWithDetails(counselingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_NOT_FOUND));

        checkWriteAccess(counseling, currentUserId);

        counselingRepository.delete(counseling);
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

    /**
     * 수정/삭제 권한 체크: 작성자 본인 또는 ADMIN
     */
    private void checkWriteAccess(Counseling counseling, Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));

        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }

        Teacher teacher = teacherRepository.findByUser(currentUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));

        if (!counseling.getTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void publishCounselingAnalyticsEvent(String action, Counseling counseling) {
        eventPublisher.publishEvent(new CounselingChangedEvent(action,
                CounselingEventPayload.builder()
                        .counselingId(counseling.getId())
                        .studentId(counseling.getStudent().getId())
                        .teacherId(counseling.getTeacher().getId())
                        .counselDate(counseling.getCounselDate())
                        .category(counseling.getCategory().name())
                        .build()));
    }

    private Teacher findTeacherByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
        return teacherRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
    }
}
