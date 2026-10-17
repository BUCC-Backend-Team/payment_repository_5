package Marketly.Payment.Service.controller;

import Marketly.Payment.Service.controller.dto.CreatePaymentRequest;
import Marketly.Payment.Service.controller.dto.CreatePaymentResponse;
import Marketly.Payment.Service.controller.dto.PaymentDetails;
import Marketly.Payment.Service.exception.ApiException;
import Marketly.Payment.Service.payments.Service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CreatePaymentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) CreatePaymentRequest body) {

        if (key == null || key.isBlank()) {
            throw new ApiException("Idempotency-Key header is required", 400, "missing_idempotency_key");
        }
        PaymentService.CreateResult result = service.create(key.trim(), body);

        ResponseEntity.BodyBuilder response = ResponseEntity.status(201);
        if (result.replayed()) response.header("Idempotent-Replayed", "true");
        return response.body(result.response());
    }

    @GetMapping("/{id}")
    public PaymentDetails get(@PathVariable String id) {
        return service.get(id);
    }
}