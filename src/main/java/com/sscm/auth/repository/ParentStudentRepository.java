package com.sscm.auth.repository;

import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.ParentStudent;
import com.sscm.auth.entity.ParentStudentId;
import com.sscm.auth.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, ParentStudentId> {
    List<ParentStudent> findByStudent(Student student);
    List<ParentStudent> findByParent(Parent parent);
    boolean existsByParentAndStudent(Parent parent, Student student);
}
