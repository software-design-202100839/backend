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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CounselingService 단위 테스트")
class CounselingServiceTest {

    @InjectMocks
    private CounselingService counselingService;

    @Mock
    private CounselingRepository counselingRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private UserRepository userRepository;

    // ── 공통 픽스처 ──────────────────────────────────────────────────────────

    private User teacherUser;
    private Teacher teacher;
    private User studentUser;
    private Student student;
    private Counseling counseling;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id(1L).name("김선생").build();
        teacher = Teacher.builder().id(1L).user(teacherUser).build();

        studentUser = User.builder().id(10L).name("이학생").build();
        student = Student.builder().id(10L).user(studentUser).admissionYear(2024).build();

        counseling = Counseling.builder()
                .id(1L)
                .student(student)
                .teacher(teacher)
                .counselDate(LocalDate.of(2026, 4, 21))
                .category(CounselCategory.ACADEMIC)
                .content("학업 상담 내용")
                .nextPlan("다음 상담 계획")
                .nextCounselDate(LocalDate.of(2026, 4, 28))
                .build();
    }

    private CounselingRequest buildCreateRequest(Long studentId) {
        // CounselingRequest는 @NoArgsConstructor + @Getter이므로 리플렉션 대신
        // ObjectMapper 없이 직접 필드를 설정하기 어렵다 → 별도 헬퍼 클래스 대신 Mockito stubbing 전략 사용
        CounselingRequest request = new CounselingRequest();
        setField(request, "studentId", studentId);
        setField(request, "counselDate", LocalDate.of(2026, 4, 21));
        setField(request, "category", CounselCategory.ACADEMIC);
        setField(request, "content", "학업 상담 내용");
        return request;
    }

    private CounselingUpdateRequest buildUpdateRequest() {
        CounselingUpdateRequest request = new CounselingUpdateRequest();
        setField(request, "counselDate", LocalDate.of(2026, 4, 22));
        setField(request, "category", CounselCategory.CAREER);
        setField(request, "content", "수정된 상담 내용");
        return request;
    }

    /** 패키지-프라이빗 필드를 포함한 DTO에 값을 주입하기 위한 리플렉션 헬퍼 */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("setField 실패: " + fieldName, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createCounseling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCounseling — 상담 내역 등록")
    class CreateCounseling {

        @Test
        @DisplayName("정상 요청 시 상담 내역 저장 후 응답 반환")
        void success() {
            CounselingRequest request = buildCreateRequest(10L);
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(counselingRepository.save(any())).willReturn(counseling);

            CounselingResponse response = counselingService.createCounseling(request, 1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getStudentName()).isEqualTo("이학생");
            assertThat(response.getTeacherName()).isEqualTo("김선생");
            assertThat(response.getCategory()).isEqualTo(CounselCategory.ACADEMIC);
            verify(counselingRepository).save(any(Counseling.class));
        }

        @Test
        @DisplayName("존재하지 않는 학생 ID → STUDENT_NOT_FOUND")
        void studentNotFound() {
            CounselingRequest request = buildCreateRequest(999L);
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.createCounseling(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }

        @Test
        @DisplayName("User 레코드 없음 → TEACHER_NOT_FOUND")
        void userNotFound() {
            CounselingRequest request = buildCreateRequest(10L);
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.createCounseling(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TEACHER_NOT_FOUND));
        }

        @Test
        @DisplayName("Teacher 프로필 없음 → TEACHER_NOT_FOUND")
        void teacherProfileNotFound() {
            CounselingRequest request = buildCreateRequest(10L);
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.createCounseling(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TEACHER_NOT_FOUND));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateCounseling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCounseling — 상담 내역 수정")
    class UpdateCounseling {

        @Test
        @DisplayName("작성자 교사가 수정 성공 → 수정된 값 반환")
        void success() {
            CounselingUpdateRequest request = buildUpdateRequest();
            given(counselingRepository.findByIdWithDetails(1L)).willReturn(Optional.of(counseling));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));

            CounselingResponse response = counselingService.updateCounseling(1L, request, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getCategory()).isEqualTo(CounselCategory.CAREER);
            assertThat(response.getContent()).isEqualTo("수정된 상담 내용");
        }

        @Test
        @DisplayName("존재하지 않는 상담 → COUNSELING_NOT_FOUND")
        void counselingNotFound() {
            given(counselingRepository.findByIdWithDetails(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.updateCounseling(999L, buildUpdateRequest(), 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.COUNSELING_NOT_FOUND));
        }

        @Test
        @DisplayName("다른 교사가 수정 시도 → ACCESS_DENIED")
        void accessDenied() {
            User otherUser = User.builder().id(2L).name("박선생").build();
            Teacher otherTeacher = Teacher.builder().id(2L).user(otherUser).build();

            given(counselingRepository.findByIdWithDetails(1L)).willReturn(Optional.of(counseling));
            given(userRepository.findById(2L)).willReturn(Optional.of(otherUser));
            given(teacherRepository.findByUser(otherUser)).willReturn(Optional.of(otherTeacher));

            assertThatThrownBy(() -> counselingService.updateCounseling(1L, buildUpdateRequest(), 2L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ACCESS_DENIED));
        }

        @Test
        @DisplayName("수정 요청 교사 User 없음 → TEACHER_NOT_FOUND")
        void teacherUserNotFound() {
            given(counselingRepository.findByIdWithDetails(1L)).willReturn(Optional.of(counseling));
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.updateCounseling(1L, buildUpdateRequest(), 99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TEACHER_NOT_FOUND));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCounseling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCounseling — 상담 단건 조회")
    class GetCounseling {

        @Test
        @DisplayName("존재하는 상담 조회 → 응답 반환")
        void success() {
            given(counselingRepository.findByIdWithDetails(1L)).willReturn(Optional.of(counseling));

            CounselingResponse response = counselingService.getCounseling(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getStudentName()).isEqualTo("이학생");
        }

        @Test
        @DisplayName("존재하지 않는 상담 → COUNSELING_NOT_FOUND")
        void notFound() {
            given(counselingRepository.findByIdWithDetails(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.getCounseling(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.COUNSELING_NOT_FOUND));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCounselingsByStudent
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCounselingsByStudent — 학생별 상담 목록 조회")
    class GetCounselingsByStudent {

        @Test
        @DisplayName("카테고리 없이 전체 조회 → 전체 목록 반환")
        void successWithoutCategory() {
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(counselingRepository.findByStudentIdWithDetails(10L))
                    .willReturn(List.of(counseling));

            List<CounselingResponse> result = counselingService.getCounselingsByStudent(10L, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo(CounselCategory.ACADEMIC);
        }

        @Test
        @DisplayName("카테고리 필터로 조회 → 해당 카테고리 목록 반환")
        void successWithCategory() {
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(counselingRepository.findByStudentIdAndCategoryWithDetails(10L, CounselCategory.ACADEMIC))
                    .willReturn(List.of(counseling));

            List<CounselingResponse> result =
                    counselingService.getCounselingsByStudent(10L, CounselCategory.ACADEMIC);

            assertThat(result).hasSize(1);
            verify(counselingRepository).findByStudentIdAndCategoryWithDetails(10L, CounselCategory.ACADEMIC);
        }

        @Test
        @DisplayName("존재하지 않는 학생 → STUDENT_NOT_FOUND")
        void studentNotFound() {
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.getCounselingsByStudent(999L, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }

        @Test
        @DisplayName("상담 내역이 없는 학생 → 빈 리스트 반환")
        void emptyResult() {
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(counselingRepository.findByStudentIdWithDetails(10L)).willReturn(List.of());

            List<CounselingResponse> result = counselingService.getCounselingsByStudent(10L, null);

            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyCounselings
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyCounselings — 내 상담 내역 조회")
    class GetMyCounselings {

        @Test
        @DisplayName("교사 자신의 상담 목록 반환")
        void success() {
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(counselingRepository.findByTeacherIdWithDetails(1L)).willReturn(List.of(counseling));

            List<CounselingResponse> result = counselingService.getMyCounselings(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTeacherName()).isEqualTo("김선생");
        }

        @Test
        @DisplayName("교사 User 없음 → TEACHER_NOT_FOUND")
        void userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.getMyCounselings(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TEACHER_NOT_FOUND));
        }

        @Test
        @DisplayName("Teacher 프로필 없음 → TEACHER_NOT_FOUND")
        void teacherProfileNotFound() {
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.getMyCounselings(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TEACHER_NOT_FOUND));
        }

        @Test
        @DisplayName("상담 내역 없음 → 빈 리스트 반환")
        void emptyResult() {
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(counselingRepository.findByTeacherIdWithDetails(1L)).willReturn(List.of());

            List<CounselingResponse> result = counselingService.getMyCounselings(1L);

            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // searchCounselings
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchCounselings — 기간별 상담 검색")
    class SearchCounselings {

        @Test
        @DisplayName("기간 내 상담 목록 반환")
        void success() {
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2026, 4, 30);
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(counselingRepository.findByStudentIdAndDateRangeWithDetails(10L, start, end))
                    .willReturn(List.of(counseling));

            List<CounselingResponse> result = counselingService.searchCounselings(10L, start, end);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCounselDate()).isEqualTo(LocalDate.of(2026, 4, 21));
        }

        @Test
        @DisplayName("기간 내 상담 없음 → 빈 리스트 반환")
        void emptyResult() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 1, 31);
            given(studentRepository.findById(10L)).willReturn(Optional.of(student));
            given(counselingRepository.findByStudentIdAndDateRangeWithDetails(10L, start, end))
                    .willReturn(List.of());

            List<CounselingResponse> result = counselingService.searchCounselings(10L, start, end);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 학생 → STUDENT_NOT_FOUND")
        void studentNotFound() {
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> counselingService.searchCounselings(
                    999L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }
    }
}
