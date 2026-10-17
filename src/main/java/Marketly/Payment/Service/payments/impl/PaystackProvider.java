package Marketly.Payment.Service.payments.impl;

import Marketly.Payment.Service.config.AppProps;
import Marketly.Payment.Service.exception.ProviderException;
import Marketly.Payment.Service.payments.PaymentProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
                .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+props.paystackSecretKey())
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
    public Optional<RefundResult> findRefund(String reference, long amountKobo, Set<String> usedRefundIds) {
        Map<String, Object> res = send(() -> http.get()
                                .uri(u -> u.path("/refund").queryParam("reference", reference).queryParam("perPage", 50).build())
                                .retrieve()
                                .body(MAP));
                if (res == null || !(res.get("data") instanceof List<?> rows)) return Optional.empty();
                for (Object o : rows) {
                        if (!(o instanceof Map<?, ?> row)) continue;
                        String id = str(row.get("id"));
                        String status = str(row.get("status"));
                        long amount = row.get("amount") instanceof Number n ? n.longValue() : -1;
                        if (id == null || usedRefundIds.contains(id) || "failed".equals(status) || amount != amountKobo) continue;
                        return Optional.of(new RefundResult(id, status, amount));
                    }
                return Optional.empty();
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

    public RefundResult refund(String reference, long amountKobo, Set<String> usedRefundIds) {
                try {
                        Map<String, Object> body = Map.of("transaction", reference, "amount", amountKobo);
                        Map<String, Object> d = data(send(() -> http.post()
                                        .uri("/refund")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body)
                                        .retrieve()
                                        .body(MAP)));
                        long amount = d.get("amount") instanceof Number n ? n.longValue() : amountKobo;
                        return new RefundResult(str(d.get("id")), str(d.get("status")), amount);
                    } catch (ProviderException exception
                ) {
                        if (!exception.retryable) throw exception;
                        try {
                                Optional<RefundResult> existing = findRefund(reference, amountKobo, usedRefundIds);
                               if (existing.isPresent()) return existing.get();
                            }catch (ProviderException lookupFailed) {}
                        throw exception;
                    }
            }
    private static <T> T send(Supplier<T> call) {
                try {
                        return call.get();
                    } catch (RestClientResponseException e) {
                        int status = e.getStatusCode().value();
                        throw new ProviderException("Paystack responded " + status, status >= 500, e, status);
                    } catch (ResourceAccessException e) {
                        throw new ProviderException("Could not reach Paystack", true, e);
                    }
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
