package Marketly.Payment.Service.db.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="refunds")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Refunds {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false)
    public UUID payment_id;
    public float amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PaymentStatus status;
    public String Refund_id;
    public String failure_reason;
    public Instant created_at;
    public Instant updated_at;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Refunds refunds = (Refunds) o;
        return Float.compare(amount, refunds.amount) == 0 && Objects.equals(id, refunds.id) && Objects.equals(payment_id, refunds.payment_id) && status == refunds.status && Objects.equals(Refund_id, refunds.Refund_id) && Objects.equals(failure_reason, refunds.failure_reason) && Objects.equals(created_at, refunds.created_at) && Objects.equals(updated_at, refunds.updated_at);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(payment_id);
        result = 31 * result + Float.hashCode(amount);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(Refund_id);
        result = 31 * result + Objects.hashCode(failure_reason);
        result = 31 * result + Objects.hashCode(created_at);
        result = 31 * result + Objects.hashCode(updated_at);
        return result;
    }
}
