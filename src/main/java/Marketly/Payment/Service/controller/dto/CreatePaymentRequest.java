package Marketly.Payment.Service.controller.dto;

public record CreatePaymentRequest(String orderId, long amount, String currency,
                                   String customerEmail, String returnUrl) {
}
