package com.sscm.grade.service;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.ParentStudentRepository;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.TeacherRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.common.service.AuditLogService;
import com.sscm.grade.dto.*;
import com.sscm.grade.entity.Score;
import com.sscm.grade.entity.Subject;
import com.sscm.grade.repository.ScoreRepository;
import com.sscm.grade.repository.SubjectRepository;
import com.sscm.analytics.event.ScoreChangedEvent;
import com.sscm.analytics.event.payload.ScoreEventPayload;
import com.sscm.common.tenant.TenantContext;
import com.sscm.notification.entity.NotificationReferenceType;
import com.sscm.notification.entity.NotificationType;
import com.sscm.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    private final ParentStudentRepository parentStudentRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final io.micrometer.core.instrument.Counter scoreCreateCounter;

    @Transactional
    public ScoreResponse createScore(ScoreRequest request, Long currentUserId) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        if (!student.getUser().getSchool().getId().equals(TenantContext.requireSchoolId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBJECT_NOT_FOUND));
        Teacher teacher = findTeacherByUserId(currentUserId);

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

        publishScoreNotification(student, subject, saved);
        publishScoreAnalyticsEvent("CREATED", saved);
        scoreCreateCounter.increment();

        return ScoreResponse.from(saved);
    }

    @Transactional
    public ScoreResponse updateScore(Long scoreId, ScoreUpdateRequest request, Long currentUserId) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCORE_NOT_FOUND));

        String oldScore = score.getScore().toPlainString();
        String gradeLetter = Score.calculateGradeLetter(request.getScore());
        score.updateScore(request.getScore(), gradeLetter, currentUserId);

        updateRanks(score.getSubject().getId(), score.getYear(), score.getSemester());

        auditLogService.record("scores", scoreId, "score",
                oldScore, request.getScore().toPlainString(), currentUserId);

        publishScoreNotification(score.getStudent(), score.getSubject(), score);
        publishScoreAnalyticsEvent("UPDATED", score);

        return ScoreResponse.from(score);
    }

    @Transactional
    public void deleteScore(Long scoreId) {
        Score score = scoreRepository.findById(scoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCORE_NOT_FOUND));

        Long subjectId = score.getSubject().getId();
        Integer year = score.getYear();
        Integer semester = score.getSemester();

        publishScoreAnalyticsEvent("DELETED", score);
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

    private void publishScoreNotification(Student student, Subject subject, Score score) {
        List<Long> recipientIds = getStudentAndParentUserIds(student);
        if (recipientIds.isEmpty()) return;

        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientIds(recipientIds)
                .type(NotificationType.SCORE_UPDATE)
                .title("성적 등록/변경")
                .message(String.format("%s 과목 성적이 업데이트되었습니다.", subject.getName()))
                .referenceType(NotificationReferenceType.SCORE)
                .referenceId(score.getId())
                .build());
    }

    /** 분석 이벤트 발행: Spring 내부 이벤트 → AnalyticsEventBridge → Kafka */
    private void publishScoreAnalyticsEvent(String action, Score score) {
        eventPublisher.publishEvent(new ScoreChangedEvent(action,
                ScoreEventPayload.builder()
                        .scoreId(score.getId())
                        .studentId(score.getStudent().getId())
                        .subjectId(score.getSubject().getId())
                        .teacherId(score.getTeacher().getId())
                        .schoolId(TenantContext.getSchoolId())
                        .year(score.getYear())
                        .semester(score.getSemester())
                        .score(score.getScore())
                        .gradeLetter(score.getGradeLetter())
                        .build()));
    }

    private List<Long> getStudentAndParentUserIds(Student student) {
        List<Long> ids = new ArrayList<>();
        ids.add(student.getUser().getId());
        parentStudentRepository.findByStudent(student).forEach(ps ->
                ids.add(ps.getParent().getUser().getId()));
        return ids;
    }
}
