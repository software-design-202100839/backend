package com.sscm.feedback.repository;

import com.sscm.feedback.entity.Feedback;
import com.sscm.feedback.entity.FeedbackCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Query("SELECT f FROM Feedback f JOIN FETCH f.student s JOIN FETCH s.user " +
            "JOIN FETCH f.teacher t JOIN FETCH t.user WHERE f.id = :id")
    Optional<Feedback> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT f FROM Feedback f JOIN FETCH f.student s JOIN FETCH s.user " +
            "JOIN FETCH f.teacher t JOIN FETCH t.user " +
            "WHERE f.student.id = :studentId ORDER BY f.createdAt DESC")
    List<Feedback> findByStudentIdWithDetails(@Param("studentId") Long studentId);

    @Query("SELECT f FROM Feedback f JOIN FETCH f.student s JOIN FETCH s.user " +
            "JOIN FETCH f.teacher t JOIN FETCH t.user " +
            "WHERE f.student.id = :studentId AND f.category = :category " +
            "ORDER BY f.createdAt DESC")
    List<Feedback> findByStudentIdAndCategoryWithDetails(
            @Param("studentId") Long studentId,
            @Param("category") FeedbackCategory category);

    @Query("SELECT f FROM Feedback f JOIN FETCH f.student s JOIN FETCH s.user " +
            "JOIN FETCH f.teacher t JOIN FETCH t.user " +
            "WHERE f.student.id = :studentId AND f.isVisibleToStudent = true " +
            "ORDER BY f.createdAt DESC")
    List<Feedback> findVisibleToStudentByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT f FROM Feedback f JOIN FETCH f.student s JOIN FETCH s.user " +
            "JOIN FETCH f.teacher t JOIN FETCH t.user " +
            "WHERE f.student.id = :studentId AND f.isVisibleToParent = true " +
            "ORDER BY f.createdAt DESC")
    List<Feedback> findVisibleToParentByStudentId(@Param("studentId") Long studentId);
}
