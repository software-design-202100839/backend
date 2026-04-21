package com.sscm.auth.repository;

import com.sscm.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    Optional<User> findByPhoneHash(String phoneHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByPhoneHash(String phoneHash);
}
