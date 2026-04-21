package com.sscm.common.repository;

import com.sscm.common.entity.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {

    boolean existsByAcademicYearAndGradeAndClassNum(int academicYear, int grade, int classNum);

    List<ClassRoom> findByAcademicYear(int academicYear);

    @Query("SELECT c FROM ClassRoom c LEFT JOIN FETCH c.homeroomTeacher t LEFT JOIN FETCH t.user WHERE c.academicYear = :year ORDER BY c.grade, c.classNum")
    List<ClassRoom> findByAcademicYearWithTeacher(@Param("year") int year);

    Optional<ClassRoom> findByAcademicYearAndGradeAndClassNum(int academicYear, int grade, int classNum);
}
