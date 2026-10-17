package Marketly.Payment.Service.db.repository;

import Marketly.Payment.Service.db.Entity.WebhookEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvents,String> {
    @Modifying
    @Query(nativeQuery = true,
    value = """
            INSERT INTO webhook_events (deduplication_key, event_type, payment_id, processed_at)
            VALUES (:key, :type, :paymentId, now())
            ON CONFLICT (deduplication_key) DO NOTHING
    """)
    int insertIfAbsent(@Param("key") String key,
                       @Param("type") String type,
                       @Param("paymentId") UUID paymentId);
}
