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

**Api key Filter**
The apiKeyFilter class runs once on every request to ensure they have the correct Api key


**Payment Provider interface**
_payment interface holds methods to_
- initialize a transaction
- refund a payment
- verify a transaction 

_The return types contain reference(to uniquely identify a payment).amount,accessCode ,returnUrl Etc_
within the paystack provider a restClient is implemented to facilitate Http calls between this service and paystack

there is a wrapper around each call called data which helps to unpack paystacks' respose as well as help with retries

retry errors or other payment provider errors are handled by the ProviderException class

**Concurrency**
for concurrency I enforced a unique index on order id so a duplicate process which
tries the register and existing order is rejected outright

the allowed states are
pending
succeded
partially refunded
and not late(this was the most difficult)

constraints also exist at the repository level for idempotency keys and payments to prevent wrong states being saved
or duplicates.

**States**
for payment states the order of flow is

PENDING            → SUCCEEDED or EXPIRED.
EXPIRED            → SUCCEEDED.
SUCCEEDED          → PARTIALLY_REFUNDED or REFUNDED.
PARTIALLY_REFUNDED → PARTIALLY_REFUNDED or REFUNDED.
REFUNDED           → false.


