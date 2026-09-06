package com.ankita.h2sdosimeter.repository;

import com.ankita.h2sdosimeter.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {

    Optional<Badge> findByBadgeId(String badgeId);

    boolean existsByBadgeId(String badgeId);
}
