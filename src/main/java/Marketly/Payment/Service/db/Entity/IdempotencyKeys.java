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

    @Column(nullable = false,updatable = false)
    public String requestHash;

    @Column(nullable = false)
    public String responseStatus;
    public String responseBody;

    @Column(nullable = false)
    public UUID paymentId;

    @Column(nullable = false)
    public UUID refundId;
    public Instant createdAt;

}
