package Marketly.Payment.Service.db.repository;

import Marketly.Payment.Service.db.Entity.CallbackOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CallbackOutboxRepository extends JpaRepository<CallbackOutbox, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO callback_outbox (id, payment_id, type, payload, attempts, next_attempt_time)
        VALUES (:id, :paymentId, :type, :payload, 0, now())
        """)
    void insert(@Param("id") UUID id,
                @Param("paymentId") UUID paymentId,
                @Param("type") String type,
                @Param("payload") String payload);
}