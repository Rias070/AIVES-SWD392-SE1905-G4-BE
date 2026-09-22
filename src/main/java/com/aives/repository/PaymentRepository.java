package com.aives.repository;

import com.aives.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);
    Optional<Payment> findByMomoOrderId(String momoOrderId);
    List<Payment> findByUserUuidOrderByCreatedAtDesc(UUID userUuid);
    List<Payment> findByExamSessionUuid(UUID examSessionUuid);
}
