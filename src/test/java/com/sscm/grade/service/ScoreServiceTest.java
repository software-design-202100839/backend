package com.sscm.grade.service;

import com.sscm.auth.entity.Role;
import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.grade.dto.ScoreRequest;
import com.sscm.grade.dto.ScoreResponse;
import com.sscm.grade.dto.ScoreUpdateRequest;
import com.sscm.grade.dto.StudentScoreSummary;
import com.sscm.grade.dto.SubjectResponse;
import com.sscm.grade.entity.Score;
import com.sscm.grade.entity.Subject;
import com.sscm.grade.repository.ScoreRepository;
import com.sscm.grade.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreService 단위 테스트")
class ScoreServiceTest {

    @InjectMocks
    private ScoreService scoreService;

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private UserRepository userRepository;

    private User teacherUser;
    private User studentUser;
    private Teacher teacher;
    private Student student;
    private Subject subject;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder()
                .id(1L).email("teacher@test.com").name("김교사")
                .passwordHash("hash").role(Role.TEACHER).build();
        studentUser = User.builder()
                .id(2L).email("student@test.com").name("이학생")
                .passwordHash("hash").role(Role.STUDENT).build();
        teacher = Teacher.builder().id(1L).user(teacherUser).department("수학과").build();
        student = Student.builder().id(1L).user(studentUser).grade(1).classNum(1).studentNum(1).admissionYear(2026).build();
        subject = Subject.builder().id(1L).name("수학").code("MATH01").build();
    }

    private ScoreRequest createScoreRequest(Long studentId, Long subjectId, BigDecimal score) throws Exception {
        ScoreRequest request = new ScoreRequest();
        setField(request, "studentId", studentId);
        setField(request, "subjectId", subjectId);
        setField(request, "year", 2026);
        setField(request, "semester", 1);
        setField(request, "score", score);
        return request;
    }

    private ScoreUpdateRequest createUpdateRequest(BigDecimal score) throws Exception {
        ScoreUpdateRequest request = new ScoreUpdateRequest();
        setField(request, "score", score);
        return request;
    }

    private Score createScore(Long id, BigDecimal scoreVal) {
        Score score = Score.builder()
                .student(student).subject(subject).teacher(teacher)
                .year(2026).semester(1).score(scoreVal)
                .gradeLetter(Score.calculateGradeLetter(scoreVal))
                .createdBy(1L).updatedBy(1L).build();
        setIdField(score, id);
        return score;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setIdField(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("성적 등록")
    class CreateScore {

        @Test
        @DisplayName("정상적으로 성적을 등록하고 등급이 자동 계산된다")
        void success() throws Exception {
            ScoreRequest request = createScoreRequest(1L, 1L, new BigDecimal("95.00"));
            Score savedScore = createScore(1L, new BigDecimal("95.00"));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(1L, 1L, 2026, 1))
                    .willReturn(Optional.empty());
            given(scoreRepository.save(any(Score.class))).willReturn(savedScore);
            given(scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(1L, 2026, 1))
                    .willReturn(List.of(savedScore));

            ScoreResponse result = scoreService.createScore(request, 1L);

            assertThat(result.getGradeLetter()).isEqualTo("A+");
            assertThat(result.getStudentName()).isEqualTo("이학생");
            verify(scoreRepository).save(any(Score.class));
        }

        @Test
        @DisplayName("존재하지 않는 학생이면 STUDENT_NOT_FOUND 예외")
        void studentNotFound() throws Exception {
            ScoreRequest request = createScoreRequest(999L, 1L, new BigDecimal("90.00"));
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }

        @Test
        @DisplayName("존재하지 않는 과목이면 SUBJECT_NOT_FOUND 예외")
        void subjectNotFound() throws Exception {
            ScoreRequest request = createScoreRequest(1L, 999L, new BigDecimal("90.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));
        }

        @Test
        @DisplayName("동일 학생-과목-학기 성적이 이미 존재하면 SCORE_ALREADY_EXISTS 예외")
        void duplicateScore() throws Exception {
            ScoreRequest request = createScoreRequest(1L, 1L, new BigDecimal("90.00"));
            Score existing = createScore(1L, new BigDecimal("85.00"));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(1L, 1L, 2026, 1))
                    .willReturn(Optional.of(existing));

            assertThatThrownBy(() -> scoreService.createScore(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SCORE_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("교사 정보를 찾을 수 없으면 TEACHER_NOT_FOUND 예외")
        void teacherNotFound() throws Exception {
            ScoreRequest request = createScoreRequest(1L, 1L, new BigDecimal("90.00"));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.TEACHER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("성적 수정")
    class UpdateScore {

        @Test
        @DisplayName("점수 수정 시 등급이 재계산된다")
        void success() throws Exception {
            Score existing = createScore(1L, new BigDecimal("70.00"));
            ScoreUpdateRequest request = createUpdateRequest(new BigDecimal("95.00"));

            given(scoreRepository.findById(1L)).willReturn(Optional.of(existing));
            given(scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(1L, 2026, 1))
                    .willReturn(List.of(existing));

            ScoreResponse result = scoreService.updateScore(1L, request, 1L);

            assertThat(result.getGradeLetter()).isEqualTo("A+");
        }

        @Test
        @DisplayName("존재하지 않는 성적 ID면 SCORE_NOT_FOUND 예외")
        void scoreNotFound() throws Exception {
            ScoreUpdateRequest request = createUpdateRequest(new BigDecimal("90.00"));
            given(scoreRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.updateScore(999L, request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SCORE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("성적 삭제")
    class DeleteScore {

        @Test
        @DisplayName("정상 삭제 후 석차가 재계산된다")
        void success() {
            Score existing = createScore(1L, new BigDecimal("85.00"));

            given(scoreRepository.findById(1L)).willReturn(Optional.of(existing));
            given(scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(1L, 2026, 1))
                    .willReturn(Collections.emptyList());

            scoreService.deleteScore(1L);

            verify(scoreRepository).delete(existing);
        }

        @Test
        @DisplayName("존재하지 않는 성적 ID면 SCORE_NOT_FOUND 예외")
        void scoreNotFound() {
            given(scoreRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.deleteScore(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SCORE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("성적 조회")
    class GetScore {

        @Test
        @DisplayName("성적 단건 조회 성공")
        void success() {
            Score score = createScore(1L, new BigDecimal("88.00"));
            given(scoreRepository.findById(1L)).willReturn(Optional.of(score));

            ScoreResponse result = scoreService.getScore(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getScore()).isEqualByComparingTo(new BigDecimal("88.00"));
        }

        @Test
        @DisplayName("존재하지 않는 성적이면 SCORE_NOT_FOUND 예외")
        void notFound() {
            given(scoreRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.getScore(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SCORE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("학생별 학기 성적 조회")
    class GetStudentScores {

        @Test
        @DisplayName("총점/평균/평균등급이 올바르게 계산된다")
        void success() {
            Score score1 = createScore(1L, new BigDecimal("90.00"));
            Score score2 = createScore(2L, new BigDecimal("80.00"));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(scoreRepository.findByStudentWithSubject(1L, 2026, 1))
                    .willReturn(List.of(score1, score2));

            StudentScoreSummary result = scoreService.getStudentScores(1L, 2026, 1);

            assertThat(result.getTotalScore()).isEqualByComparingTo(new BigDecimal("170.00"));
            assertThat(result.getAverageScore()).isEqualByComparingTo(new BigDecimal("85.00"));
            assertThat(result.getAverageGradeLetter()).isEqualTo("B+");
            assertThat(result.getScores()).hasSize(2);
        }

        @Test
        @DisplayName("성적이 없으면 평균 0, 등급 F")
        void emptyScores() {
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(scoreRepository.findByStudentWithSubject(1L, 2026, 1))
                    .willReturn(Collections.emptyList());

            StudentScoreSummary result = scoreService.getStudentScores(1L, 2026, 1);

            assertThat(result.getTotalScore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getAverageScore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getAverageGradeLetter()).isEqualTo("F");
            assertThat(result.getScores()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 학생이면 STUDENT_NOT_FOUND 예외")
        void studentNotFound() {
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.getStudentScores(999L, 2026, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("과목 목록 조회")
    class GetAllSubjects {

        @Test
        @DisplayName("전체 과목 목록을 반환한다")
        void success() {
            Subject subject2 = Subject.builder().id(2L).name("영어").code("ENG01").build();
            given(subjectRepository.findAll()).willReturn(List.of(subject, subject2));

            List<SubjectResponse> result = scoreService.getAllSubjects();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("수학");
            assertThat(result.get(1).getName()).isEqualTo("영어");
        }
    }
}
