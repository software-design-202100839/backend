package com.sscm.common.repository;

import com.sscm.auth.entity.Student;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

    boolean existsByStudentAndAcademicYear(Student student, int academicYear);

    boolean existsByClassRoomAndStudentNum(ClassRoom classRoom, int studentNum);

    List<StudentEnrollment> findByClassRoom(ClassRoom classRoom);

    Optional<StudentEnrollment> findByStudentAndAcademicYear(Student student, int academicYear);

    @Query("SELECT e FROM StudentEnrollment e JOIN FETCH e.student s JOIN FETCH s.user WHERE e.classRoom = :classRoom")
    List<StudentEnrollment> findByClassRoomWithStudent(@Param("classRoom") ClassRoom classRoom);

    @Query("SELECT e FROM StudentEnrollment e JOIN FETCH e.classRoom WHERE e.student = :student ORDER BY e.academicYear DESC")
    List<StudentEnrollment> findByStudentOrderByYear(@Param("student") Student student);
}
