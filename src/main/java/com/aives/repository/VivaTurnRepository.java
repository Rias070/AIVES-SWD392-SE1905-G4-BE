package com.aives.repository;

import com.aives.entity.VivaTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VivaTurnRepository extends JpaRepository<VivaTurn, UUID> {
    List<VivaTurn> findByExamSessionUuidOrderByTurnOrderAsc(UUID examSessionUuid);
}
