# (Marketly) Payment Service

A payment service built for integration with Marketly, an existing e-commerce platform.

## My Goal

Provide a reliable, scalable, and auditable way to process online payments within Marketly.

## Stack

- Spring Boot for backend logic
- PostgreSQL for persistent data
- Docker for running PostgreSQL locally
- Paystack API for payment processing

## Run

```bash
docker compose up -d      # Postgres (needs DB_PASSWORD)
./mvnw spring-boot:run    # listens on port 4000
```

## Environment variables

| Variable | Purpose |
|---|---|
| `MERCHANT_API_KEY` | Bearer token Marketly must send |
| `PAYSTACK_SECRET_KEY` | Paystack test key, also verifies webhook signatures |
| `CALLBACK_URL` | Where signed callbacks to Marketly are sent |
| `CALLBACK_SECRET` | HMAC key for callback signatures |
| `PAYMENT_TIMEOUT_SECONDS` | Pending time before expiry (default 1800) |
| `TEST_MODE` | `true` enables `POST /admin/reset` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Database connection |

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

## How it works

- **Idempotency:** keys are stored with a request hash under a unique constraint. Repeats replay the saved result, and a reused key with a different body returns 422.
- **Concurrency:** a partial unique index allows one active payment per order. Refunds lock the payment row before checking the balance, so they can never exceed the amount paid.
- **Webhooks:** HMAC-SHA512 signature check, a dedup key committed in the same transaction as the state change, and `charge.success` is confirmed with Paystack's verify endpoint.
- **Callbacks:** written to an outbox table in the same transaction as the state change, then delivered by a background worker with exponential backoff and the same `id` on every retry. They survive restarts.
- **Expiry and reconciliation:** a job verifies pending payments after the timeout and expires unpaid ones. It also rechecks recently expired payments, so a late transfer becomes `succeeded` with `late: true`.
- **Refund timeouts:** if a Paystack refund call times out, the refund stays pending. A recovery job asks Paystack whether it exists before ever creating another.


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

## Retries
retries like 5xx or 429 are caught in wrappers named send or data within paystack providers and apiException, in others such as payment service.
The send wrapper poses a retryable flag which allows 429 or 5xx to retry.
It is worth to note that codes like 404 are not retryable .
## System design diagram
the system design diagram is found in the root outside of src.

