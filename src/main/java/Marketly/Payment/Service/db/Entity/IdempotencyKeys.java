package Marketly.Payment.Service.db.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="idempotency_keys")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class IdempotencyKeys {
    @Id
    public String key;
    public String scope;
    public String requestHash;
    public String responseStatus;
    public String responseBody;
    public UUID paymentId;
    public UUID refundId;
    public Instant createdAt;

}
