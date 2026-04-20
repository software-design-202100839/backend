package com.sscm.counsel.repository;

import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.entity.Counseling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CounselingRepository extends JpaRepository<Counseling, Long> {

    @Query("SELECT c FROM Counseling c JOIN FETCH c.student s JOIN FETCH s.user " +
            "JOIN FETCH c.teacher t JOIN FETCH t.user WHERE c.id = :id")
    Optional<Counseling> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT c FROM Counseling c JOIN FETCH c.student s JOIN FETCH s.user " +
            "JOIN FETCH c.teacher t JOIN FETCH t.user " +
            "WHERE c.student.id = :studentId ORDER BY c.counselDate DESC")
    List<Counseling> findByStudentIdWithDetails(@Param("studentId") Long studentId);

    @Query("SELECT c FROM Counseling c JOIN FETCH c.student s JOIN FETCH s.user " +
            "JOIN FETCH c.teacher t JOIN FETCH t.user " +
            "WHERE c.student.id = :studentId AND c.category = :category " +
            "ORDER BY c.counselDate DESC")
    List<Counseling> findByStudentIdAndCategoryWithDetails(
            @Param("studentId") Long studentId,
            @Param("category") CounselCategory category);

    @Query("SELECT c FROM Counseling c JOIN FETCH c.student s JOIN FETCH s.user " +
            "JOIN FETCH c.teacher t JOIN FETCH t.user " +
            "WHERE c.teacher.id = :teacherId ORDER BY c.counselDate DESC")
    List<Counseling> findByTeacherIdWithDetails(@Param("teacherId") Long teacherId);

    @Query("SELECT c FROM Counseling c JOIN FETCH c.student s JOIN FETCH s.user " +
            "JOIN FETCH c.teacher t JOIN FETCH t.user " +
            "WHERE c.student.id = :studentId " +
            "AND c.counselDate BETWEEN :startDate AND :endDate " +
            "ORDER BY c.counselDate DESC")
    List<Counseling> findByStudentIdAndDateRangeWithDetails(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
