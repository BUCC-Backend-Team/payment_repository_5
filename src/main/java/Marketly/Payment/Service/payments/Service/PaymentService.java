package Marketly.Payment.Service.payments.Service;

import Marketly.Payment.Service.controller.dto.CreatePaymentRequest;
import Marketly.Payment.Service.controller.dto.CreatePaymentResponse;
import Marketly.Payment.Service.controller.dto.PaymentDetails;
import Marketly.Payment.Service.db.Entity.IdempotencyKeys;
import Marketly.Payment.Service.db.Entity.Payments;
import Marketly.Payment.Service.db.enums.PaymentStatus;
import Marketly.Payment.Service.db.repository.IdempotencyKeyRepository;
import Marketly.Payment.Service.db.repository.PaymentRepository;
import Marketly.Payment.Service.exception.ApiException;
import Marketly.Payment.Service.exception.ProviderException;
import Marketly.Payment.Service.payments.PaymentProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PaymentService {
    private static final int MAX_INIT_ATTEMPTS = 3;
    private static final Duration REPLAY_WAIT = Duration.ofSeconds(20);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PaymentRepository payments;
    private final IdempotencyKeyRepository keys;
    private final PaymentProvider provider;
    private final TransactionTemplate tx;

    public record CreateResult(CreatePaymentResponse response, boolean replayed) {}
    public record Valid(String orderId, long amount, String currency, String email, String returnUrl) {}
    public record Reservation(Payments created, IdempotencyKeys existing) {}

    public PaymentService(PaymentRepository payments, IdempotencyKeyRepository keys,
                          PaymentProvider provider, PlatformTransactionManager txManager) {
        this.payments = payments;
        this.keys = keys;
        this.provider = provider;
        this.tx = new TransactionTemplate(txManager);
    }

    public CreateResult create(String rawKey, CreatePaymentRequest req) {
        Valid v = validate(req);
        String keyId = "payment:create:" + rawKey;
        String hash = hash(v);

        // Step 1: short transaction. Reserve the key and save the pending payment together.
        Reservation r;
        try {
            r = tx.execute(status -> reserve(keyId, hash, v));
        } catch (DataIntegrityViolationException e) {
            // The partial unique index refused a second live payment for this order.
            throw new ApiException("This order already has a pending or succeeded payment", 409, "payment_exists");
        }
        if (r.existing() != null) {
            return replay(keyId, r.existing(), hash);
        }

        // Step 2: call Paystack with NO transaction open.
        Payments p = r.created();
        String checkoutUrl = startCheckout(p, keyId);

        // Step 3: store the checkout URL and mark the key complete, atomically.
        CreatePaymentResponse body = tx.execute(status -> {
            Payments fresh = payments.findById(p.id).orElseThrow();
            fresh.checkout_url = checkoutUrl;
            fresh.updated_at = Instant.now();
            keys.findById(keyId).orElseThrow().setResponseStatus(201);
            return createResponse(fresh);
        });
        return new CreateResult(body, false);
    }

    private Reservation reserve(String keyId, String hash, Valid v) {
        UUID paymentId = UUID.randomUUID();


        if (keys.insertIfAbsent(keyId, hash, paymentId) == 0) {
            IdempotencyKeys existing = keys.findById(keyId).orElseThrow(() ->
                    new ApiException("Retry the request", 409, "request_in_progress"));
            return new Reservation(null, existing);
        }

        Instant now = Instant.now();
        Payments p = new Payments();
        p.id = paymentId;
        p.order_id = v.orderId();
        p.amount = v.amount();
        p.setCurrency(v.currency());
        p.customer_email = v.email();
        p.return_url = v.returnUrl();
        p.setStatus(PaymentStatus.PENDING);
        p.amount_refunded = 0;
        p.late = false;
        p.created_at = now;
        p.updated_at = now;
        payments.saveAndFlush(p);
        return new Reservation(p, null);
    }

    private String startCheckout(Payments p, String keyId) {
        try {
            return initialize(new PaymentProvider.InitRequest(p.id.toString(), p.customer_email,
                    p.amount, p.getCurrency(), p.return_url)).checkoutUrl();
        } catch (RuntimeException e) {
            // Nobody has a checkout URL for this reference, so it can never be paid.
            // Release the order and the key so the client can retry cleanly.
            release(p.id, keyId);
            if (e instanceof ProviderException) {
                throw new ApiException("Could not start the payment, please retry", 502, "provider_error");
            }
            throw e;
        }
    }


    private PaymentProvider.InitResult initialize(PaymentProvider.InitRequest req) {
        for (int attempt = 1; ; attempt++) {
            try {
                return provider.initializeTransaction(req);
            } catch (ProviderException e) {
                if (!e.retryable || attempt == MAX_INIT_ATTEMPTS) throw e;
                sleep(200L * attempt);
            }
        }
    }

    private void release(UUID paymentId, String keyId) {
        tx.executeWithoutResult(status -> {
            keys.deleteById(keyId);
            payments.deleteById(paymentId);
        });
    }

    private CreateResult replay(String keyId, IdempotencyKeys seen, String hash) {
        if (!seen.getRequestHash().equals(hash)) {
            throw new ApiException("This Idempotency-Key was already used with a different request body",
                    422, "idempotency_key_reused");
        }
        IdempotencyKeys k = seen;
        long deadline = System.nanoTime() + REPLAY_WAIT.toNanos();
        while (k.getResponseStatus() == null) {
            if (System.nanoTime() > deadline) {
                throw new ApiException("The original request is still running", 409, "request_in_progress");
            }
            sleep(100);
            k = keys.findById(keyId).orElseThrow(() ->
                    new ApiException("The original request failed, retry with the same key", 502, "provider_error"));
        }
        Payments p = payments.findById(k.getPaymentId()).orElseThrow();
        return new CreateResult(createResponse(p), true);
    }

    public PaymentDetails get(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Payment not found", 404, "not_found");
        }
        Payments p = payments.findById(uuid)
                .orElseThrow(() -> new ApiException("Payment not found", 404, "not_found"));
        return new PaymentDetails(p.id, p.order_id, p.getStatus().api(), p.amount,
                p.getCurrency(), p.checkout_url, p.channel, p.amount_refunded);
    }



    //helper functions
    private static CreatePaymentResponse createResponse(Payments p) {
        return new CreatePaymentResponse(p.id, p.order_id, PaymentStatus.PENDING.api(),
                p.amount, p.getCurrency(), p.checkout_url);
    }

    public static Valid validate(CreatePaymentRequest r) {
        if (r == null) throw bad("this request requires a body");

        String orderId = trim(r.orderId());
        if (orderId.isEmpty()) throw bad("orderId is required");

        if (r.amount() <= 0) throw bad("amount must be a whole number of kobo greater than 0");

        String currency = trim(r.currency()).toUpperCase(Locale.ROOT);
        if (!currency.equals("NGN")) throw bad("currency must be NGN");

        String email = trim(r.customerEmail());
        if (!EMAIL.matcher(email).matches()) throw bad("customerEmail is invalid");

        String returnUrl = trim(r.returnUrl());
        try {
            URI uri = URI.create(returnUrl);
            boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!web || uri.getHost() == null) throw bad("returnUrl must be an http(s) URL");
        } catch (IllegalArgumentException e) {
            throw bad("returnUrl must be an http(s) URL");
        }
        return new Valid(orderId, r.amount(), currency, email, returnUrl);
    }

    private static String hash(Valid v) {
        try {
            String canonical = String.join("\n", v.orderId(), Long.toString(v.amount()),
                    v.currency(), v.email(), v.returnUrl());
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static ApiException bad(String message) {
        return new ApiException(message, 400, "invalid_request");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted", 503, "interrupted");
        }
    }
}