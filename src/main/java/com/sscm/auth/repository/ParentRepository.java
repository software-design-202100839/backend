package com.sscm.auth.repository;

import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {
    Optional<Parent> findByUser(User user);
    Optional<Parent> findByUser_Id(Long userId);
    Page<Parent> findByUser_School_Id(Long schoolId, Pageable pageable);
}
