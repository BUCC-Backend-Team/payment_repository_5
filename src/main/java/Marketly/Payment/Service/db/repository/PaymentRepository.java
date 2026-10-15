package Marketly.Payment.Service.db.repository;

import Marketly.Payment.Service.db.Entity.Payments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payments,UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (id, request_hash, payment_id, created_at)
            VALUES (:id, :hash, :paymentId, now())
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id,
                       @Param("hash")String hash,
                       @Param("paymentId")UUID paymentId);
}
