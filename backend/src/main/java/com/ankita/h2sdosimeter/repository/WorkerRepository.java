package com.ankita.h2sdosimeter.repository;

import com.ankita.h2sdosimeter.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByWorkerId(String workerId);

    Optional<Worker> findByEmail(String email);

    Optional<Worker> findByBadgeId(String badgeId);

    List<Worker> findByActiveTrue();

    List<Worker> findByDepartmentAndActiveTrue(String department);

    boolean existsByWorkerId(String workerId);

    boolean existsByEmail(String email);

    boolean existsByBadgeId(String badgeId);
}
