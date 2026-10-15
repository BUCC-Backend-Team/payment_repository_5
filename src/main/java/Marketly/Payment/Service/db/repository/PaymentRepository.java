package Marketly.Payment.Service.db.repository;

import Marketly.Payment.Service.db.Entity.Payments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payments,UUID> {

    @Modifying
    @Query(value = """
        UPDATE payment
        SET status = 'SUCCEEDED', late = (status = 'EXPIRED'), paid_at = :paidAt, updated_at = now()
        WHERE id = :id AND status IN ('PENDING', 'EXPIRED')
        """, nativeQuery = true)
    int markSucceeded(@Param("id") UUID id, @Param("paidAt") Instant paidAt);
}
