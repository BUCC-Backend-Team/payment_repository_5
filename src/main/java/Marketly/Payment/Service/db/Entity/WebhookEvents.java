package Marketly.Payment.Service.db.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="webhook_events")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WebhookEvents {
    @Id
    public String deduplicationKey;
    public String eventType;
    public UUID paymentId;
    public Instant processedAt;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        WebhookEvents that = (WebhookEvents) o;
        return Objects.equals(deduplicationKey, that.deduplicationKey) && Objects.equals(eventType, that.eventType) && Objects.equals(paymentId, that.paymentId) && Objects.equals(processedAt, that.processedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(deduplicationKey);
        result = 31 * result + Objects.hashCode(eventType);
        result = 31 * result + Objects.hashCode(paymentId);
        result = 31 * result + Objects.hashCode(processedAt);
        return result;
    }
}
