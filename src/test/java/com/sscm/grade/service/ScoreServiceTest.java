package com.sscm.grade.service;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.StudentEnrollment;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.common.repository.StudentEnrollmentRepository;
import com.sscm.common.repository.TeacherAssignmentRepository;
import com.sscm.common.service.AuditLogService;
import com.sscm.grade.dto.*;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreService 단위 테스트")
class ScoreServiceTest {

    @InjectMocks
    private ScoreService scoreService;

    @Mock private ScoreRepository scoreRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeacherAssignmentRepository teacherAssignmentRepository;
    @Mock private StudentEnrollmentRepository studentEnrollmentRepository;
    @Mock private AuditLogService auditLogService;

    private User teacherUser;
    private Teacher teacher;
    private User studentUser;
    private Student student;
    private Subject subject;
    private ClassRoom classRoom;
    private StudentEnrollment enrollment;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id(1L).name("김교사").build();
        teacher = Teacher.builder().id(1L).user(teacherUser).department("수학").build();

        studentUser = User.builder().id(2L).name("이학생").build();
        student = Student.builder().id(1L).user(studentUser).admissionYear(2024).build();

        subject = Subject.builder().id(1L).name("수학").code("MATH101").build();

        classRoom = ClassRoom.builder().id(1L).academicYear(2024).grade(2).classNum(3).build();

