package Marketly.Payment.Service.db.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="callback_outbox")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CallbackOutbox {
    @Id
    public UUID id;
    @Column(nullable = false)
    public UUID paymentId;

    @Column(nullable = false)
    public String type;
    public String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentStatus Status;

    public int attempts;
    public Instant nextAttemptTime;
    public String lastError;
    public Instant deliveredAt;

}
