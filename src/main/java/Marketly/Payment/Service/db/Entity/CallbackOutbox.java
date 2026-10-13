package Marketly.Payment.Service.db.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
    public UUID id;
    public UUID paymentId;
    public String type;
    public String payload;
    public PaymentStatus Status;
    public int attempts;
    public Instant nextAttemptTime;
    public String lastError;
    public Instant deliveredAt;

}
