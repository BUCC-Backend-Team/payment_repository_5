package Marketly.Payment.Service.payments.Service;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final EntityManager entityManager;

    public AdminService(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional
    public void reset() {
        entityManager.createNativeQuery(
                        "TRUNCATE TABLE callback_outbox, webhook_events, refunds, idempotency_keys, payment CASCADE")
                .executeUpdate();
    }
}