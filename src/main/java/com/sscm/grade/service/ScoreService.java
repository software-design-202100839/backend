package com.sscm.grade.service;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final SubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public ScoreResponse createScore(ScoreRequest request, Long currentUserId) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBJECT_NOT_FOUND));
        Teacher teacher = findTeacherByUserId(currentUserId);

        // 담당 교사 검증: 해당 학년도·반·과목에 배정된 교사만 성적 입력 가능
        StudentEnrollment enrollment = studentEnrollmentRepository
                .findByStudentAndAcademicYear(student, request.getYear())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean isAssigned = teacherAssignmentRepository
                .existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                        teacher, enrollment.getClassRoom(), subject, request.getYear());
        if (!isAssigned) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        scoreRepository.findByStudentIdAndSubjectIdAndYearAndSemester(
                request.getStudentId(), request.getSubjectId(),
                request.getYear(), request.getSemester()
        ).ifPresent(s -> { throw new BusinessException(ErrorCode.SCORE_ALREADY_EXISTS); });

        String gradeLetter = Score.calculateGradeLetter(request.getScore());

        Score score = Score.builder()
                .student(student)
                .subject(subject)
                .teacher(teacher)
                .year(request.getYear())
                .semester(request.getSemester())
                .score(request.getScore())
                .gradeLetter(gradeLetter)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        Score saved = scoreRepository.save(score);
        updateRanks(subject.getId(), request.getYear(), request.getSemester());

        auditLogService.record("scores", saved.getId(), "score",
                null, request.getScore().toPlainString(), currentUserId);

        return ScoreResponse.from(saved);
    }

    @Transactional
    public ScoreResponse updateScore(Long scoreId, ScoreUpdateRequest request, Long currentUserId) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCORE_NOT_FOUND));
        Teacher teacher = findTeacherByUserId(currentUserId);

        // 담당 교사 검증
        StudentEnrollment enrollment = studentEnrollmentRepository
                .findByStudentAndAcademicYear(score.getStudent(), score.getYear())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean isAssigned = teacherAssignmentRepository
                .existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                        teacher, enrollment.getClassRoom(), score.getSubject(), score.getYear());
        if (!isAssigned) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        String oldScore = score.getScore().toPlainString();
        String gradeLetter = Score.calculateGradeLetter(request.getScore());
        score.updateScore(request.getScore(), gradeLetter, currentUserId);

        updateRanks(score.getSubject().getId(), score.getYear(), score.getSemester());

        auditLogService.record("scores", scoreId, "score",
                oldScore, request.getScore().toPlainString(), currentUserId);

        return ScoreResponse.from(score);
    }

    @Transactional
    public void deleteScore(Long scoreId) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCORE_NOT_FOUND));

        Long subjectId = score.getSubject().getId();
        Integer year = score.getYear();
        Integer semester = score.getSemester();

        scoreRepository.delete(score);
        updateRanks(subjectId, year, semester);
    }

    public ScoreResponse getScore(Long scoreId) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCORE_NOT_FOUND));
        return ScoreResponse.from(score);
    }

    public StudentScoreSummary getStudentScores(Long studentId, Integer year, Integer semester) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        List<Score> scores = scoreRepository.findByStudentWithSubject(studentId, year, semester);
        List<ScoreResponse> scoreResponses = scores.stream()
                .map(ScoreResponse::from)
                .toList();

        BigDecimal totalScore = scores.stream()
                .map(Score::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageScore = scores.isEmpty()
                ? BigDecimal.ZERO
                : totalScore.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);

        String averageGradeLetter = Score.calculateGradeLetter(averageScore);

        return StudentScoreSummary.builder()
                .studentId(studentId)
                .studentName(student.getUser().getName())
                .year(year)
                .semester(semester)
                .scores(scoreResponses)
                .totalScore(totalScore)
                .averageScore(averageScore)
                .averageGradeLetter(averageGradeLetter)
                .build();
    }

    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAll().stream()
                .map(SubjectResponse::from)
                .toList();
    }

    private void updateRanks(Long subjectId, Integer year, Integer semester) {
        List<Score> scores = scoreRepository.findBySubjectAndSemesterOrderByScoreDesc(
                subjectId, year, semester);
        for (int i = 0; i < scores.size(); i++) {
            scores.get(i).updateRank(i + 1);
        }
    }

    public void checkStudentAccess(Long userId, String role, Long studentId) {
        if ("ROLE_STUDENT".equals(role)) {
            Student student = studentRepository.findByUser_Id(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
            if (!student.getId().equals(studentId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    private Teacher findTeacherByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
        return teacherRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
    }
}
