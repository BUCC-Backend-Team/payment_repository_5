package Marketly.Payment.Service.controller;

import Marketly.Payment.Service.webhooks.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {
    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) { this.webhooks = webhooks; }

    @PostMapping("/webhooks/paystack")
    public ResponseEntity<Void> paystack(
            @RequestBody byte[] raw,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {
        webhooks.handle(raw, signature);
        return ResponseEntity.ok().build();
    }
}