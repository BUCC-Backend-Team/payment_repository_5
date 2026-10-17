package Marketly.Payment.Service.jobs;

import Marketly.Payment.Service.config.AppProps;
import Marketly.Payment.Service.db.Entity.Payments;
import Marketly.Payment.Service.db.enums.PaymentStatus;
import Marketly.Payment.Service.db.repository.IdempotencyKeyRepository;
import Marketly.Payment.Service.db.repository.PaymentRepository;
import Marketly.Payment.Service.exception.ProviderException;
import Marketly.Payment.Service.payments.PaymentProvider;
import Marketly.Payment.Service.payments.Service.PaymentStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);
    private static final int BATCH = 20;
    private static final long STUCK_AFTER_SECONDS = 120;

    private final AppProps props;
    private final PaymentProvider provider;
    private final PaymentRepository payments;
    private final IdempotencyKeyRepository keys;
    private final PaymentStateService state;
    private final TransactionTemplate tx;

    public ExpiryJob(AppProps props, PaymentProvider provider, PaymentRepository payments,
                     IdempotencyKeyRepository keys, PaymentStateService state,
                     PlatformTransactionManager txManager) {
        this.props = props;
        this.provider = provider;
        this.payments = payments;
        this.keys = keys;
        this.state = state;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelay = 2000)
    public void run() {
        try {
            Instant now = Instant.now();
            List<UUID> due = payments.findExpirable(
                    now.minusSeconds(props.paymentTimeoutSeconds()),
                    now.minusSeconds(STUCK_AFTER_SECONDS),
                    BATCH);
            for (UUID id : due) {
                try {
                    check(id);
                } catch (Exception e) {
                    log.error("Expiry check failed for {}", id, e);   // one bad payment must not block the rest
                }
            }

            // Recheck recently expired payments for late transfers whose webhook was lost.
            List<UUID> expired = payments.findRecheckableExpired(
                    now.minus(Duration.ofHours(24)), now.minusSeconds(60), BATCH);
            for (UUID id : expired) {
                try {
                    recheckExpired(id);
                } catch (Exception e) {
                    log.error("Expired recheck failed for {}", id, e);
                }
            }
        } catch (Exception e) {
            log.error("Expiry job loop failed", e);
        }
    }

    private void check(UUID id) {
        Payments p = payments.findById(id).orElse(null);
        if (p == null || p.getStatus() != PaymentStatus.PENDING) return;

        PaymentProvider.VerifyTransaction v = null;
        try {
            v = provider.verify(id.toString());
        } catch (ProviderException e) {
            if (Integer.valueOf(404).equals(e.httpStatus)) {
                v = null;
            } else {
                log.warn("Verify failed for {} ({}), will retry next run", id, e.getMessage());
                return;                         // never expire on a network error or 5xx
            }
        }

        if (v != null && v.succeeded()) {
            boolean matches = v.amount() == p.amount && p.getCurrency().equalsIgnoreCase(v.currency());
            if (!matches) {
                log.error("Payment {} is paid on Paystack but amount/currency differ, leaving for manual review", id);
                return;
            }
            Instant paidAt = v.paidAt() != null ? v.paidAt() : Instant.now();
            String channel = v.channel();
            // A missed webhook: Paystack says paid, so we catch up.
            tx.executeWithoutResult(s -> state.succeed(id, paidAt, channel));
            return;
        }

        // Not paid (abandoned, pending, failed, or unknown to Paystack): expire it.
        boolean neverGotCheckoutUrl = p.getCheckout_url() == null;
        tx.executeWithoutResult(s -> {
            if (state.expire(id) && neverGotCheckoutUrl) {
                keys.deleteCreateKeyForPayment(id);
            }
        });
    }

    /** A late bank transfer whose webhook was lost: EXPIRED -> SUCCEEDED with late=true. */
    private void recheckExpired(UUID id) {
        Payments p = payments.findById(id).orElse(null);
        if (p == null || p.getStatus() != PaymentStatus.EXPIRED) return;

        PaymentProvider.VerifyTransaction v;
        try {
            v = provider.verify(id.toString());
        } catch (ProviderException e) {
            v = null;                                   // unknown to Paystack or unreachable, try again later
        }

        if (v != null && v.succeeded()
                && v.amount() == p.amount && p.getCurrency().equalsIgnoreCase(v.currency())) {
            Instant paidAt = v.paidAt() != null ? v.paidAt() : Instant.now();
            String channel = v.channel();
            tx.executeWithoutResult(s -> state.succeed(id, paidAt, channel));
            return;
        }
        // Not paid: push it to the back of the queue so Paystack is checked about once a minute per payment.
        tx.executeWithoutResult(s -> payments.touchExpired(id));
    }
}