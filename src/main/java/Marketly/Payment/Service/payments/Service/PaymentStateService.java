package Marketly.Payment.Service.payments.Service;

import Marketly.Payment.Service.callbacks.CallbackService;
import Marketly.Payment.Service.db.Entity.Payments;
import Marketly.Payment.Service.db.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** The only place that moves a payment between PENDING / EXPIRED / SUCCEEDED. */
@Service
public class PaymentStateService {
    private final PaymentRepository payments;
    private final CallbackService callbacks;

    public PaymentStateService(PaymentRepository payments, CallbackService callbacks) {
        this.payments = payments;
        this.callbacks = callbacks;
    }

    /** PENDING or EXPIRED -> SUCCEEDED. Returns false if another worker already moved it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean succeed(UUID id, Instant paidAt, String channel) {
        if (payments.markSucceeded(id, paidAt, channel) == 0) return false;
        Payments fresh = payments.findById(id).orElseThrow();
        callbacks.enqueue(fresh, "payment.succeeded");
        return true;
    }

    /** PENDING -> EXPIRED. Returns false if it is no longer pending. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expire(UUID id) {
        if (payments.markExpired(id) == 0) return false;
        Payments fresh = payments.findById(id).orElseThrow();
        callbacks.enqueue(fresh, "payment.expired");
        return true;
    }
}