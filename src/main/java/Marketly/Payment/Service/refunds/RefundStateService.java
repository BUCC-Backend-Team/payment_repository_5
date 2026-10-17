package Marketly.Payment.Service.refunds;

import Marketly.Payment.Service.callbacks.CallbackService;
import Marketly.Payment.Service.db.enums.PaymentStatus;
import Marketly.Payment.Service.db.repository.PaymentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Refund pending -> processed | failed. Every method locks the payment row FIRST,
 * the same order RefundService uses, so concurrent refund work cannot deadlock.
 */
@Service
public class RefundStateService {
    private record Locked(PaymentStatus status, long amount, long refunded) {}

    private final JdbcTemplate jdbc;
    private final PaymentRepository payments;
    private final CallbackService callbacks;

    public RefundStateService(JdbcTemplate jdbc, PaymentRepository payments, CallbackService callbacks) {
        this.jdbc = jdbc;
        this.payments = payments;
        this.callbacks = callbacks;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean processed(UUID refundId) {
        UUID paymentId = paymentIdOf(refundId);
        if (paymentId == null) return false;
        Locked l = lock(paymentId);

        int n = jdbc.update(
                "UPDATE refunds SET status = 'PROCESSED', updated_at = now() WHERE id = ? AND status = 'PENDING'",
                refundId);
        if (n == 0) return false;                                   // already final, ignore the repeat

        long amount = jdbc.queryForObject("SELECT amount FROM refunds WHERE id = ?", Long.class, refundId);
        long refunded = l.refunded() + amount;
        PaymentStatus next = refunded >= l.amount() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        if (!l.status().canMoveTo(next)) {
            throw new IllegalStateException("Illegal payment transition " + l.status() + " -> " + next);
        }
        jdbc.update("UPDATE payment SET amount_refunded = ?, status = ?, updated_at = now() WHERE id = ?",
                refunded, next.name(), paymentId);

        callbacks.enqueue(payments.findById(paymentId).orElseThrow(), "payment.refunded");
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean failed(UUID refundId, String reason) {
        UUID paymentId = paymentIdOf(refundId);
        if (paymentId == null) return false;
        lock(paymentId);

        int n = jdbc.update("""
                UPDATE refunds SET status = 'FAILED', failure_reason = ?, updated_at = now()
                WHERE id = ? AND status = 'PENDING'
                """, reason, refundId);
        if (n == 0) return false;                                   // balance is released: FAILED no longer counts

        callbacks.enqueue(payments.findById(paymentId).orElseThrow(), "payment.refund_failed");
        return true;
    }

    /**
     * Matches a Paystack refund event to our refund row: by Paystack's refund id first, otherwise the
     * oldest pending refund of that payment with the same amount (covers a refund whose id we never stored).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID findRefundId(UUID paymentId, String paystackRefundId, long amount) {
        List<UUID> byId = jdbc.query(
                "SELECT id FROM refunds WHERE payment_id = ? AND paystack_refund_id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), paymentId, paystackRefundId);
        if (!byId.isEmpty()) return byId.get(0);

        List<UUID> pending = jdbc.query("""
                SELECT id FROM refunds
                WHERE payment_id = ? AND status = 'PENDING' AND (? < 0 OR amount = ?)
                ORDER BY created_at LIMIT 1
                """, (rs, i) -> rs.getObject(1, UUID.class), paymentId, amount, amount);
        if (pending.isEmpty()) return null;

        jdbc.update("UPDATE refunds SET paystack_refund_id = ? WHERE id = ? AND paystack_refund_id IS NULL",
                paystackRefundId, pending.get(0));
        return pending.get(0);
    }

    private UUID paymentIdOf(UUID refundId) {
        List<UUID> r = jdbc.query("SELECT payment_id FROM refunds WHERE id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), refundId);
        return r.isEmpty() ? null : r.get(0);
    }

    private Locked lock(UUID paymentId) {
        return jdbc.query("SELECT status, amount, amount_refunded FROM payment WHERE id = ? FOR UPDATE",
                        (rs, i) -> new Locked(PaymentStatus.valueOf(rs.getString(1)), rs.getLong(2), rs.getLong(3)),
                        paymentId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " missing"));
    }
}