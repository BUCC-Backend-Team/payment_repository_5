package Marketly.Payment.Service.controller.dto;

import java.util.UUID;

public record PaymentDetails(UUID paymentId, String orderId, String status, long amount,
                             String currency, String checkoutUrl, String channel, long amountRefunded) {
}
