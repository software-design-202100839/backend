package com.sscm.auth.repository;

import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {
    Optional<Parent> findByUser(User user);
}
