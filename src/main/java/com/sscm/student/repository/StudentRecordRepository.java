package com.sscm.student.repository;

import com.sscm.student.entity.RecordCategory;
import com.sscm.student.entity.StudentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRecordRepository extends JpaRepository<StudentRecord, Long> {

    List<StudentRecord> findByStudentIdAndYearAndSemester(Long studentId, Integer year, Integer semester);

    List<StudentRecord> findByStudentIdAndYearAndSemesterAndCategory(
            Long studentId, Integer year, Integer semester, RecordCategory category);

    List<StudentRecord> findByStudentIdOrderByYearDescSemesterDesc(Long studentId);
}
