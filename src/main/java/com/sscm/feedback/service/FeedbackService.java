package com.sscm.feedback.service;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.feedback.dto.FeedbackRequest;
import com.sscm.feedback.dto.FeedbackResponse;
import com.sscm.feedback.dto.FeedbackUpdateRequest;
import com.sscm.feedback.entity.Feedback;
import com.sscm.feedback.entity.FeedbackCategory;
import com.sscm.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeedbackResponse createFeedback(FeedbackRequest request, Long currentUserId) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        Teacher teacher = findTeacherByUserId(currentUserId);

        Feedback feedback = Feedback.builder()
                .student(student)
                .teacher(teacher)
                .category(request.getCategory())
                .content(request.getContent())
                .isVisibleToStudent(request.getIsVisibleToStudent())
                .isVisibleToParent(request.getIsVisibleToParent())
                .build();

        Feedback saved = feedbackRepository.save(feedback);
        return FeedbackResponse.from(saved);
    }

    @Transactional
    public FeedbackResponse updateFeedback(Long feedbackId, FeedbackUpdateRequest request, Long currentUserId) {
        Feedback feedback = feedbackRepository.findByIdWithDetails(feedbackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));

        Teacher teacher = findTeacherByUserId(currentUserId);
        if (!feedback.getTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        feedback.update(
                request.getCategory(),
                request.getContent(),
                request.getIsVisibleToStudent(),
                request.getIsVisibleToParent()
        );

        return FeedbackResponse.from(feedback);
    }

    @Transactional
    public void deleteFeedback(Long feedbackId, Long currentUserId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));

        Teacher teacher = findTeacherByUserId(currentUserId);
        if (!feedback.getTeacher().getId().equals(teacher.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        feedbackRepository.delete(feedback);
    }

    public FeedbackResponse getFeedback(Long feedbackId) {
        Feedback feedback = feedbackRepository.findByIdWithDetails(feedbackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
        return FeedbackResponse.from(feedback);
    }

    public List<FeedbackResponse> getFeedbacksByStudent(Long studentId, FeedbackCategory category) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        List<Feedback> feedbacks;
        if (category != null) {
            feedbacks = feedbackRepository.findByStudentIdAndCategoryWithDetails(studentId, category);
        } else {
            feedbacks = feedbackRepository.findByStudentIdWithDetails(studentId);
        }

        return feedbacks.stream().map(FeedbackResponse::from).toList();
    }

    public List<FeedbackResponse> getVisibleFeedbacksForStudent(Long studentId) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        return feedbackRepository.findVisibleToStudentByStudentId(studentId)
                .stream().map(FeedbackResponse::from).toList();
    }

    public List<FeedbackResponse> getVisibleFeedbacksForParent(Long studentId) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        return feedbackRepository.findVisibleToParentByStudentId(studentId)
                .stream().map(FeedbackResponse::from).toList();
    }

    private Teacher findTeacherByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
        return teacherRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
    }
}
