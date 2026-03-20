package com.securepushgateway.repository;

import com.securepushgateway.model.Badge;
import com.securepushgateway.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByDeveloper(User developer);
    boolean existsByDeveloperAndBadgeType(User developer, Badge.BadgeType type);
}
