package com.sscm.common.repository;

import com.sscm.auth.entity.Teacher;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.TeacherAssignment;
import com.sscm.grade.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {

    boolean existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
            Teacher teacher, ClassRoom classRoom, Subject subject, int academicYear);

    boolean existsByTeacherAndClassRoomAndAcademicYear(
            Teacher teacher, ClassRoom classRoom, int academicYear);

    @Query("SELECT a FROM TeacherAssignment a JOIN FETCH a.teacher t JOIN FETCH t.user JOIN FETCH a.classRoom JOIN FETCH a.subject WHERE a.academicYear = :year")
    List<TeacherAssignment> findByAcademicYearWithDetails(@Param("year") int year);

    @Query("SELECT a FROM TeacherAssignment a JOIN FETCH a.teacher t JOIN FETCH t.user JOIN FETCH a.classRoom JOIN FETCH a.subject WHERE a.teacher = :teacher AND a.academicYear = :year")
    List<TeacherAssignment> findByTeacherAndYear(@Param("teacher") Teacher teacher, @Param("year") int year);

    Optional<TeacherAssignment> findByTeacherAndClassRoomAndAcademicYear(Teacher teacher, ClassRoom classRoom, int academicYear);
}
