package com.sscm.auth.repository;

import com.sscm.auth.entity.Teacher;
import com.sscm.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByUser(User user);
    Page<Teacher> findByUser_School_Id(Long schoolId, Pageable pageable);
}
