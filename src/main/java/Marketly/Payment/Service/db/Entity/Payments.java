package Marketly.Payment.Service.db.Entity;

import Marketly.Payment.Service.db.enums.PaymentStatus;
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
    public UUID id;

    @Column(nullable = false)
    public String order_id;

    @Column(nullable = false)
    public long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    public String customer_email;

    @Column(nullable = false)
    public String return_url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    public String channel;

    @Column(nullable = false)
    private String returnUrl;

    @Column(nullable = false)
    public long amount_refunded;

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
        return amount == payments.amount && amount_refunded == payments.amount_refunded && Objects.equals(id, payments.id) && Objects.equals(order_id, payments.order_id) && Objects.equals(currency, payments.currency) && Objects.equals(customer_email, payments.customer_email) && Objects.equals(return_url, payments.return_url) && status == payments.status && Objects.equals(channel, payments.channel) && Objects.equals(returnUrl, payments.returnUrl) && Objects.equals(checkout_url, payments.checkout_url) && Objects.equals(refunds, payments.refunds) && Objects.equals(late, payments.late) && Objects.equals(paid_at, payments.paid_at) && Objects.equals(created_at, payments.created_at) && Objects.equals(updated_at, payments.updated_at);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(order_id);
        result = 31 * result + Long.hashCode(amount);
        result = 31 * result + Objects.hashCode(currency);
        result = 31 * result + Objects.hashCode(customer_email);
        result = 31 * result + Objects.hashCode(return_url);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(channel);
        result = 31 * result + Objects.hashCode(returnUrl);
        result = 31 * result + Long.hashCode(amount_refunded);
        result = 31 * result + Objects.hashCode(checkout_url);
        result = 31 * result + Objects.hashCode(refunds);
        result = 31 * result + Objects.hashCode(late);
        result = 31 * result + Objects.hashCode(paid_at);
        result = 31 * result + Objects.hashCode(created_at);
        result = 31 * result + Objects.hashCode(updated_at);
        return result;
    }
}
