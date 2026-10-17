package Marketly.Payment.Service.controller.dto;

import java.util.UUID;

public record RefundResponse(UUID refundId, long amount, String status) {}