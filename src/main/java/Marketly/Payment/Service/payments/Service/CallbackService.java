package Marketly.Payment.Service.callbacks;

import Marketly.Payment.Service.db.Entity.Payments;
import Marketly.Payment.Service.db.repository.CallbackOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CallbackService {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CallbackOutboxRepository outbox;

    public CallbackService(CallbackOutboxRepository outbox) { this.outbox = outbox; }

    /** MANDATORY: refuses to run unless the caller already opened the state-change transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Payments p, String type) {
        UUID id = UUID.randomUUID();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", p.getId().toString());
        data.put("orderId", p.order_id);
        data.put("status", p.getStatus().api());
        data.put("amount", p.amount);
        data.put("amountRefunded", p.amount_refunded);
        data.put("currency", p.getCurrency());
        data.put("late", Boolean.TRUE.equals(p.late));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("type", type);
        body.put("createdAt", Instant.now().toString());
        body.put("data", data);

        outbox.insert(id, p.getId(), type, JSON.writeValueAsString(body));
    }
}