package com.sscm.auth.repository;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUser(User user);
    Optional<Student> findByUser_Id(Long userId);
}
