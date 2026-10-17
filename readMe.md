# (Marketly) Payment Service

A payment service built for integration with Marketly, an existing e-commerce platform.

## My Goal

Provide a reliable, scalable, and auditable way to process online payments within Marketly.

## Stack

- Spring Boot for backend logic
- PostgreSQL for persistent data
- Docker for running PostgreSQL locally
- Paystack API for payment processing

## Features

- Payment processing and status tracking
- Refund management
- Webhook event handling
- Idempotency-key support
- Callback outbox for reliable event delivery

## Data Model

The service uses the following entities:

- `Payments`
- `PaymentStatus`
- `Refunds`
- `WebhookEvents`
- `IdempotencyKeys`
- `CallbackOutbox`

## Key Components

### API key filter

APIKEY FILTER checks each request for a valid API key.

### Payment provider

The PAYMENT PROVIDER interface defines operations to initialize, verify, and refund transactions. Initialization returns a reference that uniquely identifies the payment, along with checkout details such as the access code and return URL. PAYSTACK PROVIDER uses Spring's RESTCLIENT to communicate with Paystack.


### Provider responses and errors

The provider's DATA helper validates Paystack's response and extracts its DATA payload. Provider failures are represented by PROVIDER EXCEPTION, which indicates whether the operation can be retried.

### Concurrency and idempotency

A unique database constraint on the order ID prevents duplicate payment records for the same order. Repository-level constraints also protect payment and idempotency records from duplicates and invalid states.

- A matching idempotency key and request replays the original result. using the same key with a different request is rejected.
- Retryable Paystack initialization failures are retried up to three times with the same payment reference.
- The `LATE flag records when a payment succeeds after it was marked expired.

### Payment status transitions

Allowed transitions are:

Current status → Allowed next status 

`PENDING` -> `SUCCEEDED` or `EXPIRED`
 `EXPIRED` -> `SUCCEEDED` 
`SUCCEEDED` ->`PARTIALLY_REFUNDED`or `REFUNDED` 
`PARTIALLY_REFUNDED` ->`PARTIALLY_REFUNDED` or `REFUNDED` 
`REFUNDED` -> None 

## Payment service

PAYMENT SERVICE contains the payment workflow and calls the provider to start checkout. It saves the payment and idempotency reservation before contacting Paystack, then uses the idempotency key to handle duplicate requests safely. 
it contains helper methods to validate payment requests and responses and  some exception handlers like apiException for 409,500.

