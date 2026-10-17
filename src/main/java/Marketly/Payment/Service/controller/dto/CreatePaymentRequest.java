package Marketly.Payment.Service.controller.dto;

public record CreatePaymentRequest(String orderId, Number amount, String currency,
                                   String customerEmail, String returnUrl) {
}
