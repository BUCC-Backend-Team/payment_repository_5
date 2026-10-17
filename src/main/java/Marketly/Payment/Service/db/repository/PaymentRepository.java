package Marketly.Payment.Service.db.repository;

import Marketly.Payment.Service.db.Entity.Payments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payments,UUID> {

    @Modifying
    @Query(value = """
        UPDATE payment
        SET status = 'SUCCEEDED', late = (status = 'EXPIRED'), paid_at = :paidAt,channel=:channel, updated_at = now()
        WHERE id = :id AND status IN ('PENDING', 'EXPIRED')
        """, nativeQuery = true)
    int markSucceeded(@Param("id") UUID id, @Param("paidAt") Instant paidAt, @Param("channel") String channel);

    @Modifying
    @Query(value = "UPDATE payment SET checkout_url = :url, updated_at = now() WHERE id = :id",
                        nativeQuery = true)
                int saveCheckoutUrl(@Param("id") UUID id, @Param("url") String url);

    @Query(nativeQuery = true, value = """
    SELECT id FROM payment
    WHERE status = 'PENDING'
      AND ( (checkout_url IS NOT NULL AND created_at <= :normalCutoff)
         OR (checkout_url IS NULL     AND created_at <= :stuckCutoff) )
    ORDER BY created_at
    LIMIT :max
    """)
    List<UUID> findExpirable(@Param("normalCutoff") Instant normalCutoff,
                             @Param("stuckCutoff") Instant stuckCutoff,
                             @Param("max") int max);
}
