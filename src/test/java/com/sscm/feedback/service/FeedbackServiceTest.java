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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService 단위 테스트")
class FeedbackServiceTest {

    @InjectMocks
    private FeedbackService feedbackService;

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private UserRepository userRepository;

    private User teacherUser;
    private Teacher teacher;
    private User studentUser;
    private Student student;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id(1L).name("김교사").build();
        teacher = Teacher.builder().id(1L).user(teacherUser).department("수학").build();

        studentUser = User.builder().id(2L).name("이학생").build();
        student = Student.builder().id(1L).user(studentUser).admissionYear(2024).build();
    }

    private Feedback buildFeedback() {
        return Feedback.builder()
                .id(10L).student(student).teacher(teacher)
                .category(FeedbackCategory.ACADEMIC)
                .content("수업 태도 양호")
                .isVisibleToStudent(true)
                .isVisibleToParent(false)
                .build();
    }

    private FeedbackRequest makeRequest(Long studentId, FeedbackCategory category, String content) {
        FeedbackRequest req = new FeedbackRequest();
        ReflectionTestUtils.setField(req, "studentId", studentId);
        ReflectionTestUtils.setField(req, "category", category);
        ReflectionTestUtils.setField(req, "content", content);
        ReflectionTestUtils.setField(req, "isVisibleToStudent", false);
        ReflectionTestUtils.setField(req, "isVisibleToParent", false);
        return req;
    }

    private FeedbackUpdateRequest makeUpdateRequest(FeedbackCategory category, String content) {
        FeedbackUpdateRequest req = new FeedbackUpdateRequest();
        ReflectionTestUtils.setField(req, "category", category);
        ReflectionTestUtils.setField(req, "content", content);
        ReflectionTestUtils.setField(req, "isVisibleToStudent", true);
        ReflectionTestUtils.setField(req, "isVisibleToParent", false);
        return req;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createFeedback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createFeedback")
    class CreateFeedback {

        @Test
        @DisplayName("정상 피드백 등록")
        void success() {
            FeedbackRequest req = makeRequest(1L, FeedbackCategory.ACADEMIC, "수업 태도 양호");
            Feedback saved = buildFeedback();

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(feedbackRepository.save(any(Feedback.class))).willReturn(saved);

            FeedbackResponse result = feedbackService.createFeedback(req, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEqualTo("수업 태도 양호");
            assertThat(result.getCategory()).isEqualTo(FeedbackCategory.ACADEMIC);
        }

        @Test
        @DisplayName("학생 없음 → STUDENT_NOT_FOUND")
        void studentNotFound() {
            FeedbackRequest req = makeRequest(99L, FeedbackCategory.ACADEMIC, "내용");
            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.createFeedback(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }

        @Test
        @DisplayName("교사 없음 → TEACHER_NOT_FOUND")
        void teacherNotFound() {
            FeedbackRequest req = makeRequest(1L, FeedbackCategory.ACADEMIC, "내용");
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.createFeedback(req, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TEACHER_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateFeedback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateFeedback")
    class UpdateFeedback {

        @Test
        @DisplayName("정상 피드백 수정")
        void success() {
            Feedback feedback = buildFeedback();
            FeedbackUpdateRequest req = makeUpdateRequest(FeedbackCategory.BEHAVIOR, "수정된 내용");

            given(feedbackRepository.findByIdWithDetails(10L)).willReturn(Optional.of(feedback));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));

            FeedbackResponse result = feedbackService.updateFeedback(10L, req, 1L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("피드백 없음 → FEEDBACK_NOT_FOUND")
        void feedbackNotFound() {
            FeedbackUpdateRequest req = makeUpdateRequest(FeedbackCategory.ACADEMIC, "내용");
            given(feedbackRepository.findByIdWithDetails(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.updateFeedback(99L, req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);
        }

        @Test
        @DisplayName("작성자가 아닌 교사 → ACCESS_DENIED")
        void accessDenied() {
            Feedback feedback = buildFeedback(); // teacher.id == 1L
            FeedbackUpdateRequest req = makeUpdateRequest(FeedbackCategory.ACADEMIC, "내용");

            Teacher otherTeacher = Teacher.builder().id(99L).user(teacherUser).build();

            given(feedbackRepository.findByIdWithDetails(10L)).willReturn(Optional.of(feedback));
            given(userRepository.findById(2L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(otherTeacher));

            assertThatThrownBy(() -> feedbackService.updateFeedback(10L, req, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteFeedback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteFeedback")
    class DeleteFeedback {

        @Test
        @DisplayName("정상 피드백 삭제")
        void success() {
            Feedback feedback = buildFeedback();
            given(feedbackRepository.findById(10L)).willReturn(Optional.of(feedback));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));

            feedbackService.deleteFeedback(10L, 1L);

            verify(feedbackRepository).delete(feedback);
        }

        @Test
        @DisplayName("피드백 없음 → FEEDBACK_NOT_FOUND")
        void feedbackNotFound() {
            given(feedbackRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.deleteFeedback(99L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);
        }

        @Test
        @DisplayName("작성자가 아닌 교사 → ACCESS_DENIED")
        void accessDenied() {
            Feedback feedback = buildFeedback(); // teacher.id == 1L
            Teacher otherTeacher = Teacher.builder().id(99L).user(teacherUser).build();

            given(feedbackRepository.findById(10L)).willReturn(Optional.of(feedback));
            given(userRepository.findById(2L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(otherTeacher));

            assertThatThrownBy(() -> feedbackService.deleteFeedback(10L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getFeedback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeedback")
    class GetFeedback {

        @Test
        @DisplayName("정상 단건 조회")
        void success() {
            Feedback feedback = buildFeedback();
            given(feedbackRepository.findByIdWithDetails(10L)).willReturn(Optional.of(feedback));

            FeedbackResponse result = feedbackService.getFeedback(10L);

            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("없는 피드백 → FEEDBACK_NOT_FOUND")
        void notFound() {
            given(feedbackRepository.findByIdWithDetails(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.getFeedback(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getFeedbacksByStudent
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeedbacksByStudent")
    class GetFeedbacksByStudent {

        @Test
        @DisplayName("카테고리 없이 전체 조회")
        void successWithoutCategory() {
            Feedback feedback = buildFeedback();
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(feedbackRepository.findByStudentIdWithDetails(1L)).willReturn(List.of(feedback));

            List<FeedbackResponse> result = feedbackService.getFeedbacksByStudent(1L, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("카테고리 필터링 조회")
        void successWithCategory() {
            Feedback feedback = buildFeedback();
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(feedbackRepository.findByStudentIdAndCategoryWithDetails(1L, FeedbackCategory.ACADEMIC))
                    .willReturn(List.of(feedback));

            List<FeedbackResponse> result = feedbackService.getFeedbacksByStudent(1L, FeedbackCategory.ACADEMIC);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo(FeedbackCategory.ACADEMIC);
        }

        @Test
        @DisplayName("학생 없음 → STUDENT_NOT_FOUND")
        void studentNotFound() {
            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.getFeedbacksByStudent(99L, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getVisibleFeedbacksForStudent / getVisibleFeedbacksForParent
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("가시성 필터 조회")
    class VisibilityFilter {

        @Test
        @DisplayName("학생에게 공개된 피드백 조회")
        void visibleToStudent() {
            Feedback feedback = buildFeedback();
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(feedbackRepository.findVisibleToStudentByStudentId(1L)).willReturn(List.of(feedback));

            List<FeedbackResponse> result = feedbackService.getVisibleFeedbacksForStudent(1L);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("학부모에게 공개된 피드백 조회")
        void visibleToParent() {
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(feedbackRepository.findVisibleToParentByStudentId(1L)).willReturn(List.of());

            List<FeedbackResponse> result = feedbackService.getVisibleFeedbacksForParent(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("학생 없음 (학생 공개) → STUDENT_NOT_FOUND")
        void visibleToStudentNotFound() {
            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.getVisibleFeedbacksForStudent(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }

        @Test
        @DisplayName("학생 없음 (학부모 공개) → STUDENT_NOT_FOUND")
        void visibleToParentNotFound() {
            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> feedbackService.getVisibleFeedbacksForParent(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }
    }
}
