package Marketly.Payment.Service.db.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="payment")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Payments {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false)
    public String order_id;

    @Column(nullable = false)
    public int amount;

    @Column(nullable = false)
    public String customer_email;

    @Column(nullable = false)
    public String return_url;

    @Column(nullable = false)
    public String Status;

    @Column(nullable = false)
    public String channel;

    @Column(nullable = false)
    public long amount_refunded;

    @Column(nullable = false)
    public String checkout_url;

    @OneToMany(mappedBy = "payment")
    public List<Refunds> refunds=new ArrayList<>();

    @Column(nullable = false)
    public Boolean late;
    public Instant paid_at;
    public Instant created_at;
    public Instant updated_at;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Payments payments = (Payments) o;
        return Float.compare(amount, payments.amount) == 0 && Float.compare(amount_refunded, payments.amount_refunded) == 0 && Objects.equals(id, payments.id) && Objects.equals(order_id, payments.order_id) && Objects.equals(customer_email, payments.customer_email) && Objects.equals(return_url, payments.return_url) && Objects.equals(Status, payments.Status) && Objects.equals(channel, payments.channel) && Objects.equals(checkout_url, payments.checkout_url) && Objects.equals(refunds, payments.refunds) && Objects.equals(late, payments.late) && Objects.equals(paid_at, payments.paid_at) && Objects.equals(created_at, payments.created_at) && Objects.equals(updated_at, payments.updated_at);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(order_id);
        result = 31 * result + Float.hashCode(amount);
        result = 31 * result + Objects.hashCode(customer_email);
        result = 31 * result + Objects.hashCode(return_url);
        result = 31 * result + Objects.hashCode(Status);
        result = 31 * result + Objects.hashCode(channel);
        result = 31 * result + Float.hashCode(amount_refunded);
        result = 31 * result + Objects.hashCode(checkout_url);
        result = 31 * result + Objects.hashCode(refunds);
        result = 31 * result + Objects.hashCode(late);
        result = 31 * result + Objects.hashCode(paid_at);
        result = 31 * result + Objects.hashCode(created_at);
        result = 31 * result + Objects.hashCode(updated_at);
        return result;
    }
}
