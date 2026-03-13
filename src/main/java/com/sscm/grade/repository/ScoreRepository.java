package com.sscm.grade.repository;

import com.sscm.grade.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    List<Score> findByStudentIdAndYearAndSemester(Long studentId, Integer year, Integer semester);

    Optional<Score> findByStudentIdAndSubjectIdAndYearAndSemester(
            Long studentId, Long subjectId, Integer year, Integer semester);

    @Query("SELECT s FROM Score s WHERE s.subject.id = :subjectId AND s.year = :year " +
            "AND s.semester = :semester ORDER BY s.score DESC")
    List<Score> findBySubjectAndSemesterOrderByScoreDesc(
            @Param("subjectId") Long subjectId,
            @Param("year") Integer year,
            @Param("semester") Integer semester);

    @Query("SELECT s FROM Score s JOIN FETCH s.subject WHERE s.student.id = :studentId " +
            "AND s.year = :year AND s.semester = :semester")
    List<Score> findByStudentWithSubject(
            @Param("studentId") Long studentId,
            @Param("year") Integer year,
            @Param("semester") Integer semester);
}
