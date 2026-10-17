package Marketly.Payment.Service.payments.Service;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final EntityManager em;

    public AdminService(EntityManager em) { this.em = em; }

    @Transactional
    public void reset() {
        em.createNativeQuery(
                        "TRUNCATE TABLE callback_outbox, webhook_events, refunds, idempotency_keys, payment CASCADE")
                .executeUpdate();
    }
}