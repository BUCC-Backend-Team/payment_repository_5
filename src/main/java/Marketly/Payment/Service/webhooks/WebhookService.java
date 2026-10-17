package Marketly.Payment.Service.webhooks;

import Marketly.Payment.Service.config.AppProps;
import Marketly.Payment.Service.db.Entity.Payments;
import Marketly.Payment.Service.db.repository.PaymentRepository;
import Marketly.Payment.Service.db.repository.WebhookEventRepository;
import Marketly.Payment.Service.exception.ApiException;
import Marketly.Payment.Service.exception.ProviderException;
import Marketly.Payment.Service.payments.PaymentProvider;
import Marketly.Payment.Service.payments.Service.PaymentStateService;
import Marketly.Payment.Service.refunds.RefundStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AppProps props;
    private final PaymentProvider provider;
    private final PaymentRepository payments;
    private final WebhookEventRepository events;
    private final PaymentStateService paymentState;
    private final RefundStateService refundState;
    private final TransactionTemplate tx;

    public WebhookService(AppProps props, PaymentProvider provider, PaymentRepository payments,
                          WebhookEventRepository events, PaymentStateService paymentState,
                          RefundStateService refundState, PlatformTransactionManager txManager) {
        this.props = props;
        this.provider = provider;
        this.payments = payments;
        this.events = events;
        this.paymentState = paymentState;
        this.refundState = refundState;
        this.tx = new TransactionTemplate(txManager);
    }

    public void handle(byte[] raw, String signature) {
        verifySignature(raw, signature);

        Map<String, Object> event = parse(raw);
        String type = str(event.get("event"));
        if ("charge.success".equals(type)) {
            onChargeSuccess(asMap(event.get("data")));
        } else if ("refund.processed".equals(type) || "refund.failed".equals(type)) {
            onRefundEvent(type, asMap(event.get("data")));
        }
        // Anything else: acknowledged with 200 and ignored.
    }

    private void onChargeSuccess(Map<String, Object> data) {
        String reference = str(data.get("reference"));
        String dataId = str(data.get("id"));
        if (reference == null || dataId == null) return;

        String key = "charge.success:" + dataId;
        if (events.existsById(key)) return;                 // cheap duplicate exit, no Paystack call

        UUID paymentId;
        try { paymentId = UUID.fromString(reference); }
        catch (IllegalArgumentException e) { return; }      // not one of ours

        Payments p = payments.findById(paymentId).orElse(null);
        if (p == null) return;

        // Slow network call happens BEFORE the transaction so no DB connection is held open.
        PaymentProvider.VerifyTransaction v;
        try {
            v = provider.verify(reference);
        } catch (ProviderException e) {
            throw new ApiException("Could not verify with Paystack", 503, "verify_unavailable");
        }

        if (!v.succeeded()
                || v.amount() != p.amount
                || !p.getCurrency().equalsIgnoreCase(v.currency())) {
            log.warn("charge.success for {} failed verification (status/amount/currency mismatch)", reference);
            return;
        }

        Instant paidAt = v.paidAt() != null ? v.paidAt() : Instant.now();
        String channel = v.channel();

        tx.executeWithoutResult(status -> {
            // Dedup row and state change commit together, or neither does.
            if (events.insertIfAbsent(key, "charge.success", paymentId) == 0) return;
            paymentState.succeed(paymentId, paidAt, channel);
        });
    }

    private void onRefundEvent(String type, Map<String, Object> data) {
        String dataId = str(data.get("id"));
        UUID paymentId = paymentIdFrom(data);
        if (dataId == null || paymentId == null) return;

        String key = type + ":" + dataId;
        if (events.existsById(key)) return;
        if (!payments.existsById(paymentId)) return;        // not one of ours

        long amount = data.get("amount") instanceof Number n ? n.longValue() : -1;

        tx.executeWithoutResult(status -> {
            if (events.insertIfAbsent(key, type, paymentId) == 0) return;

            UUID refundId = refundState.findRefundId(paymentId, dataId, amount);
            if (refundId == null) {
                log.warn("{} for payment {} matches no pending refund, ignoring", type, paymentId);
                return;
            }
            if ("refund.processed".equals(type)) refundState.processed(refundId);
            else refundState.failed(refundId, "Paystack reported refund.failed");
        });
    }

    /** Paystack names the original transaction reference differently across payloads. */
    private static UUID paymentIdFrom(Map<String, Object> data) {
        String ref = str(data.get("transaction_reference"));
        if (ref == null) ref = str(asMap(data.get("transaction")).get("reference"));
        if (ref == null) ref = str(data.get("reference"));
        if (ref == null) return null;
        try { return UUID.fromString(ref); }
        catch (IllegalArgumentException e) { return null; }
    }

    private void verifySignature(byte[] raw, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new ApiException("Missing signature", 400, "invalid_signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(props.paystackSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            String expected = HexFormat.of().formatHex(mac.doFinal(raw));
            boolean ok = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            if (!ok) throw new ApiException("Invalid signature", 400, "invalid_signature");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(byte[] raw) {
        try {
            Object o = JSON.readValue(raw, Map.class);
            return (Map<String, Object>) o;
        } catch (Exception e) {
            throw new ApiException("Malformed body", 400, "invalid_body");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}