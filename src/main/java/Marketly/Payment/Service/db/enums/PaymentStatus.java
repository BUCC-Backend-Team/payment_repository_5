package Marketly.Payment.Service.db.enums;

import java.util.Locale;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    EXPIRED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public String api(){
        return name().toLowerCase();
    }
    public boolean canMoveTo(PaymentStatus next) {
        return switch (this) {
            case PENDING            -> next == SUCCEEDED || next == EXPIRED;
            case EXPIRED            -> next == SUCCEEDED;
            case SUCCEEDED, PARTIALLY_REFUNDED -> next == PARTIALLY_REFUNDED || next == REFUNDED;
            case REFUNDED           -> false;
        };
}
 }
