package com.inventory.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Additional repository for User-specific queries not covered by the main UserRepository.
 * Bean name will be "modelUserRepository" (no conflict with "userRepository").
 */
public interface ModelUserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByEmail(String email);
}
