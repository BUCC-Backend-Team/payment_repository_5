package Marketly.Payment.Service.payments;

import Marketly.Payment.Service.controller.dto.CreatePaymentRequest;
import Marketly.Payment.Service.controller.dto.CreatePaymentResponse;
import Marketly.Payment.Service.db.Entity.IdempotencyKeys;
import Marketly.Payment.Service.db.Entity.Payments;
import Marketly.Payment.Service.db.repository.IdempotencyKeyRepository;
import Marketly.Payment.Service.db.repository.PaymentRepository;

import Marketly.Payment.Service.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;


@Service
public class PaymentService {
    private static final int MAX_INIT_ATTEMPTS = 3;
    private static final Duration REPLAY_WAIT = Duration.ofSeconds(20);
    private final PaymentRepository payments;
    private final IdempotencyKeyRepository keys;
    private final PaymentProvider provider;
    private final TransactionTemplate tx;

    public record createResult(CreatePaymentResponse response, boolean replayed) {}
    public record Valid(String orderId, long amount, String currency, String returnUrl, String email) {}
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
        if(keys.insertIfAbsent(keyId,hash,valid)==0){

        }

    }

    public static Valid validate(CreatePaymentRequest request) {
        if(request == null) throw bad("this request requires a body");
        String orderId=request.orderId();
        if (!request.orderId().isEmpty()) {
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
        String currency=request.currency().trim().toUpperCase(Locale.ROOT);
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
            throw new ApiException("interrupted", 503, "Request interrupted");
        }
    }

    private static ApiException bad(String message) {
        return new ApiException("invalid_request", 400, message);

    }
}
