package Marketly.Payment.Service.payments;

import Marketly.Payment.Service.controller.dto.CreatePaymentRequest;
import Marketly.Payment.Service.controller.dto.CreatePaymentResponse;
import Marketly.Payment.Service.db.repository.IdempotencyKeyRepository;
import Marketly.Payment.Service.db.repository.PaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;


@Service
public class PaymentService {
    private static final int MAX_INIT_ATTEMPTS = 3;
    private static final Duration REPLAY_WAIT = Duration.ofSeconds(20);
    private final PaymentRepository payments;
    private final IdempotencyKeyRepository keys;
    private final PaymentProvider provider;
    private  final TransactionTemplate tx;

    public record createResult(CreatePaymentResponse response,boolean replayed){}
    public record valid(String orderId,long amount,String currency,String returnUrl,String email){}

    public PaymentService(PaymentRepository payments, IdempotencyKeyRepository keys, PaymentProvider provider,
                          PlatformTransactionManager txManager) {
        this.payments = payments;
        this.keys = keys;
        this.provider = provider;
        this.tx = new TransactionTemplate(txManager);
    }
//create transaction
    public createResult createResult(String rawKey, CreatePaymentRequest createPaymentRequest){
//step 1
        valid valid =Val
    }



}
