package Marketly.Payment.Service.payments;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;


@Service
public class PaymentService {
    private static final int MAX_INIT_ATTEMPTS = 3;
    private static final Duration REPLAY_WAIT = Duration.ofSeconds(20);
    private final PaymentRepository payments;
    private final IdempotencyKeyRepository keys;
    private final PaymentProvider provider;
    private final TransactionTemplate tx;

    public record CreateResult(CreatePaymentResponse response, boolean replayed) {}
    public record Valid(String orderId, long amount, String currency, String email, String returnUrl) {}
    public record Reservation(Payments created, IdempotencyKeys existing) {}

    public PaymentService(PaymentRepository payments, IdempotencyKeyRepository keys, PaymentProvider provider,
                          PlatformTransactionManager txManager) {
        this.payments = payments;
        this.keys = keys;
        this.provider = provider;
        this.tx = new TransactionTemplate(txManager);
    }

    //create transaction
    public createResult createResult(String rawKey, CreatePaymentRequest createPaymentRequest) {

    }

    // once a payment starts a reservation in the db via the idempotency key
    //so if a duplicate request lands its returns the same value no duplicates
    private Reservation reserve(String keyId, String hash, Valid valid) {
        UUID paymentId=UUID.randomUUID();
        if(keys.insertIfAbsent(keyId,hash,paymentId)==0){
            IdempotencyKeys existing=keys.findById(keyId).orElseThrow(()->
                    new ApiException("retry the request",409,"request in progress"));
            return new Reservation(null,existing);
        }
        Instant now = Instant.now();
        Payments p = new Payments();
        p.id = paymentId;
        p.order_id = valid.orderId();
        p.amount = valid.amount();
        p.setCurrency(valid.currency());
        p.customer_email = valid.email();
        p.return_url = valid.returnUrl();
        p.setStatus(PaymentStatus.PENDING);
        p.amount_refunded = 0;
        p.late = false;
        p.created_at = now;
        p.updated_at = now;
        payments.saveAndFlush(p);
        return new Reservation(p, null);
    }
    private String startCheckout(Payments payment,String keyId){
    return "checkout";
    }

    private void release(UUID paymentId, String keyId) {
        tx.executeWithoutResult(status -> {
            keys.deleteById(keyId);
            payments.deleteById(paymentId);
        });
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



    private CreateResult replay(String keyId, IdempotencyKeys seen, String hash) {
        if (!seen.getRequestHash().equals(hash)) {
            throw new ApiException("This Idempotency-Key was already used by a different request body",
                    422, "idempotency_key_reused");
        }

        IdempotencyKeys key = seen;
        long deadline = System.nanoTime() + REPLAY_WAIT.toNanos();
        while (key.getResponseStatus() == null) {
            if (System.nanoTime() > deadline) {
                throw new ApiException("The original request is still running", 409, "request_in_progress");
            }
            sleep(100);
            key = keys.findById(keyId).orElseThrow(() ->
                    new ApiException("The original request failed, retry with the same key", 502, "provider_error"));
        }
        Payments p = payments.findById(key.getPaymentId()).orElseThrow();
        return new CreateResult(createResponse(p), true);
    }


    private PaymentProvider.InitResult initialize(PaymentProvider.InitRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return provider.initializeTransaction(request);
            } catch (ProviderException exception) {
                if (!exception.retryable || attempt == MAX_INIT_ATTEMPTS) throw exception;
                sleep(200L * attempt);
            }
        }
    }
    private static CreatePaymentResponse createResponse(Payments p) {
        return new CreatePaymentResponse(p.id, p.order_id, PaymentStatus.PENDING.api(),
                p.amount, p.getCurrency(), p.checkout_url);
    }
    public static Valid validate(CreatePaymentRequest request) {
        if(request == null) throw bad("this request requires a body");
        String orderId=request.orderId();
        if (request.orderId().isEmpty()) {
            throw bad("order Id cannot be null");
        }
        //if ( request.amount()){ throw bad("amount is required");}
        long amount;

        try {
            amount = request.amount();
        } catch (ArithmeticException e) {
            throw bad("amount must be a whole number of kobo");
        }
        if(amount<=0){
            throw bad("amount must be greater than 0");
        }
        String currency=request.currency().trim().toUpperCase();
        if(!currency.equals("NGN")){throw bad("currency must be NGN");}

        String email=request.customerEmail().trim();
        String returnUrl=request.returnUrl().trim();
        try {
            URI uri = URI.create(returnUrl);
            boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!web || uri.getHost() == null) throw bad("returnUrl must be an http(s) URL");
        } catch (IllegalArgumentException e) {
            throw bad("uri must be https");
        }
        return new Valid(orderId, amount, currency, email, returnUrl);
    }

    private static String hash(Valid valid){
        try{
            String canonical = String.join("\n", valid.orderId(), Long.toString(valid.amount()),
                    valid.currency(), valid.email(), valid.returnUrl());
            byte[] digest= MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted", 503,"interrupted" );
        }
    }

    private static ApiException bad(String message) {
        return new ApiException(message, 400,"invalid_request" );

    }
}
