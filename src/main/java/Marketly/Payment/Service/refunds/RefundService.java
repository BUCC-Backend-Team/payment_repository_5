package Marketly.Payment.Service.refunds;

import Marketly.Payment.Service.controller.dto.RefundResponse;
import Marketly.Payment.Service.db.enums.PaymentStatus;
import Marketly.Payment.Service.exception.ApiException;
import Marketly.Payment.Service.exception.ProviderException;
import Marketly.Payment.Service.payments.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RefundService {
    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int STUCK_AFTER_SECONDS = 120;

    public record CreateResult(RefundResponse response, boolean replayed) {}
    private record Reserved(UUID refundId, UUID paymentId, long amount, boolean replay) {}
    private record KeyRow(String hash, UUID refundId) {}
    private record Stuck(UUID id, UUID paymentId, long amount) {}

    private final JdbcTemplate jdbc;
    private final PaymentProvider provider;
    private final RefundStateService state;
    private final TransactionTemplate tx;

    public RefundService(JdbcTemplate jdbc, PaymentProvider provider, RefundStateService state,
                         PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.state = state;
        this.tx = new TransactionTemplate(txManager);
    }

    public CreateResult create(String paymentIdRaw, String rawKey, Long requested) {
        UUID paymentId = parse(paymentIdRaw);
        if (requested != null && requested <= 0) {
            throw new ApiException("amount must be a whole number of kobo greater than 0", 400, "invalid_request");
        }
        String keyId = "refund:create:" + rawKey;
        String hash = hash(paymentId + "\n" + (requested == null ? "" : requested));

        // Step 1: key + balance reservation + refund row, in ONE short transaction.
        Reserved r = tx.execute(s -> reserve(paymentId, keyId, hash, requested));

        // Step 2: Paystack call with no transaction open. Skipped on a replay.
        if (!r.replay()) dispatch(r.refundId(), r.paymentId(), r.amount());

        return new CreateResult(new RefundResponse(r.refundId(), r.amount(), "pending"), r.replay());
    }

    private Reserved reserve(UUID paymentId, String keyId, String hash, Long requested) {
        UUID refundId = UUID.randomUUID();

        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys (id, request_hash, payment_id, refund_id, response_status, created_at)
                VALUES (?, ?, ?, ?, 202, now())
                ON CONFLICT (id) DO NOTHING
                """, keyId, hash, paymentId, refundId);

        if (inserted == 0) {                       // key already used: replay or 422
            KeyRow k = jdbc.query("SELECT request_hash, refund_id FROM idempotency_keys WHERE id = ?",
                            (rs, i) -> new KeyRow(rs.getString(1), rs.getObject(2, UUID.class)), keyId)
                    .stream().findFirst()
                    .orElseThrow(() -> new ApiException("Retry the request", 409, "request_in_progress"));
            if (!k.hash().equals(hash)) {
                throw new ApiException("This Idempotency-Key was already used with a different request body",
                        422, "idempotency_key_reused");
            }
            long amount = jdbc.query("SELECT amount FROM refunds WHERE id = ?", (rs, i) -> rs.getLong(1), k.refundId())
                    .stream().findFirst()
                    .orElseThrow(() -> new ApiException("Retry the request", 409, "request_in_progress"));
            return new Reserved(k.refundId(), paymentId, amount, true);
        }

        // Row lock on the payment: concurrent refund requests queue here, so the balance check is race-free.
        record Row(String status, long amount) {}
        Row p = jdbc.query("SELECT status, amount FROM payment WHERE id = ? FOR UPDATE",
                        (rs, i) -> new Row(rs.getString(1), rs.getLong(2)), paymentId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException("Payment not found", 404, "not_found"));

        PaymentStatus status = PaymentStatus.valueOf(p.status());
        if (status != PaymentStatus.SUCCEEDED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ApiException("Payment is not refundable", 409, "not_refundable");   // rolls the key back too
        }

        long reserved = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM refunds
                WHERE payment_id = ? AND status IN ('PENDING', 'PROCESSED')
                """, Long.class, paymentId);
        long remaining = p.amount() - reserved;
        long amount = requested == null ? remaining : requested;
        if (amount <= 0 || amount > remaining) {
            throw new ApiException("Refund exceeds the refundable balance", 409, "not_refundable");
        }

        jdbc.update("""
                INSERT INTO refunds (id, payment_id, amount, status, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', now(), now())
                """, refundId, paymentId, amount);
        return new Reserved(refundId, paymentId, amount, false);
    }

    /**
     * Calls Paystack. Declines and validation errors are final (refund FAILED, balance released).
     * Network errors and 5xx are retried with backoff. If we still cannot tell whether Paystack got it,
     * the refund stays PENDING with no Paystack id and recoverStuck() sorts it out.
     */
    private void dispatch(UUID refundId, UUID paymentId, long amount) {
        Set<String> used = usedIds(paymentId);
        for (int attempt = 1; ; attempt++) {
            try {
                PaymentProvider.RefundResult res = provider.refund(paymentId.toString(), amount, used);
                attach(refundId, res.providerRefundId());
                return;                                   // final status comes only from the webhook
            } catch (ProviderException e) {
                if (!e.retryable) {
                    tx.executeWithoutResult(s -> state.failed(refundId, "Paystack rejected the refund: " + e.getMessage()));
                    return;
                }
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Refund {} outcome unknown after {} attempts, recovery job will resolve it", refundId, attempt);
                    return;
                }
                sleep(500L * (1L << (attempt - 1)));
            }
        }
    }

    /** Timed-out Paystack calls: ask Paystack whether the refund exists before ever creating another one. */
    public void recoverStuck() {
        List<Stuck> rows = jdbc.query("""
                SELECT id, payment_id, amount FROM refunds
                WHERE status = 'PENDING' AND paystack_refund_id IS NULL
                  AND created_at <= now() - (? * interval '1 second')
                ORDER BY created_at LIMIT 20
                """, (rs, i) -> new Stuck(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3)),
                STUCK_AFTER_SECONDS);
        for (Stuck s : rows) {
            try {
                Optional<PaymentProvider.RefundResult> found =
                        provider.findRefund(s.paymentId().toString(), s.amount(), usedIds(s.paymentId()));
                if (found.isPresent()) attach(s.id(), found.get().providerRefundId());
                else dispatch(s.id(), s.paymentId(), s.amount());
            } catch (Exception e) {
                log.warn("Refund recovery failed for {}: {}", s.id(), e.getMessage());
            }
        }
    }

    private void attach(UUID refundId, String providerRefundId) {
        if (providerRefundId == null) return;
        jdbc.update("UPDATE refunds SET paystack_refund_id = ?, updated_at = now() WHERE id = ? AND paystack_refund_id IS NULL",
                providerRefundId, refundId);
    }

    private Set<String> usedIds(UUID paymentId) {
        return new HashSet<>(jdbc.query(
                "SELECT paystack_refund_id FROM refunds WHERE payment_id = ? AND paystack_refund_id IS NOT NULL",
                (rs, i) -> rs.getString(1), paymentId));
    }

    private static UUID parse(String id) {
        try { return UUID.fromString(id); }
        catch (IllegalArgumentException e) { throw new ApiException("Payment not found", 404, "not_found"); }
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted", 503, "interrupted");
        }
    }
}