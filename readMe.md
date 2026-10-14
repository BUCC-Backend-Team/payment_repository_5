## PAYMENT SERVICE (MARKETLY)
This is a payment system to be integrated into Marketly, an existing ecommerce platform.

STACK
- Spring Boot for backend logic
- Docker for running PostgreSQL locally
- Paystack API for payment processing
- PostgreSQL for database

FEATURES
- Payment processing and status tracking
- Refund management
- Webhook event handling
- Idempotency key support
- Callback outbox  for reliable event delivery

ENTITIES
- Payments
- PaymentStatus
- Refunds
- WebhookEvents
- IdempotencyKeys
- CallbackOutbox

My GOAL
To provide a reliable, scalable, and auditable payment service for online transactions within the Marketly platform.
