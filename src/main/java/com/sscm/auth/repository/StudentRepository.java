package com.sscm.auth.repository;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUser(User user);
    Optional<Student> findByUser_Id(Long userId);
    Page<Student> findByUser_School_Id(Long schoolId, Pageable pageable);
    List<Student> findByUser_School_Id(Long schoolId);

    @Query("SELECT s FROM Student s JOIN FETCH s.user u WHERE u.school.id = :schoolId AND u.name LIKE %:name%")
    List<Student> findBySchoolIdAndNameContaining(@Param("schoolId") Long schoolId, @Param("name") String name);
}