        enrollment = StudentEnrollment.builder()
                .id(1L).student(student).classRoom(classRoom).academicYear(2024).studentNum(15)
                .build();
    }

    private ScoreRequest makeRequest(Long studentId, Long subjectId, Integer year, Integer semester, BigDecimal score) {
        ScoreRequest req = new ScoreRequest();
        ReflectionTestUtils.setField(req, "studentId", studentId);
        ReflectionTestUtils.setField(req, "subjectId", subjectId);
        ReflectionTestUtils.setField(req, "year", year);
        ReflectionTestUtils.setField(req, "semester", semester);
        ReflectionTestUtils.setField(req, "score", score);
        return req;
    }

    private ScoreUpdateRequest makeUpdateRequest(BigDecimal score) {
        ScoreUpdateRequest req = new ScoreUpdateRequest();
        ReflectionTestUtils.setField(req, "score", score);
        return req;
    }

    private Score buildScore(BigDecimal scoreVal) {
        return Score.builder()
                .id(10L).student(student).subject(subject).teacher(teacher)
                .year(2024).semester(1).score(scoreVal).gradeLetter("A").rank(1)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createScore
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createScore")
    class CreateScore {

        @Test
        @DisplayName("정상 성적 등록")
        void success() {
            ScoreRequest req = makeRequest(1L, 1L, 2024, 1, new BigDecimal("92.00"));
            Score saved = buildScore(new BigDecimal("92.00"));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(studentEnrollmentRepository.findByStudentAndAcademicYear(student, 2024))
                    .willReturn(Optional.of(enrollment));
            given(teacherAssignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(true);
            given(scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(1L, 1L, 2024, 1))
                    .willReturn(Optional.empty());
            given(scoreRepository.save(any(Score.class))).willReturn(saved);
            given(scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(1L, 2024, 1))
                    .willReturn(List.of(saved));

            ScoreResponse result = scoreService.createScore(req, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getScore()).isEqualByComparingTo(new BigDecimal("92.00"));
            verify(auditLogService).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("학생 없음 → STUDENT_NOT_FOUND")
        void studentNotFound() {
            ScoreRequest req = makeRequest(99L, 1L, 2024, 1, new BigDecimal("80.00"));
            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }

        @Test
        @DisplayName("과목 없음 → SUBJECT_NOT_FOUND")
        void subjectNotFound() {
            ScoreRequest req = makeRequest(1L, 99L, 2024, 1, new BigDecimal("80.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SUBJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("교사 없음 → TEACHER_NOT_FOUND")
        void teacherNotFound() {
            ScoreRequest req = makeRequest(1L, 1L, 2024, 1, new BigDecimal("80.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(req, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TEACHER_NOT_FOUND);
        }

        @Test
        @DisplayName("수강 정보 없음 → RESOURCE_NOT_FOUND")
        void enrollmentNotFound() {
            ScoreRequest req = makeRequest(1L, 1L, 2024, 1, new BigDecimal("80.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(studentEnrollmentRepository.findByStudentAndAcademicYear(student, 2024))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.createScore(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("담당 교사 아님 → ACCESS_DENIED")
        void notAssigned() {
            ScoreRequest req = makeRequest(1L, 1L, 2024, 1, new BigDecimal("80.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(studentEnrollmentRepository.findByStudentAndAcademicYear(student, 2024))
                    .willReturn(Optional.of(enrollment));
            given(teacherAssignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(false);

            assertThatThrownBy(() -> scoreService.createScore(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("이미 등록된 성적 → SCORE_ALREADY_EXISTS")
        void scoreAlreadyExists() {
            ScoreRequest req = makeRequest(1L, 1L, 2024, 1, new BigDecimal("80.00"));
            Score existing = buildScore(new BigDecimal("80.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(studentEnrollmentRepository.findByStudentAndAcademicYear(student, 2024))
                    .willReturn(Optional.of(enrollment));
            given(teacherAssignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(true);
            given(scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(1L, 1L, 2024, 1))
                    .willReturn(Optional.of(existing));

            assertThatThrownBy(() -> scoreService.createScore(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCORE_ALREADY_EXISTS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateScore
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateScore")
    class UpdateScore {

        @Test
        @DisplayName("정상 성적 수정")
        void success() {
            Score score = buildScore(new BigDecimal("75.00"));
            ScoreUpdateRequest req = makeUpdateRequest(new BigDecimal("88.00"));

            given(scoreRepository.findById(10L)).willReturn(Optional.of(score));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(studentEnrollmentRepository.findByStudentAndAcademicYear(student, 2024))
                    .willReturn(Optional.of(enrollment));
            given(teacherAssignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(true);
            given(scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(1L, 2024, 1))
                    .willReturn(List.of(score));

            ScoreResponse result = scoreService.updateScore(10L, req, 1L);

            assertThat(result).isNotNull();
            verify(auditLogService).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("성적 없음 → SCORE_NOT_FOUND")
        void scoreNotFound() {
            ScoreUpdateRequest req = makeUpdateRequest(new BigDecimal("80.00"));
            given(scoreRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.updateScore(99L, req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCORE_NOT_FOUND);
        }

        @Test
        @DisplayName("담당 교사 아님 → ACCESS_DENIED")
        void notAssigned() {
            Score score = buildScore(new BigDecimal("75.00"));
            ScoreUpdateRequest req = makeUpdateRequest(new BigDecimal("80.00"));

            given(scoreRepository.findById(10L)).willReturn(Optional.of(score));
            given(userRepository.findById(1L)).willReturn(Optional.of(teacherUser));
            given(teacherRepository.findByUser(teacherUser)).willReturn(Optional.of(teacher));
            given(studentEnrollmentRepository.findByStudentAndAcademicYear(student, 2024))
                    .willReturn(Optional.of(enrollment));
            given(teacherAssignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(false);

            assertThatThrownBy(() -> scoreService.updateScore(10L, req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteScore
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteScore")
    class DeleteScore {

        @Test
        @DisplayName("정상 성적 삭제")
        void success() {
            Score score = buildScore(new BigDecimal("90.00"));
            given(scoreRepository.findById(10L)).willReturn(Optional.of(score));
            given(scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(1L, 2024, 1))
                    .willReturn(List.of());

            scoreService.deleteScore(10L);

            verify(scoreRepository).delete(score);
        }

        @Test
        @DisplayName("성적 없음 → SCORE_NOT_FOUND")
        void scoreNotFound() {
            given(scoreRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.deleteScore(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCORE_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getScore
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getScore")
    class GetScore {

        @Test
        @DisplayName("정상 단건 조회")
        void success() {
            Score score = buildScore(new BigDecimal("85.00"));
            given(scoreRepository.findById(10L)).willReturn(Optional.of(score));

            ScoreResponse result = scoreService.getScore(10L);

            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("없는 성적 → SCORE_NOT_FOUND")
        void notFound() {
            given(scoreRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.getScore(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SCORE_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStudentScores
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStudentScores")
    class GetStudentScores {

        @Test
        @DisplayName("정상 조회 — 성적 있음")
        void success() {
            Score score = buildScore(new BigDecimal("92.00"));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(scoreRepository.findByStudentWithSubject(1L, 2024, 1)).willReturn(List.of(score));

            StudentScoreSummary result = scoreService.getStudentScores(1L, 2024, 1);

            assertThat(result.getScores()).hasSize(1);
            assertThat(result.getAverageScore()).isEqualByComparingTo(new BigDecimal("92.00"));
        }

        @Test
        @DisplayName("성적 없음 → 평균 0")
        void emptyScores() {
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(scoreRepository.findByStudentWithSubject(1L, 2024, 1)).willReturn(List.of());

            StudentScoreSummary result = scoreService.getStudentScores(1L, 2024, 1);

            assertThat(result.getScores()).isEmpty();
            assertThat(result.getAverageScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("학생 없음 → STUDENT_NOT_FOUND")
        void studentNotFound() {
            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scoreService.getStudentScores(99L, 2024, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllSubjects
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllSubjects")
    class GetAllSubjects {

        @Test
        @DisplayName("전체 과목 조회")
        void success() {
            given(subjectRepository.findAll()).willReturn(List.of(subject));

            List<SubjectResponse> result = scoreService.getAllSubjects();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("MATH101");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // checkStudentAccess
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkStudentAccess")
    class CheckStudentAccess {

        @Test
        @DisplayName("학생 본인 접근 — 정상")
        void studentAccessOwnRecord() {
            given(studentRepository.findByUser_Id(2L)).willReturn(Optional.of(student));

            // student.getId() == 1L, studentId == 1L → 통과
            scoreService.checkStudentAccess(2L, "ROLE_STUDENT", 1L);
        }

        @Test
        @DisplayName("다른 학생 접근 → ACCESS_DENIED")
        void studentAccessOtherRecord() {
            given(studentRepository.findByUser_Id(2L)).willReturn(Optional.of(student));

            // student.getId() == 1L, studentId == 99L → 거부
            assertThatThrownBy(() -> scoreService.checkStudentAccess(2L, "ROLE_STUDENT", 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("교사 역할 — 접근 제한 없음")
        void teacherSkipsCheck() {
            // ROLE_TEACHER는 studentRepository 호출 없이 통과
            scoreService.checkStudentAccess(1L, "ROLE_TEACHER", 99L);
        }
    }
}
