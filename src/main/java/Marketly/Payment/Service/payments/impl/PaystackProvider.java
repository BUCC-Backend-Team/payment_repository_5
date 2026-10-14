package Marketly.Payment.Service.payments.impl;

import Marketly.Payment.Service.config.AppProps;
import Marketly.Payment.Service.exception.ProviderException;
import Marketly.Payment.Service.payments.PaymentProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class PaystackProvider implements PaymentProvider {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public PaystackProvider( AppProps props) {
        HttpClient jdk= HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().
                baseUrl("https://api.paystack.co")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+props.paystack_secret_key())
                .build();
    }
    @Override
    public InitResult initializeTransaction(InitRequest r) {
        Map<String, Object> body = Map.of(
                "email", r.email(),
                "amount", r.amount(),
                "currency", r.currency(),
                "reference", r.reference(),
                "callback_url", r.returnUrl());

        Map<String, Object> d = data( http.post()
                .uri("/transaction/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP));

        return new InitResult(str(d.get("reference")), str(d.get("authorization_url")), str(d.get("access_code")));
    }

    @Override
    public VerifyTransaction verify(String reference) {
        Map<String, Object> d = data( http.get()
                .uri("/transaction/verify/{ref}", reference)
                .retrieve()
                .body(MAP));

        String paidAt = str(d.get("paid_at"));
        return new VerifyTransaction(
                str(d.get("reference")),
                str(d.get("status")),
                ((Number) d.get("amount")).longValue(),
                str(d.get("currency")),
                str(d.get("channel")),
                paidAt == null ? null : Instant.parse(paidAt));
    }

    @Override
    public RefundResult refund(String reference, long amountKobo) {
        Map<String, Object> body = Map.of("transaction", reference, "amount", amountKobo);

        Map<String, Object> d = data( http.post()
                .uri("/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP));


        long amount = d.get("amount") instanceof Number n ? n.longValue() : amountKobo;
        return new RefundResult(str(d.get("id")), str(d.get("status")), amount);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> res) {
        if (res == null || !Boolean.TRUE.equals(res.get("status")) || !(res.get("data") instanceof Map<?, ?> d)) {
            throw new ProviderException("Paystack rejected this request: "
                    + (res == null ? "empty" : res.get("message")), false, null);
        }
        return (Map<String, Object>) d;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
